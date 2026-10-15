package dev.monkeypatch.rctiming.infrastructure.audio;

import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.SpeechName;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts the current running order to the race audio STOMP topic at regular intervals (AUDIO-06).
 * <p>
 * Interval schedule, from the club's {@code audioSettings.tuning} (#299):
 * <ul>
 *   <li>Early in a race (the first 10 minutes unless changed): every 2 minutes unless changed</li>
 *   <li>After that: every 5 minutes unless changed</li>
 * </ul>
 * Names are spoken as the competitor's spoken name when set (#119).
 * The announcement depth (top N positions) is taken from the club's {@code audioSettings.runningOrderDepth}
 * with a default of 3.
 * <p>
 * The service registers/deregisters races via {@link RaceStatusChangedEvent}:
 * RUNNING → starts tracking; STOPPED/FINISHED → stops tracking.
 */
@Service
public class RunningOrderAnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(RunningOrderAnnouncementService.class);

    private final RaceRepository raceRepository;
    private final ClubProfileService clubProfileService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LapTimingService lapTimingService;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;

    /** raceId → race start time (when RUNNING state was entered) */
    private final Map<Long, Instant> raceStartTimes = new ConcurrentHashMap<>();
    /** raceId → last announcement time */
    private final Map<Long, Instant> lastAnnouncementTimes = new ConcurrentHashMap<>();

    public RunningOrderAnnouncementService(RaceRepository raceRepository,
                                           ClubProfileService clubProfileService,
                                           SimpMessagingTemplate messagingTemplate,
                                           LapTimingService lapTimingService,
                                           EntryRepository entryRepository,
                                           CompetitorRepository competitorRepository) {
        this.raceRepository = raceRepository;
        this.clubProfileService = clubProfileService;
        this.messagingTemplate = messagingTemplate;
        this.lapTimingService = lapTimingService;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
    }

    /** Outbound STOMP message payload for running-order announcements. */
    public record RunningOrderAnnouncement(
            String type,
            List<String> positions
    ) {}

    /**
     * Listens for RUNNING transitions to start tracking; STOPPED/FINISHED to stop.
     */
    @EventListener
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        // After the commit, so a command that rolls back does not start or stop announcements for a race whose
        // state did not change
        if (event.getNewStatus() == RaceStatus.RUNNING) {
            AfterCommit.run(() -> onRaceStarted(event.getRaceId()));
        } else if (event.getNewStatus() == RaceStatus.STOPPED
                || event.getNewStatus() == RaceStatus.FINISHED) {
            AfterCommit.run(() -> onRaceStopped(event.getRaceId()));
        }
    }

    /**
     * Register a race as started — begins tracking for interval announcements.
     */
    public void onRaceStarted(Long raceId) {
        Instant now = Instant.now();
        raceStartTimes.put(raceId, now);
        lastAnnouncementTimes.put(raceId, now);
        log.info("Race {} started — running order announcements enabled", raceId);
    }

    /**
     * Deregister a race — stops interval announcements.
     */
    public void onRaceStopped(Long raceId) {
        raceStartTimes.remove(raceId);
        lastAnnouncementTimes.remove(raceId);
        log.info("Race {} stopped — running order announcements disabled", raceId);
    }

    /**
     * Checks every 10 seconds whether any tracked race is due for a running-order announcement.
     */
    @Scheduled(fixedRate = 10_000)
    public void checkAndAnnounce() {
        if (raceStartTimes.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        ClubAudioSettings settings = clubProfileService.audioSettings();
        int announcementDepth = settings.runningOrderDepth();
        ClubAudioSettings.Tuning tuning = settings.tuning();
        Duration initialPeriod = Duration.ofMinutes(tuning.runningOrderEarlyPeriodMinutes());
        Duration initialInterval = Duration.ofMinutes(tuning.runningOrderEarlyEveryMinutes());
        Duration laterInterval = Duration.ofMinutes(tuning.runningOrderLaterEveryMinutes());

        for (Map.Entry<Long, Instant> entry : raceStartTimes.entrySet()) {
            Long raceId = entry.getKey();
            Instant startTime = entry.getValue();
            Instant lastAnnouncement = lastAnnouncementTimes.get(raceId);

            // Verify race is still RUNNING
            Race race = raceRepository.findById(raceId).orElse(null);
            if (race == null || race.getStatus() != RaceStatus.RUNNING) {
                onRaceStopped(raceId);
                continue;
            }

            // Determine which interval applies (early period vs. thereafter)
            Duration elapsed = Duration.between(startTime, now);
            Duration interval = elapsed.compareTo(initialPeriod) < 0 ? initialInterval : laterInterval;

            // Check if enough time has passed since the last announcement
            Duration sinceLast = Duration.between(lastAnnouncement, now);
            if (sinceLast.compareTo(interval) >= 0) {
                broadcastRunningOrder(raceId, announcementDepth);
                lastAnnouncementTimes.put(raceId, now);
            }
        }
    }

    private void broadcastRunningOrder(Long raceId, int depth) {
        Optional<LiveRaceState> stateOpt = lapTimingService.peek(raceId);
        if (stateOpt.isEmpty()) {
            return;
        }

        List<LiveTimingRowDto> rows = stateOpt.get().calculatePositions();
        if (rows.isEmpty()) {
            return;
        }

        List<String> positions = rows.stream()
                .sorted(Comparator.comparingInt(LiveTimingRowDto::position))
                .limit(depth)
                .map(this::spokenNameOf)
                .toList();

        RunningOrderAnnouncement announcement = new RunningOrderAnnouncement("running-order", positions);
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/audio", announcement);
        log.debug("Broadcast running order for race {}: top {} — {}", raceId, depth, positions);
    }

    /** What to say for a row: the competitor's spoken name (#119), else their tidied name (#120). */
    private String spokenNameOf(LiveTimingRowDto row) {
        return entryRepository.findById(row.entryId())
                .map(Entry::getCompetitorId)
                .flatMap(competitorRepository::findById)
                .map(Competitor::speechName)
                .orElseGet(() -> SpeechName.tidy(row.driverName()));
    }

    /**
     * Returns an unmodifiable snapshot of tracked race IDs (for testing).
     */
    public Map<Long, Instant> getRaceStartTimes() {
        return Collections.unmodifiableMap(raceStartTimes);
    }
}
