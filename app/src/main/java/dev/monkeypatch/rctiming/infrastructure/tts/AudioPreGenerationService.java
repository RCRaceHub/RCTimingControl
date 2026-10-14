package dev.monkeypatch.rctiming.infrastructure.tts;

import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-generates all predictable audio clips for a race when it transitions to {@code GRID} state.
 * <p>
 * Clips generated (AUDIO-09):
 * <ul>
 *   <li>Countdown intervals: 10m, 5m, 2m, 1m, 30s</li>
 *   <li>Grid calls: one per entry, keyed {@code grid-<entryId>}, saying the competitor's spoken name (or display name)</li>
 *   <li>Race finished: one clip, keyed {@code finish}</li>
 * </ul>
 * All clip URLs are cached in-memory keyed by raceId and served to the race control client
 * via {@link #getClipMap(Long)} (AUDIO-10).
 */
@Service
public class AudioPreGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AudioPreGenerationService.class);

    /** Countdown intervals in seconds: 10 min, 5 min, 2 min, 1 min, 30 sec */
    private static final int[] COUNTDOWN_SECONDS = {600, 300, 120, 60, 30};

    private final TtsClipService clipService;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final ClubProfileService clubProfileService;

    /** In-memory clip cache: raceId → Map<clipKey, url> */
    private final Map<Long, Map<String, String>> clipCache = new ConcurrentHashMap<>();

    public AudioPreGenerationService(TtsClipService clipService,
                                     RaceRepository raceRepository,
                                     RaceEntryRepository raceEntryRepository,
                                     EntryRepository entryRepository,
                                     CompetitorRepository competitorRepository,
                                     ClubProfileService clubProfileService) {
        this.clipService = clipService;
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.clubProfileService = clubProfileService;
    }

    /**
     * React to race GRID transition and pre-generate all predictable clips.
     * Runs asynchronously so it does not block the race transition itself.
     */
    @Async
    @EventListener
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        if (event.getNewStatus() == RaceStatus.FINISHED) {
            clearClips(event.getRaceId());
            return;
        }
        if (event.getNewStatus() != RaceStatus.GRID) {
            return;
        }

        Long raceId = event.getRaceId();
        log.info("Race {} entered GRID — starting audio pre-generation", raceId);

        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null) {
            log.warn("Race {} not found for audio pre-generation", raceId);
            return;
        }

        // The club's voice; null leaves the choice to TtsClipService, which uses the Piper default
        String voiceId = clubProfileService.defaultVoiceId().orElse(null);

        Map<String, String> clips = new HashMap<>();

        // 1. Countdown clips (AUDIO-02). The text matches the browser's spoken fallback.
        for (int seconds : COUNTDOWN_SECONDS) {
            String url = clipService.generateCountdownClip(
                    raceId, seconds, formatCountdownLabel(seconds) + " remaining.", voiceId);
            if (url != null) {
                clips.put("countdown-" + seconds, url);
            }
        }

        // 2. Grid calls (AUDIO-03): one per entry, keyed by entry id so the browser can find it
        List<RaceEntry> entries = raceEntryRepository.findByRaceIdOrderByGridPosition(raceId);
        for (RaceEntry raceEntry : entries) {
            Entry entry = entryRepository.findById(raceEntry.getEntryId()).orElse(null);
            if (entry == null || entry.getCompetitorId() == null) continue;
            Competitor competitor = competitorRepository.findById(entry.getCompetitorId()).orElse(null);
            if (competitor == null) continue;

            String url = clipService.generateGridCallClip(
                    raceId, entry.getId(), competitor.speechName() + ".", voiceId);
            if (url != null) {
                clips.put("grid-" + entry.getId(), url);
            }
        }

        // 3. Race finished (AUDIO-05): one clip for the whole race
        String finishUrl = clipService.generateRaceFinishedClip(
                raceId, "Race finished. Checkered flag.", voiceId);
        if (finishUrl != null) {
            clips.put("finish", finishUrl);
        }

        clipCache.put(raceId, Collections.unmodifiableMap(clips));
        log.info("Audio pre-generation complete for race {}: {} clips cached", raceId, clips.size());
    }

    /**
     * Returns pre-generated clip URL map for the given race. Empty map if not yet generated.
     * Called by {@code AudioClipController} (AUDIO-10).
     */
    public Map<String, String> getClipMap(Long raceId) {
        return clipCache.getOrDefault(raceId, Collections.emptyMap());
    }

    /**
     * Evicts cached clips for a finished race to free memory.
     */
    public void clearClips(Long raceId) {
        clipCache.remove(raceId);
    }

    private static String formatCountdownLabel(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            int minutes = seconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }
        return seconds + " seconds";
    }
}
