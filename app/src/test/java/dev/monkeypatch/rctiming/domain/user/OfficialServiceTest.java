package dev.monkeypatch.rctiming.domain.user;

import dev.monkeypatch.rctiming.domain.auth.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The guard rails that keep a club from locking itself out (#61). */
class OfficialServiceTest {
    /** Stores and matches passwords as plain text, so a test can read what the service saved. */
    private static final PasswordEncoder PLAIN_PASSWORDS = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return raw.toString();
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return raw.toString().equals(encoded);
        }
    };


    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final long ACTOR = 1L;

    private final UserRepository users = mock(UserRepository.class);
    private final OfficialAuditLogRepository auditLog = mock(OfficialAuditLogRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private OfficialService service;

    @BeforeEach
    void setUp() {
        service = new OfficialService(users, auditLog, refreshTokens, PLAIN_PASSWORDS, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theLastEnabledAdminKeepsAdmin() {
        User admin = official(2L, Role.ADMIN);
        when(users.countEnabledWithRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRoles(2L, Set.of(Role.REFEREE), ACTOR))
                .isInstanceOf(OfficialChangeRefusedException.class)
                .hasMessageContaining("only admin");
        assertThat(admin.getRoles()).containsExactly(Role.ADMIN);
        verify(auditLog, never()).save(any());
    }

    @Test
    void anAdminCanLoseAdminWhileAnotherAdminCanSignIn() {
        User admin = official(2L, Role.ADMIN);
        when(users.countEnabledWithRole(Role.ADMIN)).thenReturn(2L);

        service.changeRoles(2L, Set.of(Role.REFEREE), ACTOR);

        assertThat(admin.getRoles()).containsExactly(Role.REFEREE);
        verify(users).save(admin);
        verify(auditLog).save(any(OfficialAuditLog.class));
    }

    @Test
    void aDisabledAdminDoesNotCountAsTheLastOne() {
        User disabledAdmin = official(2L, Role.ADMIN);
        disabledAdmin.setDisabledAt(NOW.minusSeconds(60));
        when(users.countEnabledWithRole(Role.ADMIN)).thenReturn(1L);

        service.changeRoles(2L, Set.of(Role.REFEREE), ACTOR);

        assertThat(disabledAdmin.getRoles()).containsExactly(Role.REFEREE);
    }

    @Test
    void theLastEnabledAdminCannotBeDisabled() {
        User admin = official(2L, Role.ADMIN);
        when(users.countEnabledWithRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.disable(2L, ACTOR)).isInstanceOf(OfficialChangeRefusedException.class);
        assertThat(admin.isEnabled()).isTrue();
    }

    @Test
    void anAdminCannotDisableThemselves() {
        User admin = official(ACTOR, Role.ADMIN);
        when(users.countEnabledWithRole(Role.ADMIN)).thenReturn(3L);

        assertThatThrownBy(() -> service.disable(ACTOR, ACTOR))
                .isInstanceOf(OfficialChangeRefusedException.class)
                .hasMessageContaining("your own");
        assertThat(admin.isEnabled()).isTrue();
    }

    @Test
    void disablingRevokesRefreshTokensAndIsLogged() {
        User referee = official(3L, Role.REFEREE);

        service.disable(3L, ACTOR);

        assertThat(referee.getDisabledAt()).isEqualTo(NOW);
        verify(refreshTokens).revokeAllForUser(3L);
        verify(users).save(referee);
        verify(events).publishEvent(new OfficialSignedOutEvent(3L, NOW));
        verify(auditLog).save(any(OfficialAuditLog.class));
    }

    @Test
    void settingAPasswordRevokesRefreshTokens() {
        User referee = official(3L, Role.REFEREE);

        service.setPassword(3L, "brandNew99", ACTOR);

        assertThat(referee.getPasswordHash()).isEqualTo("brandNew99");
        verify(refreshTokens).revokeAllForUser(3L);
        verify(users).save(referee);
        verify(events).publishEvent(new OfficialSignedOutEvent(3L, NOW));
    }

    @Test
    void aShortPasswordIsRefused() {
        official(3L, Role.REFEREE);

        assertThatThrownBy(() -> service.setPassword(3L, "short", ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOfficialNeedsAtLeastOneRole() {
        official(3L, Role.REFEREE);

        assertThatThrownBy(() -> service.changeRoles(3L, Set.of(), ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    private User official(long id, Role... roles) {
        User user = new User();
        user.setId(id);
        user.setEmail("official" + id + "@example.com");
        user.setFirstName("Official");
        user.setLastName(String.valueOf(id));
        user.setPasswordHash("old");
        user.setRoles(new HashSet<>(Set.of(roles)));
        user.setCreatedAt(NOW.minusSeconds(3600));
        user.setUpdatedAt(NOW.minusSeconds(3600));
        when(users.getOrThrow(id)).thenReturn(user);
        return user;
    }
}
