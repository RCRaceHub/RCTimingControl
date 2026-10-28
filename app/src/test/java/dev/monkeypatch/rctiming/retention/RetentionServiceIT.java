package dev.monkeypatch.rctiming.retention;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The retention clean-up deletes what has outlived its period and nothing else (#262). */
class RetentionServiceIT extends AbstractIntegrationTest {

    private static final String TYPE = "it-retention";

    @Autowired RetentionService retention;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("retention-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("x");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(Set.of(Role.REFEREE));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userId = userRepository.save(user).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from audit_log where entity_type = ?", TYPE);
        jdbc.update("delete from refresh_tokens where user_id = ?", userId);
        jdbc.update("delete from user_roles where user_id = ?", userId);
        jdbc.update("delete from users where id = ?", userId);
    }

    private static long micros(Duration ago) {
        Instant at = Instant.now().minus(ago);
        return at.getEpochSecond() * 1_000_000L + at.getNano() / 1_000;
    }

    private void token(String name, Duration expiresIn, boolean revoked, Duration createdAgo) {
        jdbc.update("insert into refresh_tokens (user_id, token_hash, expires_at, created_at, revoked) values (?,?,?,?,?)",
                userId, name + UUID.randomUUID(), micros(expiresIn.negated()), micros(createdAgo), revoked ? 1 : 0);
    }

    private void audit(String action, Duration ago) {
        jdbc.update("insert into audit_log (occurred_at, actor_label, source, action, entity_type, summary) "
                + "values (?, 'anonymous', 'UI', ?, ?, 'row')", micros(ago), action, TYPE);
    }

    private int tokens(String prefix) {
        return jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ? and token_hash like ?",
                Integer.class, userId, prefix + "%");
    }

    private int audits(String action) {
        return jdbc.queryForObject("select count(*) from audit_log where entity_type = ? and action = ?",
                Integer.class, TYPE, action);
    }

    @Test
    void deletesExpiredTokensButKeepsLiveAndRevokedOnesUntilTheyExpire() {
        token("expired", Duration.ofDays(-2), false, Duration.ofDays(9));
        token("expiredrevoked", Duration.ofDays(-1), true, Duration.ofDays(9));
        token("revoked", Duration.ofDays(5), true, Duration.ofDays(3));
        token("live", Duration.ofDays(5), false, Duration.ofDays(3));

        retention.purge();

        assertThat(tokens("expired")).isZero();
        assertThat(tokens("expiredrevoked")).isZero();
        assertThat(tokens("revoked")).isEqualTo(1);
        assertThat(tokens("live")).isEqualTo(1);
    }

    @Test
    void deletesRefusedSignInsAfterTheirPeriodAndKeepsOtherAuditRows() {
        audit("LOGIN_FAILED", Duration.ofDays(120));
        audit("LOGIN_FAILED", Duration.ofDays(10));
        audit("REFRESH_REFUSED", Duration.ofDays(120));
        audit("RACE_STARTED", Duration.ofDays(120));

        retention.purge();

        assertThat(audits("LOGIN_FAILED")).isEqualTo(1);
        assertThat(audits("REFRESH_REFUSED")).isZero();
        assertThat(audits("RACE_STARTED")).isEqualTo(1);
    }

    @Test
    void deletesOtherAuditRowsOnlyPastTheAuditPeriod() {
        audit("RACE_STARTED", Duration.ofDays(800));
        audit("RACE_FINISHED", Duration.ofDays(700));

        retention.purge();

        assertThat(audits("RACE_STARTED")).isZero();
        assertThat(audits("RACE_FINISHED")).isEqualTo(1);
    }

    @Test
    void recordsWhatItDeletedWithoutNamingAnyone() {
        audit("LOGIN_FAILED", Duration.ofDays(120));
        Long before = jdbc.queryForObject("select coalesce(max(id), 0) from audit_log", Long.class);

        retention.purge();

        var rows = jdbc.queryForList(
                "select actor_label, source, summary from audit_log where action = 'RETENTION_PURGE' and id > ?",
                before);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("source", "SYSTEM").containsEntry("actor_label", "system:retention");
        assertThat((String) rows.get(0).get("summary")).startsWith("Deleted ");
        jdbc.update("delete from audit_log where action = 'RETENTION_PURGE' and id > ?", before);
    }

    @Test
    void thePeriodsMustBeSensible() {
        assertThatThrownBy(() -> new RetentionProperties(0, 730, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetentionProperties(90, -1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetentionProperties(90, 30, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetentionProperties(Integer.MAX_VALUE, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new RetentionProperties(90, 0, true).auditDays()).isZero();
    }
}
