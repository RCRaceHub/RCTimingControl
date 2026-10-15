package dev.monkeypatch.rctiming.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Signs a REST request in from its {@code Authorization: Bearer} access token. A missing or bad token leaves
 * the request anonymous, and {@link SecurityConfig}'s rules then decide whether that is enough.
 *
 * <p>It skips everything under {@code /api/v1/auth/}, since sign-in and refresh carry no access token. A new
 * endpoint there that needs a signed-in official would never see one; put it elsewhere.
 *
 * <p>The token alone is trusted: the account is not read again on each request. A disabled official keeps
 * REST access until their access token expires ({@code app.jwt.access-token-ttl-ms}, 15 minutes by default),
 * though their refresh tokens are revoked at once. An official whose roles change keeps the old ones for as
 * long, since the roles are in the token. WebSocket sessions are cut off sooner
 * ({@link StompSessionRegistry}).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtTokenService.parseToken(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, JwtTokenService.authorities(claims));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                // Invalid token — do not set context; Spring Security will reject
                // if the endpoint requires authentication
            }
        }
        chain.doFilter(request, response);
    }
}
