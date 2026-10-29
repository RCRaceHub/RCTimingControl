package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.jooq.generated.tables.records.OfficialAuditLogRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.monkeypatch.rctiming.jooq.generated.tables.OfficialAuditLog.OFFICIAL_AUDIT_LOG;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code RCTimingControl reset-admin-password} (#61), run against the test database while the app is up. */
class ResetAdminPasswordCommandIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    UserRepository userRepository;

    @Autowired
    DSLContext dsl;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${rctiming.database.data-directory}")
    String dataDirectory;

    @Test
    void aLockedOutOfficialGetsANewPasswordAndBecomesAnEnabledAdmin() {
        String email = createUser(Set.of(Role.REFEREE), "forgotten1");
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setDisabledAt(Instant.now());
        userRepository.save(user);

        int exit = ResetAdminPasswordCommand.run(new String[] {email, dataDirectoryArgument()},
                passwords("rescued123", "rescued123"));

        assertThat(exit).isZero();
        User reset = userRepository.findByEmail(email).orElseThrow();
        assertThat(reset.isEnabled()).isTrue();
        assertThat(reset.getRoles()).containsExactlyInAnyOrder(Role.REFEREE, Role.ADMIN);
        assertThat(login(email, "forgotten1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, "rescued123").getStatusCode()).isEqualTo(HttpStatus.OK);
        List<OfficialAuditLogRecord> log = officialLog(reset.getId());
        assertThat(log).extracting(OfficialAuditLogRecord::getAction)
                .containsExactlyInAnyOrder("PASSWORD_SET", "ROLES_CHANGED", "ENABLED");
        assertThat(log).allSatisfy(entry -> assertThat(entry.getActorUserId()).isNull());

        // One row in the audit log too, from the command line user, naming the official and what else changed
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from audit_log where entity_type = 'official' and entity_id = ?"
                        + " and action = 'ADMIN_PASSWORD_RESET'",
                String.valueOf(reset.getId()));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("source", "CLI").containsEntry("actor_user_id", null);
        assertThat(rows.get(0).get("actor_label").toString()).startsWith("cli:");
        assertThat(rows.get(0).get("summary").toString())
                .contains("from the command line").contains("made them an admin").contains("enabled their account");
        assertThat(rows.get(0).get("after_json").toString())
                .contains("\"madeAdmin\":true").contains("\"reEnabled\":true");
    }

    @Test
    void anAdminKeepsTheirRolesAndOnlyThePasswordIsLogged() {
        String email = createUser(Set.of(Role.ADMIN), "forgotten1");

        int exit = ResetAdminPasswordCommand.run(new String[] {email, dataDirectoryArgument()},
                passwords("rescued123", "rescued123"));

        assertThat(exit).isZero();
        User reset = userRepository.findByEmail(email).orElseThrow();
        assertThat(reset.getRoles()).containsExactly(Role.ADMIN);
        assertThat(officialLog(reset.getId()))
                .extracting(OfficialAuditLogRecord::getAction).containsExactly("PASSWORD_SET");
        Map<String, Object> row = jdbc.queryForMap(
                "select summary, after_json from audit_log where entity_type = 'official' and entity_id = ?"
                        + " and action = 'ADMIN_PASSWORD_RESET'", String.valueOf(reset.getId()));
        assertThat(row.get("summary").toString()).endsWith("from the command line");
        assertThat(row.get("after_json").toString()).contains("\"madeAdmin\":false").contains("\"reEnabled\":false");
        assertThat(row.toString()).doesNotContain("rescued123");
    }

    @Test
    void mismatchedOrShortPasswordsChangeNothing() {
        String email = createUser(Set.of(Role.ADMIN), "keepThis1");

        assertThat(ResetAdminPasswordCommand.run(new String[] {email, dataDirectoryArgument()},
                passwords("rescued123", "different1"))).isEqualTo(1);
        assertThat(ResetAdminPasswordCommand.run(new String[] {email, dataDirectoryArgument()},
                passwords("short", "short"))).isEqualTo(1);

        assertThat(login(email, "keepThis1").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anUnknownEmailOrNoEmailIsRefused() {
        assertThat(ResetAdminPasswordCommand.run(new String[] {"nobody-" + UUID.randomUUID() + "@example.com",
                dataDirectoryArgument()}, passwords())).isEqualTo(1);
        assertThat(ResetAdminPasswordCommand.run(new String[] {dataDirectoryArgument()}, passwords())).isEqualTo(2);
    }

    @Test
    void aFolderWithNoDatabaseIsRefusedWithoutCreatingOne(@TempDir Path empty) {
        int exit = ResetAdminPasswordCommand.run(new String[] {"admin@example.com",
                "--rctiming.database.data-directory=" + empty}, passwords());

        assertThat(exit).isEqualTo(1);
        assertThat(empty.toFile().list()).isEmpty();
    }

    private String dataDirectoryArgument() {
        return "--rctiming.database.data-directory=" + dataDirectory;
    }

    private static ResetAdminPasswordCommand.PasswordSource passwords(String... typed) {
        Deque<String> lines = new ArrayDeque<>(List.of(typed));
        return prompt -> lines.isEmpty() ? null : lines.pop().toCharArray();
    }

    private String createUser(Set<Role> roles, String password) {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Locked");
        user.setLastName("Out");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        return email;
    }

    private ResponseEntity<AuthResponse> login(String email, String password) {
        return rest.postForEntity("/api/v1/auth/login", new LoginRequest(email, password), AuthResponse.class);
    }

    private List<OfficialAuditLogRecord> officialLog(Long officialUserId) {
        return dsl.selectFrom(OFFICIAL_AUDIT_LOG)
                .where(OFFICIAL_AUDIT_LOG.OFFICIAL_USER_ID.eq(officialUserId))
                .orderBy(OFFICIAL_AUDIT_LOG.CREATED_AT, OFFICIAL_AUDIT_LOG.ID)
                .fetch();
    }
}
