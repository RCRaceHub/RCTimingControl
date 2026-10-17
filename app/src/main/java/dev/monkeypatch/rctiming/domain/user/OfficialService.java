package dev.monkeypatch.rctiming.domain.user;

import dev.monkeypatch.rctiming.domain.auth.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Managing officials after setup (#61): add one, change their roles, set a new password, and
 * disable or re-enable them. Officials are never deleted, since audit logs point at them.
 * <p>
 * The club must never lock itself out, so the last enabled admin can't lose {@code ADMIN} or be
 * disabled, and an admin can't disable themselves. Disabling an official or setting their password
 * revokes their refresh tokens, so they are signed out when their access token runs out, and
 * publishes {@link OfficialSignedOutEvent} so their live timing sockets are closed straight away.
 * <p>
 * Every change is written to {@code official_audit_log} with the admin who made it, or no admin
 * for the laptop's {@code reset-admin-password} command.
 */
@Service
@Transactional
public class OfficialService {

    /** The same minimum the setup wizard asks for. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final OfficialAuditLogRepository auditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public OfficialService(UserRepository userRepository,
                           OfficialAuditLogRepository auditLogRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           ApplicationEventPublisher events) {
        this(userRepository, auditLogRepository, refreshTokenRepository, passwordEncoder, events, Clock.systemUTC());
    }

    OfficialService(UserRepository userRepository,
                    OfficialAuditLogRepository auditLogRepository,
                    RefreshTokenRepository refreshTokenRepository,
                    PasswordEncoder passwordEncoder,
                    ApplicationEventPublisher events,
                    Clock clock) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Adds an official with a password, and records it in the officials log.
     *
     * @param actorId the admin adding them, or null when there is none, as for the first admin
     * @throws OfficialChangeRefusedException if the email is already in use (HTTP 409)
     * @throws IllegalArgumentException if the email, a name, the password or the roles are missing or too short
     */
    public User add(String email, String firstName, String lastName, String password, Set<Role> roles, Long actorId) {
        String address = requireText(email, "Email");
        Set<Role> granted = requireRoles(roles);
        requirePassword(password);
        if (userRepository.findByEmail(address).isPresent()) {
            throw new OfficialChangeRefusedException("An official with the email " + address + " already exists");
        }
        Instant now = clock.instant();
        User user = new User();
        user.setEmail(address);
        user.setFirstName(requireText(firstName, "First name"));
        user.setLastName(requireText(lastName, "Last name"));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoles(new HashSet<>(granted));
        userRepository.save(user);
        log(user, actorId, OfficialAuditLog.Action.ADDED, "Roles: " + describe(granted), now);
        return user;
    }

    /**
     * Replaces the official's roles. Saving the roles they already have changes and records nothing.
     *
     * @throws OfficialChangeRefusedException if it would remove ADMIN from the only admin who can sign in (HTTP 409)
     */
    public User changeRoles(long officialId, Set<Role> roles, long actorId) {
        User user = load(officialId);
        Set<Role> granted = requireRoles(roles);
        Set<Role> before = EnumSet.noneOf(Role.class);
        before.addAll(user.getRoles());
        if (before.equals(granted)) {
            return user;
        }
        if (before.contains(Role.ADMIN) && !granted.contains(Role.ADMIN) && isLastEnabledAdmin(user)) {
            throw new OfficialChangeRefusedException(
                    user.getEmail() + " is the only admin who can sign in, so they must stay an admin");
        }
        Instant now = clock.instant();
        user.setRoles(new HashSet<>(granted));
        userRepository.save(user);
        log(user, actorId, OfficialAuditLog.Action.ROLES_CHANGED,
                describe(before) + " → " + describe(granted), now);
        return user;
    }

    /**
     * Sets a new password, revokes the official's refresh tokens and publishes an {@link OfficialSignedOutEvent}. A
     * null {@code actorId} is the laptop's command line.
     */
    public User setPassword(long officialId, String password, Long actorId) {
        User user = load(officialId);
        requirePassword(password);
        Instant now = clock.instant();
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        revokeSessions(user);
        log(user, actorId, OfficialAuditLog.Action.PASSWORD_SET, null, now);
        return user;
    }

    /**
     * Stops the official signing in, revoking their refresh tokens and publishing an {@link OfficialSignedOutEvent}.
     * Disabling one who is already disabled does nothing.
     *
     * @throws OfficialChangeRefusedException if it is the actor's own account or the only admin who can sign in (HTTP
     *         409)
     */
    public User disable(long officialId, long actorId) {
        User user = load(officialId);
        if (!user.isEnabled()) {
            return user;
        }
        if (user.getId() == actorId) {
            throw new OfficialChangeRefusedException("You can't disable your own account");
        }
        if (user.getRoles().contains(Role.ADMIN) && isLastEnabledAdmin(user)) {
            throw new OfficialChangeRefusedException(
                    user.getEmail() + " is the only admin who can sign in, so they can't be disabled");
        }
        Instant now = clock.instant();
        user.setDisabledAt(now);
        userRepository.save(user);
        revokeSessions(user);
        log(user, actorId, OfficialAuditLog.Action.DISABLED, null, now);
        return user;
    }

    /** Lets a disabled official sign in again; enabling one who is not disabled does nothing. */
    public User enable(long officialId, Long actorId) {
        User user = load(officialId);
        if (user.isEnabled()) {
            return user;
        }
        Instant now = clock.instant();
        user.setDisabledAt(null);
        userRepository.save(user);
        log(user, actorId, OfficialAuditLog.Action.ENABLED, null, now);
        return user;
    }

    private boolean isLastEnabledAdmin(User user) {
        return user.isEnabled() && userRepository.countEnabledWithRole(Role.ADMIN) <= 1;
    }

    private void revokeSessions(User user) {
        refreshTokenRepository.revokeAllForUser(user.getId());
        events.publishEvent(new OfficialSignedOutEvent(user.getId(), clock.instant()));
    }

    private void log(User user, Long actorId, OfficialAuditLog.Action action, String detail, Instant at) {
        auditLogRepository.save(new OfficialAuditLog(user.getId(), actorId, action, detail, at));
    }

    private User load(long officialId) {
        return userRepository.getOrThrow(officialId);
    }

    private static Set<Role> requireRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one role");
        }
        return EnumSet.copyOf(roles);
    }

    static void requirePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("The password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String describe(Set<Role> roles) {
        return String.join(", ", new TreeSet<>(roles.stream().map(Enum::name).toList()));
    }
}
