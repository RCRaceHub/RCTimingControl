package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.config.SpaConfig;
import dev.monkeypatch.rctiming.domain.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Who may call what over HTTP. Officials sign in; spectators and racers never do.
 *
 * <ul>
 *   <li>CSRF protection is off because the API is stateless: it authenticates with a Bearer token that a
 *       page must add itself, which a cross-site form cannot do. The one cookie, the refresh token, is
 *       SameSite=Lax and sent only to {@code /api/v1/auth/refresh} (see {@code AuthController}).</li>
 *   <li>{@code /ws/timing} is open because a browser cannot add a header to the WebSocket upgrade. Sign-in
 *       happens on the STOMP CONNECT frame instead ({@link WebSocketJwtChannelInterceptor}).</li>
 *   <li>{@code POST /api/v1/setup/bootstrap} is open so a fresh install can create its first admin. The only
 *       guard is that no account exists yet ({@code SetupService.bootstrap}), so the first caller on the venue
 *       network becomes admin.</li>
 *   <li>Any {@code /api/v1} route not opened here needs an official role, whichever one. Controllers with no
 *       {@code @PreAuthorize}, such as {@code AudioController}, {@code AudioClipController} and
 *       {@code DecoderStatusController}, rely on that last rule.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] OFFICIAL_ROLES = Role.OFFICIAL_ROLES.stream().map(Role::name).toArray(String[]::new);

    /**
     * The HTTP rules, stateless with the {@link JwtAuthenticationFilter} ahead of the standard login filter. A
     * request with no valid token is answered 401. Order matters: the explicit public routes come first and the last
     * rule, any official role, applies to everything else under the API.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/setup/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/setup/bootstrap").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/results/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/championships/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/about", "/api/v1/privacy-notice").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/boards/**").permitAll()
                        // Club logos and TTS clips are public content served from local disk —
                        // see FilesystemObjectStorageService / StaticStorageConfig.
                        .requestMatchers(HttpMethod.GET, "/storage/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(OFFICIAL_ROLES)
                        .requestMatchers("/ws/timing", "/ws/timing/**").permitAll()
                        // The bundled frontend (SpaConfig): the pages and their assets are public,
                        // the data behind them is not
                        .requestMatchers(SecurityConfig::isFrontendRequest).permitAll()
                        .anyRequest().hasAnyRole(OFFICIAL_ROLES)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Whether this is a read of a page or asset of the bundled frontend rather than an API, storage or socket path. */
    static boolean isFrontendRequest(HttpServletRequest request) {
        // Decoded, as Spring MVC routes it, so an encoded /api path is not taken for a page
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod()))
                && !SpaConfig.isServerPath(path);
    }

    /** BCrypt, for officials' passwords. Its slowness is deliberate; see {@code AuthService}. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
