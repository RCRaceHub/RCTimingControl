package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.Names;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static dev.monkeypatch.rctiming.persistence.RoundTrip.assertSavedAndReloaded;
import static org.assertj.core.api.Assertions.assertThat;

/** The competitor and entry repositories save and load every field (#72). */
class CompetitorAndEntryRepositoriesIT extends AbstractIntegrationTest {

    private static final Instant T1 = Instant.parse("2026-10-05T10:15:30.123456Z");
    private static final Instant T2 = T1.plus(1, ChronoUnit.HOURS);

    @Autowired CompetitorRepository competitors;
    @Autowired EntryRepository entries;
    @Autowired EntryAuditLogRepository auditLogs;
    @Autowired EventRepository events;
    @Autowired EventClassRepository eventClasses;
    @Autowired UserRepository users;
    @Autowired DSLContext dsl;

    private final List<Runnable> cleanup = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        cleanup.reversed().forEach(Runnable::run);
    }

    @Test
    void competitorRoundTrip() {
        String externalId = "rh-driver-" + System.nanoTime();
        Competitor saved = assertSavedAndReloaded(competitors, competitor(externalId), c -> {
            c.setDisplayName("Renamed driver");
            c.setExternalSource(null);
            c.setExternalId(null);
            c.setBrcaNumber(null);
            c.setHomeClub("Other club");
            c.setSpokenName(null);
            c.setCreatedAt(T2);
            c.setUpdatedAt(T2);
            return c;
        }, Competitor::getId);
        cleanup.add(() -> competitors.deleteById(saved.getId()));

        Competitor imported = competitors.save(competitor(externalId));
        cleanup.add(() -> competitors.deleteById(imported.getId()));
        assertThat(competitors.findByExternalSourceAndExternalId("RACEHUB", externalId))
                .get().extracting(Competitor::getId).isEqualTo(imported.getId());
        assertThat(competitors.findByExternalSourceAndExternalId("RACEHUB", "missing")).isEmpty();
    }

    @Test
    void findBySameNameIgnoresCaseAndSpacing() {
        String name = "Normal Name " + System.nanoTime();
        Competitor saved = competitors.save(named(name));
        cleanup.add(() -> competitors.deleteById(saved.getId()));
        Competitor other = competitors.save(named(name + " Jr"));
        cleanup.add(() -> competitors.deleteById(other.getId()));

        assertThat(competitors.findBySameName("  " + name.toUpperCase().replace(" ", "   ") + " "))
                .extracting(Competitor::getId).containsExactly(saved.getId());
        assertThat(competitors.findBySameName(name.toLowerCase().replace(" ", "")))
                .extracting(Competitor::getId).containsExactly(saved.getId());
        assertThat(competitors.findBySameName("Nobody Like " + name)).isEmpty();
    }

    @Test
    void findBySameNameIgnoresAccentedCapitalsAndTabs() {
        String n = String.valueOf(System.nanoTime());
        Competitor accented = competitors.save(named("Ren\u00e9 M\u00fcller " + n));
        cleanup.add(() -> competitors.deleteById(accented.getId()));
        Competitor tabbed = competitors.save(named("Alex\tRowe " + n));
        cleanup.add(() -> competitors.deleteById(tabbed.getId()));

        assertThat(competitors.findBySameName("REN\u00c9  M\u00dcLLER " + n))
                .extracting(Competitor::getId).containsExactly(accented.getId());
        assertThat(competitors.findBySameName("alex rowe " + n))
                .extracting(Competitor::getId).containsExactly(tabbed.getId());
        assertThat(competitors.findBySameName("Alex\u00a0Rowe " + n))
                .extracting(Competitor::getId).containsExactly(tabbed.getId());
    }

    @Test
    void findWithoutBrcaNumberByMatchKeyUsesTheSameRuleAndSkipsBrcaHolders() {
        String n = String.valueOf(System.nanoTime());
        Competitor plain = competitors.save(named("Ren\u00e9 M\u00fcller " + n));
        cleanup.add(() -> competitors.deleteById(plain.getId()));
        Competitor numbered = named("Ren\u00e9 M\u00fcller " + n);
        numbered.setBrcaNumber("B" + n);
        Competitor withBrca = competitors.save(numbered);
        cleanup.add(() -> competitors.deleteById(withBrca.getId()));

        assertThat(competitors.findWithoutBrcaNumberByMatchKey().get(Names.matchKey("REN\u00c9   M\u00dcLLER " + n)))
                .extracting(Competitor::getId).containsExactly(plain.getId());
    }

    private static Competitor named(String displayName) {
        Competitor c = new Competitor();
        c.setDisplayName(displayName);
        c.setCreatedAt(T1);
        c.setUpdatedAt(T1);
        return c;
    }

    @Test
    void entryAndAuditLogRoundTrip() {
        Event event = events.save(event());
        cleanup.add(() -> events.deleteById(event.getId()));
        EventClass eventClass = eventClasses.save(eventClass(event.getId()));
        cleanup.add(() -> eventClasses.deleteById(eventClass.getId()));
        Competitor competitor = competitors.save(competitor("rh-driver-" + System.nanoTime()));
        cleanup.add(() -> competitors.deleteById(competitor.getId()));
        User official = users.save(official());
        cleanup.add(() -> users.deleteById(official.getId()));

        Entry e = new Entry();
        e.setCompetitorId(competitor.getId());
        e.setEventId(event.getId());
        e.setEventClassId(eventClass.getId());
        e.setTransponderNumberSnapshot("1234567");
        e.setSecondaryTransponderNumber("7654321");
        e.setStatus(EntryStatus.CONFIRMED);
        e.setSubmittedAt(T1);
        e.setConfirmedAt(T1);
        e.setWithdrawnAt(null);
        e.setUpdatedAt(T1);
        e.setExternalSource("RACEHUB");
        e.setExternalEntryId("rh-entry-" + System.nanoTime());
        e.setExternalEntryVersion(3L);
        e.setRacehubArrival("ARRIVED");
        e.setRacehubEventClassId("rh-class-1");

        Entry saved = assertSavedAndReloaded(entries, e, c -> {
            c.setEventClassId(null);
            c.setTransponderNumberSnapshot("2222222");
            c.setSecondaryTransponderNumber(null);
            c.setStatus(EntryStatus.WITHDRAWN);
            c.setSubmittedAt(T2);
            c.setConfirmedAt(null);
            c.setWithdrawnAt(T2);
            c.setUpdatedAt(T2);
            c.setExternalEntryVersion(4L);
            c.setRacehubArrival(null);
            c.setRacehubEventClassId(null);
            return c;
        }, Entry::getId);
        cleanup.add(() -> entries.deleteById(saved.getId()));

        assertThat(entries.findByEventId(event.getId())).extracting(Entry::getId).containsExactly(saved.getId());
        assertThat(entries.findByExternalSourceAndExternalEntryId("RACEHUB", saved.getExternalEntryId()))
                .get().extracting(Entry::getId).isEqualTo(saved.getId());
        assertThat(entries.findByIdForUpdate(saved.getId())).isPresent();

        saved.setEventClassId(eventClass.getId());
        entries.save(saved);
        assertThat(entries.findByEventClassIdAndStatus(eventClass.getId(), EntryStatus.WITHDRAWN))
                .extracting(Entry::getId).containsExactly(saved.getId());
        assertThat(entries.findByEventClassIdAndStatus(eventClass.getId(), EntryStatus.CONFIRMED)).isEmpty();

        EntryAuditLog later = auditLog(saved.getId(), official.getId(), "WITHDRAW", T2);
        auditLogs.save(later);
        EntryAuditLog first = assertSavedAndReloaded(auditLogs,
                auditLog(saved.getId(), official.getId(), "CREATE", T1), c -> {
                    c.setAction("EDIT");
                    c.setReason(null);
                    c.setBeforeSnapshot(null);
                    c.setAfterSnapshot("{\"status\":\"PENDING\"}");
                    return c;
                }, EntryAuditLog::getId);
        assertThat(entryLogIds(saved.getId())).containsExactly(first.getId(), later.getId());

        entries.deleteById(saved.getId());
        assertThat(entryLogIds(saved.getId())).as("the audit log goes with its entry").isEmpty();
    }

    private static Competitor competitor(String externalId) {
        Competitor c = new Competitor();
        c.setDisplayName("Round trip driver");
        c.setExternalSource("RACEHUB");
        c.setExternalId(externalId);
        c.setBrcaNumber("BRCA-123");
        c.setHomeClub("Test club");
        c.setSpokenName("Round-trip say as");
        c.setCreatedAt(T1);
        c.setUpdatedAt(T1);
        return c;
    }

    private static EntryAuditLog auditLog(Long entryId, Long userId, String action, Instant at) {
        EntryAuditLog a = new EntryAuditLog();
        a.setEntryId(entryId);
        a.setAdminUserId(userId);
        a.setAction(action);
        a.setReason("Because");
        a.setBeforeSnapshot("{}");
        a.setAfterSnapshot("{\"status\":\"CONFIRMED\"}");
        a.setCreatedAt(at);
        return a;
    }

    private static Event event() {
        Event e = new Event();
        e.setName("Entry event");
        e.setEventDate(LocalDate.of(2026, 10, 5));
        e.setCreatedAt(T1);
        e.setUpdatedAt(T1);
        return e;
    }

    private static EventClass eventClass(Long eventId) {
        EventClass ec = new EventClass();
        ec.setEventId(eventId);
        ec.setConfigSnapshot(new TimedRaceConfig(5, StartType.STAGGER, QualifyingType.FTQ, 2, 3));
        ec.setCreatedAt(T1);
        ec.setUpdatedAt(T1);
        return ec;
    }

    private static User official() {
        User u = new User();
        u.setEmail("entry-official-" + System.nanoTime() + "@example.com");
        u.setPasswordHash("x");
        u.setFirstName("Entry");
        u.setLastName("Official");
        u.setRoles(Set.of(Role.ADMIN));
        u.setCreatedAt(T1);
        u.setUpdatedAt(T1);
        return u;
    }

    private List<Long> entryLogIds(Long entryId) {
        return dsl.select(ENTRY_AUDIT_LOG.ID).from(ENTRY_AUDIT_LOG)
                .where(ENTRY_AUDIT_LOG.ENTRY_ID.eq(entryId))
                .orderBy(ENTRY_AUDIT_LOG.CREATED_AT, ENTRY_AUDIT_LOG.ID)
                .fetch(ENTRY_AUDIT_LOG.ID);
    }
}
