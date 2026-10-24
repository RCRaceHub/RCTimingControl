package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriver;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriverRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeLap;
import dev.monkeypatch.rctiming.domain.practice.PracticeLapRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeSession;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.practice.PracticeDayAggregator.Driver;
import dev.monkeypatch.rctiming.practice.dto.PracticeDayDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The day view of practice (#151): each driver's laps added up across every session that started on a day, so a
 * driver timed in two sessions is one row.
 */
@Service
@ReadTransaction
public class PracticeDayService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeLapRepository lapRepository;
    private final PracticeDriverRepository driverRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final PracticeSessionService sessionService;
    private final ClubProfileService clubProfileService;

    public PracticeDayService(PracticeSessionRepository sessionRepository,
                              PracticeLapRepository lapRepository,
                              PracticeDriverRepository driverRepository,
                              EntryRepository entryRepository,
                              CompetitorRepository competitorRepository,
                              PracticeSessionService sessionService,
                              ClubProfileService clubProfileService) {
        this.sessionRepository = sessionRepository;
        this.lapRepository = lapRepository;
        this.driverRepository = driverRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.sessionService = sessionService;
        this.clubProfileService = clubProfileService;
    }

    /**
     * The practice of the day in the club's time zone (today when no date is given), averaging {@code bestLapN} laps;
     * 5 when not given. Practice laps recorded before racers lost their logins carry only a user id and show as
     * unknown here.
     *
     * <p>A transponder is named by the session's own driver list first, else by the one entry in the session's event
     * that holds the number (withdrawn entries are skipped). Runs on a request thread in a read transaction.
     *
     * @throws IllegalArgumentException if {@code bestLapN} is below 1
     */
    public PracticeDayDto day(LocalDate requested, Integer bestLapN) {
        ZoneId zone = clubProfileService.getProfile().map(ClubProfile::getTimezone).map(ZoneId::of)
                .orElse(ZoneId.systemDefault());
        LocalDate date = requested == null ? LocalDate.now(zone) : requested;
        int n = bestLapN == null ? 5 : bestLapN;
        if (n < 1) {
            throw new IllegalArgumentException("The number of laps to average must be at least 1");
        }
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<PracticeSession> sessions = sessionRepository.findStartedBetween(from, to);

        Map<Long, PracticeSession> byId = new HashMap<>();
        sessions.forEach(s -> byId.put(s.getId(), s));
        Map<String, Long> named = new HashMap<>();
        for (PracticeSession s : sessions) {
            for (PracticeDriver d : driverRepository.findByPracticeSessionId(s.getId())) {
                named.put(s.getId() + "/" + d.getTransponderNumber(), d.getCompetitorId());
            }
        }
        Map<Long, List<Entry>> entriesByEvent = new HashMap<>();
        Map<Long, Competitor> competitors = new HashMap<>();

        List<PracticeDayAggregator.Lap> laps = lapRepository.findByPracticeSessionIds(byId.keySet()).stream()
                .filter(l -> l.getLapTimeMs() != null && l.getLapTimeMs() > 0)
                .map(l -> new PracticeDayAggregator.Lap(l.getPracticeSessionId(), l.getTransponderNumber(),
                        l.getRunNumber() == null ? 1 : l.getRunNumber(), l.getLapTimeMs()))
                .toList();

        PracticeDayAggregator.DriverLookup lookup = (sessionId, transponder) -> {
            Long competitorId = named.get(sessionId + "/" + transponder);
            if (competitorId == null) {
                Long eventId = byId.get(sessionId).getEventId();
                if (eventId != null) {
                    competitorId = entriesByEvent.computeIfAbsent(eventId, entryRepository::findByEventId).stream()
                            .filter(e -> e.getStatus() != EntryStatus.WITHDRAWN)
                            .filter(e -> e.hasTransponder(transponder))
                            .map(Entry::getCompetitorId)
                            .filter(Objects::nonNull)
                            .findFirst().orElse(null);
                }
            }
            if (competitorId == null) {
                return new Driver(null, null);
            }
            Long id = competitorId;
            Competitor c = competitors.computeIfAbsent(id, i -> competitorRepository.findById(i).orElse(null));
            return c == null ? new Driver(null, null) : new Driver(id, c.getDisplayName());
        };

        return new PracticeDayDto(date, n,
                sessions.stream().map(sessionService::toDto).toList(),
                PracticeDayAggregator.aggregate(laps, lookup, n));
    }
}
