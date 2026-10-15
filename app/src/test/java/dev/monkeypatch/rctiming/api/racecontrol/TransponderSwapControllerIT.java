package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.transponder.SwapResult;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSlot;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSwapService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventStatus;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryAuditLogRecord;
import dev.monkeypatch.rctiming.security.JwtTokenService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static org.assertj.core.api.Assertions.assertThat;

/** Entry search and transponder swap end to end (L11). */
class TransponderSwapControllerIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService jwtTokenService;
    @Autowired EventRepository eventRepository;
    @Autowired RacingClassRepository racingClassRepository;
    @Autowired EventClassRepository eventClassRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired DSLContext dsl;
    @Autowired CompetitorService competitorService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired TransponderSwapService transponderSwapService;

    private Long directorId;
    private String directorToken;
    private String refereeToken;
    private String noRoleToken;
    private Event event;
    private EventClass eventClass;
    private String className;
    private String suffix;

    @BeforeEach
    void setUp() {
        User director = createUser(Set.of(Role.RACE_DIRECTOR));
        directorId = director.getId();
        directorToken = jwtTokenService.generateAccessToken(director);
        refereeToken = jwtTokenService.generateAccessToken(createUser(Set.of(Role.REFEREE)));
        noRoleToken = jwtTokenService.generateAccessToken(createUser(Set.of()));

        Instant now = Instant.now();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        event = saveEvent(now);
        className = "Stock Buggy " + suffix;
        eventClass = saveEventClass(event.getId(), saveRacingClass(className, now).getId(), now);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_isCaseInsensitiveAndScopedToTheEvent() {
        saveEntry("Jane Doe", "A" + suffix, null, EntryStatus.CONFIRMED);
        saveEntry("John Smith", "B" + suffix, null, EntryStatus.CONFIRMED);

        ResponseEntity<List> resp = get(directorToken, search("jane doe " + suffix), List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(((Map<String, Object>) resp.getBody().get(0)).get("competitorName"))
                .isEqualTo("Jane Doe " + suffix);

        assertThat(get(directorToken, search(""), List.class).getBody()).isEmpty();
    }

    @Test
    void swap_updatesTheTransponder_andIsAudited() {
        Entry entry = saveEntry("Jane Doe", "F" + suffix, null, EntryStatus.CONFIRMED);

        ResponseEntity<Map> resp = post(refereeToken, swapUrl(entry),
                Map.of("slot", "PRIMARY", "newTransponderNumber", "G" + suffix), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("oldTransponderNumber", "F" + suffix)
                .containsEntry("newTransponderNumber", "G" + suffix)
                .containsEntry("slot", "PRIMARY");
        assertThat(entryRepository.findById(entry.getId()).orElseThrow().getTransponderNumberSnapshot())
                .isEqualTo("G" + suffix);

        List<EntryAuditLogRecord> audit = entryLog(entry.getId());
        assertThat(audit).extracting(EntryAuditLogRecord::getAction).containsExactly("TRANSPONDER_SWAP");

        // The search shows the new number
        assertThat(((Map<?, ?>) get(directorToken, search("Jane Doe " + suffix), List.class).getBody().get(0))
                .get("transponderNumber")).isEqualTo("G" + suffix);
    }

    @Test
    void swap_toAnotherCompetitorsNumber_is409() {
        Entry entry = saveEntry("Jane Doe", "H" + suffix, null, EntryStatus.CONFIRMED);
        saveEntry("John Smith", "I" + suffix, null, EntryStatus.CONFIRMED);

        ResponseEntity<Map> resp = post(directorToken, swapUrl(entry),
                Map.of("slot", "SECONDARY", "newTransponderNumber", "I" + suffix), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).containsEntry("error", "transponder_already_assigned");
        assertThat(entryRepository.findById(entry.getId()).orElseThrow().getSecondaryTransponderNumber()).isNull();
    }

    @Test
    void swap_removingThePrimary_is400() {
        Entry entry = saveEntry("Jane Doe", "J" + suffix, null, EntryStatus.CONFIRMED);

        ResponseEntity<Map> resp = post(directorToken, swapUrl(entry),
                Map.of("slot", "PRIMARY", "newTransponderNumber", ""), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).containsEntry("error", "primary_required");
    }

    @Test
    void accountsWithoutAnOfficialRole_areTurnedAway() {
        Entry entry = saveEntry("Jane Doe", "K" + suffix, null, EntryStatus.CONFIRMED);

        assertThat(get(noRoleToken, search("Jane"), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(noRoleToken, swapUrl(entry),
                Map.of("slot", "PRIMARY", "newTransponderNumber", "L" + suffix), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(null, search("Jane"), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void concurrentSwapsToTheSameFreeNumber_onlyOneCompetitorGetsIt() throws Exception {
        List<Entry> entries = IntStream.range(0, 4)
                .mapToObj(i -> saveEntry("Driver " + i, "N" + i + suffix, null, EntryStatus.CONFIRMED))
                .toList();

        List<SwapResult> results = runConcurrently(entries.size(),
                i -> () -> transponderSwapService.swap(event.getId(), entries.get(i).getId(),
                        TransponderSlot.PRIMARY, "FREE" + suffix, directorId));

        assertThat(results).filteredOn(r -> r instanceof SwapResult.Success).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof SwapResult.TransponderAlreadyAssigned)
                .hasSize(entries.size() - 1);
    }

    // --- helpers ---

    /** Starts the tasks together and waits for all of them. */
    private <T> List<T> runConcurrently(int count, java.util.function.IntFunction<Callable<T>> task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = IntStream.range(0, count)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return task.apply(i).call();
                    }))
                    .toList();
            start.countDown();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String search(String query) {
        return "/api/v1/race-control/events/" + event.getId() + "/entries/search?query=" + query;
    }

    private String swapUrl(Entry entry) {
        return "/api/v1/race-control/events/" + event.getId() + "/entries/" + entry.getId() + "/transponder-swap";
    }

    private <T> ResponseEntity<T> post(String token, String url, Object body, Class<T> type) {
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers(token)), type);
    }

    private <T> ResponseEntity<T> get(String token, String url, Class<T> type) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(token)), type);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private User createUser(Set<Role> roles) {
        User user = new User();
        user.setEmail("official-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("unused");
        user.setFirstName("Test");
        user.setLastName("Official");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private Event saveEvent(Instant now) {
        Event e = new Event();
        e.setName("Swap Event " + suffix);
        e.setEventDate(LocalDate.now());
        e.setStatus(EventStatus.IN_PROGRESS);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return eventRepository.save(e);
    }

    private RacingClass saveRacingClass(String name, Instant now) {
        RacingClass rc = new RacingClass();
        rc.setName(name);
        rc.setCreatedAt(now);
        rc.setUpdatedAt(now);
        return racingClassRepository.save(rc);
    }

    private EventClass saveEventClass(Long eventId, Long racingClassId, Instant now) {
        EventClass ec = new EventClass();
        ec.setEventId(eventId);
        ec.setRacingClassId(racingClassId);
        ec.setConfigSnapshot(new TimedRaceConfig(5,
                dev.monkeypatch.rctiming.domain.format.StartType.ROLLING,
                QualifyingType.FASTEST_LAP, 1, 3));
        ec.setCreatedAt(now);
        ec.setUpdatedAt(now);
        return eventClassRepository.save(ec);
    }

    private Entry saveEntry(String name, String primary, String secondary, EntryStatus status) {
        Instant now = Instant.now();
        Entry entry = new Entry();
        entry.setCompetitorId(competitorService.createWalkIn(name + " " + suffix).getId());
        entry.setEventId(event.getId());
        entry.setEventClassId(eventClass.getId());
        entry.setStatus(status);
        entry.setTransponderNumberSnapshot(primary);
        entry.setSecondaryTransponderNumber(secondary);
        entry.setSubmittedAt(now);
        entry.setUpdatedAt(now);
        return entryRepository.save(entry);
    }

    private List<EntryAuditLogRecord> entryLog(Long entryId) {
        return dsl.selectFrom(ENTRY_AUDIT_LOG)
                .where(ENTRY_AUDIT_LOG.ENTRY_ID.eq(entryId))
                .orderBy(ENTRY_AUDIT_LOG.CREATED_AT, ENTRY_AUDIT_LOG.ID)
                .fetch();
    }
}
