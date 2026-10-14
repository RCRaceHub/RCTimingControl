package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validates JWT on STOMP CONNECT frames. The HTTP upgrade itself is permitAll, since a browser
 * cannot set an Authorization header on it.
 *
 * <p>A CONNECT with no token (or an empty bearer) is accepted as an anonymous
 * spectator session (L12 spectator boards). Anonymous sessions may only
 * SUBSCRIBE to a race's live timing and state topics and may never SEND. A CONNECT
 * carrying a non-empty but invalid token is still rejected (returns null).
 *
 * <p>An official's CONNECT is also refused when their account is disabled, or when the token was
 * issued before they were signed out (#61). {@link StompSessionRegistry} closes the sessions they
 * already have open.
 */
@Component
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketJwtChannelInterceptor.class);

    /** Topics an anonymous spectator may subscribe to. */
    static final Pattern ANONYMOUS_TOPICS = Pattern.compile("^/topic/race/\\d+/(timing|state)$");

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final StompSessionRegistry sessionRegistry;

    public WebSocketJwtChannelInterceptor(JwtTokenService jwtTokenService, UserRepository userRepository,
                                          StompSessionRegistry sessionRegistry) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        return switch (accessor.getCommand()) {
            case CONNECT, STOMP -> handleConnect(message, accessor);
            case SUBSCRIBE -> handleSubscribe(message, accessor);
            case SEND -> handleSend(message, accessor);
            default -> message;
        };
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || authHeader.isBlank() || authHeader.strip().equals("Bearer")) {
            // Anonymous spectator: no user is set; SUBSCRIBE/SEND checks restrict it.
            return message;
        }
        if (!authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT rejected: malformed Authorization header");
            return null;
        }
        String token = authHeader.substring(7).strip();
        if (token.isEmpty()) {
            return message;
        }
        try {
            Claims claims = jwtTokenService.parseToken(token);
            String officialId = claims.getSubject();
            if (!maySignIn(officialId)) {
                log.warn("STOMP CONNECT rejected: official {} is disabled", officialId);
                return null;
            }
            if (sessionRegistry.issuedBeforeSignOut(officialId,
                    claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant())) {
                log.warn("STOMP CONNECT rejected: official {} was signed out after this token was issued", officialId);
                return null;
            }
            var auth = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(), null, JwtTokenService.authorities(claims));
            accessor.setUser(auth);
            sessionRegistry.signedIn(accessor.getSessionId(), officialId);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("STOMP CONNECT rejected: invalid JWT — {}", e.getMessage());
            return null;
        }
        return message;
    }

    private boolean maySignIn(String officialId) {
        Optional<User> user;
        try {
            user = userRepository.findById(Long.parseLong(officialId));
        } catch (NumberFormatException e) {
            return false;
        }
        return user.map(User::canSignIn).orElse(false);
    }

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        if (accessor.getUser() != null) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination != null && ANONYMOUS_TOPICS.matcher(destination).matches()) {
            return message;
        }
        log.debug("STOMP SUBSCRIBE rejected for anonymous session: {}", destination);
        return null;
    }

    private Message<?> handleSend(Message<?> message, StompHeaderAccessor accessor) {
        if (accessor.getUser() != null) {
            return message;
        }
        log.debug("STOMP SEND rejected for anonymous session: {}", accessor.getDestination());
        return null;
    }
}
