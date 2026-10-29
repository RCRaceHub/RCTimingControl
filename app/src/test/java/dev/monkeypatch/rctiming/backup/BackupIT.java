package dev.monkeypatch.rctiming.backup;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.admin.BackupController;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventService;
import dev.monkeypatch.rctiming.domain.event.EventStatus;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import dev.monkeypatch.rctiming.practice.PracticeSessionService;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Database backups (#22): taken on demand, at day close and while laps are being written. */
class BackupIT extends AbstractIntegrationTest {

    @Autowired BackupService backupService;
    @Autowired DatabaseProperties databaseProperties;
    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EventRepository eventRepository;
    @Autowired EventService eventService;
    @Autowired PracticeSessionService practiceSessionService;
    @Autowired PracticeSessionRepository practiceSessionRepository;
    @Autowired ApplicationEventPublisher eventPublisher;

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @TempDir Path backupFolder;

    @Test
    void anAdminTakesABackupNowAndSeesItListed() throws SQLException {
        HttpHeaders admin = adminHeaders();

        ResponseEntity<BackupFile> taken = restTemplate.exchange("/api/v1/admin/backups", HttpMethod.POST,
                new HttpEntity<>(admin), BackupFile.class);
        assertThat(taken.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(taken.getBody().reason()).isEqualTo("manual");

        // A backup an official asks for is in the audit log with who asked; the automatic ones are not
        var rows = jdbc.queryForList("select * from audit_log where action = 'BACKUP_TAKEN' and entity_id = ?",
                taken.getBody().name());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_user_id")).isNotNull();

        ResponseEntity<BackupController.BackupsDto> listed = restTemplate.exchange("/api/v1/admin/backups",
                HttpMethod.GET, new HttpEntity<>(admin), BackupController.BackupsDto.class);
        assertThat(listed.getBody().backups()).extracting(BackupFile::name).contains(taken.getBody().name());

        Path file = backupService.directory().resolve(taken.getBody().name());
        databaseProperties.vendor().checkBackup(file);
        assertThat(count(file, "SELECT count(*) FROM events")).isPositive();
    }

    @Test
    void backupsAreForAdminsOnly() {
        assertThat(restTemplate.getForEntity("/api/v1/admin/backups", String.class).getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        // Race directors and referees pass the /api/v1/admin/** rule but not the controller's
        for (Role role : new Role[] {Role.RACE_DIRECTOR, Role.REFEREE}) {
            HttpHeaders official = headersFor(role);
            assertThat(restTemplate.exchange("/api/v1/admin/backups", HttpMethod.GET,
                    new HttpEntity<>(official), String.class).getStatusCode()).as(role.name())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(restTemplate.exchange("/api/v1/admin/backups", HttpMethod.POST,
                    new HttpEntity<>(official), String.class).getStatusCode()).as(role.name())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void keepingNoBackupsIsRejected() {
        assertThatThrownBy(() -> new BackupProperties(backupFolder, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1");
    }

    @Test
    void aConfiguredFolderThatIsMissingIsNotCreatedOnTheLocalDisk() {
        Path unplugged = backupFolder.resolve("usb-stick");
        BackupService toUsb = new BackupService(databaseProperties, new BackupProperties(unplugged, 14));

        assertThatThrownBy(() -> toUsb.backup("manual"))
                .isInstanceOf(BackupFailedException.class)
                .hasMessageContaining("is not there");
        assertThatThrownBy(toUsb::list)
                .isInstanceOf(BackupFailedException.class)
                .hasMessageContaining("is not there");
        assertThat(unplugged).doesNotExist();
    }

    @Test
    void aFailedBackupLeavesNoPartialFile(@TempDir Path emptyDataDirectory) throws Exception {
        // A data directory with no app database: the copy is made but fails the check
        DatabaseProperties noDatabase = new DatabaseProperties(databaseProperties.vendor(), emptyDataDirectory,
                databaseProperties.migrationLocations(), databaseProperties.readConnections());
        BackupService failing = new BackupService(noDatabase, new BackupProperties(backupFolder, 14));

        assertThatThrownBy(() -> failing.backup("manual")).isInstanceOf(BackupFailedException.class);
        try (var files = java.nio.file.Files.list(backupFolder)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void onlyTheNewestBackupsAreKept() {
        BackupService keepTwo = new BackupService(databaseProperties, new BackupProperties(backupFolder, 2));

        keepTwo.backup("manual");
        keepTwo.backup("manual");
        BackupFile newest = keepTwo.backup("nightly");

        assertThat(keepTwo.list()).hasSize(2).first().isEqualTo(newest);
    }

    @Test
    void aBackupIsStillReportedWhenAnOldOneCannotBeDeleted() throws Exception {
        // A non-empty folder with a backup's name sorts oldest and cannot be deleted like a file
        Path stuck = backupFolder.resolve("rctiming-20200101-000000-manual.db");
        java.nio.file.Files.createDirectories(stuck);
        java.nio.file.Files.writeString(stuck.resolve("inside"), "x");
        java.nio.file.Files.setLastModifiedTime(stuck, java.nio.file.attribute.FileTime.fromMillis(0));
        BackupService keepOne = new BackupService(databaseProperties, new BackupProperties(backupFolder, 1));

        BackupFile taken = keepOne.backup("manual");

        assertThat(java.nio.file.Files.isRegularFile(backupFolder.resolve(taken.name()))).isTrue();
        assertThat(java.nio.file.Files.exists(stuck)).isTrue();
    }

    @Test
    void closingTheRaceDayTakesABackup() {
        Event event = new Event();
        event.setName("Day close " + UUID.randomUUID());
        event.setEventDate(LocalDate.now());
        event.setStatus(EventStatus.IN_PROGRESS);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        long eventId = eventRepository.save(event).getId();

        eventService.transition(Actor.system("test"), eventId, EventStatus.COMPLETED);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(backupService.list()).extracting(BackupFile::reason).contains("day-close"));
    }

    @Test
    void aBackupTakenWhileLapsAreWrittenOpensCleanly() throws Exception {
        finishLeftoverRunningRaces();
        practiceSessionRepository.findRunningSession()
                .ifPresent(running -> practiceSessionService.stop(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), running.getId()));
        long sessionId = practiceSessionService
                .create(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), "Backup race", null, 3, null).id();
        practiceSessionService.start(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), sessionId);
        AtomicBoolean racing = new AtomicBoolean(true);
        CompletableFuture<Integer> decoder = CompletableFuture.supplyAsync(() -> {
            int passings = 0;
            while (racing.get() || passings < 100) {
                eventPublisher.publishEvent(new LapPassingEvent(0, String.valueOf(950 + passings % 6), passings * 3_000_000L));
                passings++;
            }
            return passings;
        });
        try {
            BackupService duringRace = new BackupService(databaseProperties, new BackupProperties(backupFolder, 5));
            await().atMost(Duration.ofSeconds(20)).until(() -> lapsSoFar(sessionId) > 20);

            BackupFile backup = duringRace.backup("manual");

            racing.set(false);
            decoder.get();
            Path file = backupFolder.resolve(backup.name());
            databaseProperties.vendor().checkBackup(file);
            long lapsInBackup = count(file, "SELECT count(*) FROM practice_laps WHERE practice_session_id = " + sessionId);
            assertThat(lapsInBackup).isPositive().isLessThanOrEqualTo(lapsSoFar(sessionId));
        } finally {
            racing.set(false);
            practiceSessionService.stop(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), sessionId);
        }
    }

    private long lapsSoFar(long sessionId) throws SQLException {
        Path live = databaseProperties.effectiveDataDirectory().resolve("rctiming.db");
        return count(live, "SELECT count(*) FROM practice_laps WHERE practice_session_id = " + sessionId);
    }

    private static long count(Path database, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             ResultSet rs = connection.createStatement().executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private HttpHeaders adminHeaders() {
        return headersFor(Role.ADMIN);
    }

    private HttpHeaders headersFor(Role role) {
        String email = role.name().toLowerCase() + "-backup-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("adminPass123"));
        user.setFirstName("Admin");
        user.setLastName("Backups");
        user.setRoles(Set.of(role));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        AuthResponse auth = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "adminPass123"), AuthResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(auth.accessToken());
        return headers;
    }
}
