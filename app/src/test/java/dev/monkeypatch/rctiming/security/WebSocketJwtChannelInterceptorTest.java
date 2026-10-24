package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.domain.user.OfficialSignedOutEvent;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebSocketJwtChannelInterceptorTest {

    private JwtTokenService jwtTokenService;
    private UserRepository userRepository;
    private StompSessionRegistry sessionRegistry;
    private WebSocketJwtChannelInterceptor interceptor;
    private User official;

    @BeforeEach
    void setUp() {
        jwtTokenService = mock(JwtTokenService.class);
        userRepository = mock(UserRepository.class);
        sessionRegistry = new StompSessionRegistry();
        interceptor = new WebSocketJwtChannelInterceptor(jwtTokenService, userRepository, sessionRegistry);
        official = new User();
        official.setRoles(Set.of(Role.RACE_DIRECTOR));
        when(userRepository.findById(42L)).thenReturn(Optional.of(official));
    }

    @Test
    void connectForADisabledOfficial_isRejected() {
        official.setDisabledAt(Instant.now());
        when(jwtTokenService.parseToken("good"))
                .thenReturn(Jwts.claims().subject("42").add("roles", List.of("RACE_DIRECTOR")).build());

        assertThat(interceptor.preSend(connect("Bearer good"), null)).isNull();
    }

    @Test
    void connectWithATokenIssuedBeforeASignOut_isRejected() {
        Instant signedOut = Instant.parse("2026-10-05T12:00:00Z");
        sessionRegistry.onSignedOut(new OfficialSignedOutEvent(42L, signedOut));
        when(jwtTokenService.parseToken("old")).thenReturn(Jwts.claims().subject("42")
                .issuedAt(Date.from(signedOut.minusSeconds(60))).build());
        when(jwtTokenService.parseToken("new")).thenReturn(Jwts.claims().subject("42")
                .issuedAt(Date.from(signedOut.plusSeconds(1))).build());

        assertThat(interceptor.preSend(connect("Bearer old"), null)).isNull();
        assertThat(interceptor.preSend(connect("Bearer new"), null)).isNotNull();
    }

    @Test
    void connectWithAValidToken_setsTheUser() {
        when(jwtTokenService.parseToken("good"))
                .thenReturn(Jwts.claims().subject("42").add("roles", List.of("RACE_DIRECTOR")).build());

        Message<?> result = interceptor.preSend(connect("Bearer good"), null);

        assertThat(result).isNotNull();
        Principal user = accessor(result).getUser();
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo("42");
    }

    @Test
    void connectWithNoToken_isAnAnonymousSpectator() {
        Message<?> result = interceptor.preSend(connect(null), null);

        assertThat(result).isNotNull();
        assertThat(accessor(result).getUser()).isNull();
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void connectWithAnEmptyBearer_isAnAnonymousSpectator() {
        assertThat(interceptor.preSend(connect("Bearer "), null)).isNotNull();
        assertThat(interceptor.preSend(connect("Bearer"), null)).isNotNull();
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void connectWithAnInvalidToken_isRejected() {
        when(jwtTokenService.parseToken(anyString())).thenThrow(new MalformedJwtException("bad"));

        assertThat(interceptor.preSend(connect("Bearer garbage"), null)).isNull();
    }

    @Test
    void connectWithAMalformedHeader_isRejected() {
        assertThat(interceptor.preSend(connect("Basic dXNlcjpwYXNz"), null)).isNull();
    }

    @Test
    void anonymousSubscribe_isLimitedToRaceTimingAndState() {
        assertThat(interceptor.preSend(subscribe("/topic/race/7/timing", null), null)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/topic/race/7/state", null), null)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/topic/race/7/battles", null), null)).isNotNull();

        assertThat(interceptor.preSend(subscribe("/topic/race/7/marshal", null), null)).isNull();
        assertThat(interceptor.preSend(subscribe("/topic/race/7/unknown-transponder", null), null)).isNull();
        assertThat(interceptor.preSend(subscribe("/topic/race/7/audio", null), null)).isNull();
        assertThat(interceptor.preSend(subscribe("/topic/system/decoder-status", null), null)).isNull();
        assertThat(interceptor.preSend(subscribe("/topic/practice/3/timing", null), null)).isNull();
        assertThat(interceptor.preSend(subscribe("/topic/race/7/timing/extra", null), null)).isNull();
        assertThat(interceptor.preSend(subscribe("/topic/race/*/timing", null), null)).isNull();
        assertThat(interceptor.preSend(subscribe(null, null), null)).isNull();
    }

    @Test
    void authenticatedSubscribe_isUnrestricted() {
        assertThat(interceptor.preSend(subscribe("/topic/race/7/marshal", official()), null)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/topic/system/decoder-status", official()), null)).isNotNull();
    }

    @Test
    void anonymousSend_isRejected() {
        assertThat(interceptor.preSend(send("/topic/race/7/timing", null), null)).isNull();
        assertThat(interceptor.preSend(send("/app/anything", null), null)).isNull();
    }

    @Test
    void authenticatedSend_isPassedThrough() {
        assertThat(interceptor.preSend(send("/app/anything", official()), null)).isNotNull();
    }

    private static UsernamePasswordAuthenticationToken official() {
        return new UsernamePasswordAuthenticationToken("1", null, List.of());
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return build(accessor);
    }

    private static Message<byte[]> subscribe(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(user);
        return build(accessor);
    }

    private static Message<byte[]> send(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(user);
        return build(accessor);
    }

    private static Message<byte[]> build(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static StompHeaderAccessor accessor(Message<?> message) {
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    }
}
