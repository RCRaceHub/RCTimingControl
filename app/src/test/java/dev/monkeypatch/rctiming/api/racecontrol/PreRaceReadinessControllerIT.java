package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventStatus;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.race.MarshalAbsence;
import dev.monkeypatch.rctiming.domain.race.MarshalAbsenceRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundStatus;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.security.JwtTokenService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PreRaceReadinessControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    RacingClassRepository racingClassRepository;

    @Autowired
    EventClassRepository eventClassRepository;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    RaceRepository raceRepository;

    @Autowired
    EntryRepository entryRepository;

    @Autowired
    RaceEntryRepository raceEntryRepository;

    @Autowired
    MarshalAbsenceRepository marshalAbsenceRepository;

    private String directorToken;
    private String racerToken;
    private Long directorUserId;

    @BeforeEach
    void setUp() {
        // Create race director user
        String directorEmail = "director-" + UUID.randomUUID() + "@test.com";
        directorUserId = createUser(directorEmail, "directorPass123", Set.of(Role.RACE_DIRECTOR));
        var loginResp = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(directorEmail, "directorPass123"), AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        directorToken = loginResp.getBody().accessToken();

        // An account with no official role cannot sign in, so mint its token directly
        String racerEmail = "racer-" + UUID.randomUUID() + "@test.com";
        Long racerId = createUser(racerEmail, "racerPass123", Set.of());
        racerToken = jwtTokenService.generateAccessToken(userRepository.findById(racerId).orElseThrow());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPreRaceReadiness_firstRaceOfEvent_returnsEmptyMarshalDuty() {
        // Seed: event -> racing class -> event class -> round (PRACTICE, seq=1) -> race (seq=1, PENDING)
        // with 3 race entries at grid positions 1, 2, 3
        Instant now = Instant.now();
        Event event = saveEvent("First Race Event " + UUID.randomUUID(), now);
        RacingClass rc = saveRacingClass("Modified Touring " + UUID.randomUUID().toString().substring(0, 6), now);
        EventClass ec = saveEventClass(event.getId(), rc.getId(), now);
        Round round = saveRound(event.getId(), RoundType.PRACTICE, 1, 1, now);
        Race race = saveRace(round.getId(), ec.getId(), 1, 1, now);

        // 3 drivers with entries and race entries
        for (int i = 1; i <= 3; i++) {
            Entry entry = saveEntry(event.getId(), ec.getId(), now);
            // L11: driver 1 has checked in; driver 2 is marked arrived in RaceHub only
            if (i == 1) {
                entry.setCheckedInAt(now);
            } else if (i == 2) {
                entry.setRacehubArrival("ARRIVED");
            }
            entryRepository.save(entry);
            saveRaceEntry(race.getId(), entry.getId(), i);
        }

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/race/" + race.getId() + "/pre-race-readiness",
                HttpMethod.GET, new HttpEntity<>(directorHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("firstRaceOfEvent")).isEqualTo(true);
        assertThat((List<?>) body.get("marshalDuty")).isEmpty();
        List<Map<String, Object>> gridCall = (List<Map<String, Object>>) body.get("gridCall");
        assertThat(gridCall).hasSize(3);
        assertThat(gridCall.get(0).get("gridPosition")).isEqualTo(1);
        assertThat(gridCall).extracting(slot -> slot.get("checkedIn")).containsExactly(true, false, false);
        assertThat(gridCall).extracting(slot -> slot.get("racehubArrival")).containsExactly(null, "ARRIVED", null);
        assertThat(body.get("raceLabel").toString()).contains("Practice 1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPreRaceReadiness_secondRace_returnsPreviousRaceDriversAsMarshalDuty() {
        // Schema: marshal_absences has UNIQUE(race_id, entry_id) — one absence per (race, entry).
        // To get missedThisEvent == 2 for a driver, they must have absences in 2 distinct races
        // within the same event. We seed: race0 (prior practice) -> race1 (previous race) -> race2 (target).
        // Driver A is in race1 and is absent from both race0 and race1 (2 total this event).
        Instant now = Instant.now();
        Event event = saveEvent("Two Race Event " + UUID.randomUUID(), now);
        RacingClass rc = saveRacingClass("Touring Car " + UUID.randomUUID().toString().substring(0, 6), now);
        EventClass ec = saveEventClass(event.getId(), rc.getId(), now);
        Round round = saveRound(event.getId(), RoundType.PRACTICE, 1, 1, now);

        // Race 0 — a prior race (seq=1), used only to record an extra absence for driver A
        Race race0 = saveRace(round.getId(), ec.getId(), 1, 1, now);

        // Race 1 (seq=2) — the "previous race" whose drivers appear in the target's marshal duty
        Race race1 = saveRace(round.getId(), ec.getId(), 2, 2, now);
        Long absenceDriverEntryId = null;
        for (int i = 1; i <= 3; i++) {
            Entry entry = saveEntry(event.getId(), ec.getId(), now);
            saveRaceEntry(race1.getId(), entry.getId(), i);
            if (i == 1) {
                absenceDriverEntryId = entry.getId();
                // Also add this driver to race0 so we can record a second absence there
                saveRaceEntry(race0.getId(), entry.getId(), i);
            }
        }

        // 2 absences for driver A: one in race0, one in race1 (different race_ids — no UNIQUE violation)
        saveMarshalAbsence(absenceDriverEntryId, race0.getId(), event.getId(), directorUserId, now);
        saveMarshalAbsence(absenceDriverEntryId, race1.getId(), event.getId(), directorUserId, now);

        // Race 2 (seq=3, target) — different 3 drivers
        Race race2 = saveRace(round.getId(), ec.getId(), 3, 3, now);
        for (int i = 1; i <= 3; i++) {
            Entry entry = saveEntry(event.getId(), ec.getId(), now);
            saveRaceEntry(race2.getId(), entry.getId(), i);
        }

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/race/" + race2.getId() + "/pre-race-readiness",
                HttpMethod.GET, new HttpEntity<>(directorHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("firstRaceOfEvent")).isEqualTo(false);

        List<Map<String, Object>> marshalDuty = (List<Map<String, Object>>) body.get("marshalDuty");
        assertThat(marshalDuty).hasSize(3);

        // Find driver A (2 absences this event)
        final Long expectedAbsenceEntryId = absenceDriverEntryId;
        Map<String, Object> absentRow = marshalDuty.stream()
                .filter(r -> expectedAbsenceEntryId.equals(((Number) r.get("entryId")).longValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Absent driver not found in marshal duty"));
        assertThat(((Number) absentRow.get("missedThisEvent")).longValue()).isEqualTo(2L);

        // Others should have 0 absences
        marshalDuty.stream()
                .filter(r -> !expectedAbsenceEntryId.equals(((Number) r.get("entryId")).longValue()))
                .forEach(r -> assertThat(((Number) r.get("missedThisEvent")).longValue())
                        .as("Non-absent driver should have 0 missedThisEvent").isEqualTo(0L));
    }

    @Test
    void getPreRaceReadiness_racerRole_returns403() {
        // Use a hardcoded non-existent raceId — auth check fires before DB lookup
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/race-control/race/99999998/pre-race-readiness",
                HttpMethod.GET, new HttpEntity<>(racerHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getPreRaceReadiness_unknownRaceId_returns404() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/race-control/race/99999999/pre-race-readiness",
                HttpMethod.GET, new HttpEntity<>(directorHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- seeding helpers ---

    private Event saveEvent(String name, Instant now) {
        Event event = new Event();
        event.setName(name);
        event.setEventDate(LocalDate.now());
        event.setStatus(EventStatus.IN_PROGRESS);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return eventRepository.save(event);
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
        ec.setConfigSnapshot(new TimedRaceConfig(5, StartType.ROLLING, QualifyingType.FASTEST_LAP, 1, 3));
        ec.setCreatedAt(now);
        ec.setUpdatedAt(now);
        return eventClassRepository.save(ec);
    }

    private Round saveRound(Long eventId, RoundType type, int roundNumber, int sequenceInEvent, Instant now) {
        Round round = new Round();
        round.setEventId(eventId);
        round.setType(type);
        round.setRoundNumber(roundNumber);
        round.setSequenceInEvent(sequenceInEvent);
        round.setStatus(RoundStatus.PENDING);
        round.setCreatedAt(now);
        round.setUpdatedAt(now);
        return roundRepository.save(round);
    }

    private Race saveRace(Long roundId, Long eventClassId, int heatNumber, int sequenceInRound, Instant now) {
        Race race = new Race();
        race.setRoundId(roundId);
        race.setEventClassId(eventClassId);
        race.setHeatNumber(heatNumber);
        race.setSequenceInRound(sequenceInRound);
        race.setStartType(StartType.GRID);
        race.setStatus(RaceStatus.PENDING);
        race.setCreatedAt(now);
        race.setUpdatedAt(now);
        return raceRepository.save(race);
    }

    private Entry saveEntry(Long eventId, Long eventClassId, Instant now) {
        Entry entry = new Entry();
        entry.setEventId(eventId);
        entry.setEventClassId(eventClassId);
        entry.setStatus(EntryStatus.CONFIRMED);
        entry.setTransponderNumberSnapshot("T" + System.nanoTime());
        entry.setSubmittedAt(now);
        entry.setUpdatedAt(now);
        return entryRepository.save(entry);
    }

    private void saveRaceEntry(Long raceId, Long entryId, int gridPosition) {
        RaceEntry re = new RaceEntry();
        re.setRaceId(raceId);
        re.setEntryId(entryId);
        re.setGridPosition(gridPosition);
        raceEntryRepository.save(re);
    }

    private void saveMarshalAbsence(Long entryId, Long raceId, Long eventId, Long recordedBy, Instant now) {
        MarshalAbsence ma = new MarshalAbsence();
        ma.setEntryId(entryId);
        ma.setRaceId(raceId);
        ma.setEventId(eventId);
        ma.setRecordedBy(recordedBy);
        ma.setRecordedAt(now);
        marshalAbsenceRepository.save(ma);
    }

    private Long createUser(String email, String password, Set<Role> roles) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(roles);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user).getId();
    }

    private HttpHeaders directorHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(directorToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders racerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(racerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
