package dev.monkeypatch.rctiming.infrastructure.tts;

import dev.monkeypatch.rctiming.domain.club.AudioSettingsChangedEvent;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Pre-generates all predictable audio clips for a race when it transitions to {@code GRID} state.
 * <p>
 * Clips generated (AUDIO-09):
 * <ul>
 *   <li>Countdown intervals: the club's {@code countdownIntervals} (10m, 5m, 2m, 1m, 30s unless changed)</li>
 *   <li>Grid calls: one per entry, keyed {@code grid-<entryId>}, saying the competitor's spoken name (or display name)</li>
 *   <li>Race finished: one clip, keyed {@code finish}, in the club's wording</li>
 *   <li>Car finish: one per entry, keyed {@code car-finish-<entryId>}, in the club's wording (#299)</li>
 * </ul>
 * All clip URLs are cached in-memory keyed by raceId and served to the race control client
 * via {@link #getClipMap(Long)} (AUDIO-10).
 */
@Service
public class AudioPreGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AudioPreGenerationService.class);

    private final TtsClipService clipService;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final ClubProfileService clubProfileService;

    /** In-memory clip cache: raceId → Map<clipKey, url> */
    private final Map<Long, Map<String, String>> clipCache = new ConcurrentHashMap<>();

    /** Races whose clips are being made now, so two requests do not make them twice */
    private final Set<Long> generating = ConcurrentHashMap.newKeySet();

    /** Races whose settings changed while their clips were being made, so they are made once more */
    private final Set<Long> remake = ConcurrentHashMap.newKeySet();

    /** When a try that made no clips may be repeated, by race */
    private final Map<Long, Long> retryAfter = new ConcurrentHashMap<>();

    private static final long RETRY_AFTER_MS = 30_000;

    /** One daemon thread for remaking lost clips, off the request that found them lost */
    private final Executor regenerateExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "audio-clip-regenerate");
        thread.setDaemon(true);
        return thread;
    });

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

        log.info("Race {} entered GRID — starting audio pre-generation", event.getRaceId());
        generateClips(event.getRaceId());
    }

    /**
     * The club has changed its announcer settings: the clips held for races on the grid or running were made with
     * the old wording, voice or countdown times, so they are dropped and made again (#299). A race that has
     * none held is left alone: its clips are made at the grid call.
     */
    @Async
    @EventListener
    public void onAudioSettingsChanged(AudioSettingsChangedEvent event) {
        for (Long raceId : List.copyOf(clipCache.keySet())) {
            Race race = raceRepository.findById(raceId).orElse(null);
            if (race == null || (race.getStatus() != RaceStatus.GRID && race.getStatus() != RaceStatus.RUNNING)) {
                continue;
            }
            // Being made now from the old settings: it is made once more when that one is done
            remake.add(raceId);
            remakeIfAsked(raceId);
        }
    }

    /**
     * Makes the race's clips again if a settings change asked for it and none are being made. The new set replaces
     * the old in one step, so a browser asking meanwhile always finds a set.
     */
    private void remakeIfAsked(Long raceId) {
        while (remake.contains(raceId) && generating.add(raceId)) {
            try {
                if (remake.remove(raceId)) {
                    buildClips(raceId);
                }
            } catch (RuntimeException e) {
                log.warn("Making the audio clips for race {} again failed", raceId, e);
            } finally {
                generating.remove(raceId);
            }
        }
    }

    /**
     * Makes the race's clips again when a client asks for them and the app no longer holds them, as after a
     * restart between the grid call and the start. Does nothing for a race that is not on the grid or running,
     * and waits a while after a try that made nothing, so a speech outage is not hit by every poll.
     */
    private void regenerateIfLost(Long raceId) {
        if (clipCache.containsKey(raceId)
                || System.currentTimeMillis() < retryAfter.getOrDefault(raceId, 0L)) {
            return;
        }
        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null || (race.getStatus() != RaceStatus.GRID && race.getStatus() != RaceStatus.RUNNING)) {
            return;
        }
        if (!generating.add(raceId)) {
            return;
        }
        log.info("Race {} has no clips held (restart?) — making them again", raceId);
        regenerateExecutor.execute(() -> {
            try {
                if (!clipCache.containsKey(raceId)) {
                    makeClips(raceId);
                }
            } finally {
                generating.remove(raceId);
            }
            remakeIfAsked(raceId);
        });
    }

    private void generateClips(Long raceId) {
        if (!generating.add(raceId)) {
            return;
        }
        try {
            makeClips(raceId);
        } finally {
            generating.remove(raceId);
        }
        remakeIfAsked(raceId);
    }

    private void makeClips(Long raceId) {
        try {
            buildClips(raceId);
        } catch (RuntimeException e) {
            log.warn("Making the audio clips for race {} failed", raceId, e);
        }
        if (!clipCache.containsKey(raceId)) {
            retryAfter.put(raceId, System.currentTimeMillis() + RETRY_AFTER_MS);
        }
    }

    private void buildClips(Long raceId) {
        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null) {
            log.warn("Race {} not found for audio pre-generation", raceId);
            return;
        }

        // The club's voice; null leaves the choice to TtsClipService, which uses the Piper default
        String voiceId = clubProfileService.defaultVoiceId().orElse(null);

        Map<String, String> clips = new HashMap<>();

        ClubAudioSettings settings = clubProfileService.audioSettings();

        // 1. Countdown clips (AUDIO-02). The text matches the browser's spoken fallback.
        for (int seconds : settings.countdownIntervals()) {
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

            // The call as the car takes the flag, in the club's wording
            if (settings.announceCarFinish()) {
                // A car with no number is called by name alone, as the browser does
                String template = settings.wording().carFinish();
                String finishCall = raceEntry.getCarNumber() == null && template.contains("{car}")
                        ? competitor.speechName() + "."
                        : ClubAudioSettings.Wording.fill(template, "car",
                                raceEntry.getCarNumber() == null ? "" : String.valueOf(raceEntry.getCarNumber()),
                                "name", competitor.speechName());
                String finishCallUrl = clipService.generateCarFinishClip(raceId, entry.getId(), finishCall, voiceId);
                if (finishCallUrl != null) {
                    clips.put("car-finish-" + entry.getId(), finishCallUrl);
                }
            }
        }

        // 3. Race finished (AUDIO-05): one clip for the whole race
        String finishUrl = clipService.generateRaceFinishedClip(
                raceId, settings.wording().raceFinished(), voiceId);
        if (finishUrl != null) {
            clips.put("finish", finishUrl);
        }

        // A race that finished while its clips were being made must not get them back
        Race latest = raceRepository.findById(raceId).orElse(null);
        if (latest == null || latest.getStatus() == RaceStatus.FINISHED) {
            return;
        }
        clipCache.put(raceId, Collections.unmodifiableMap(clips));
        log.info("Audio pre-generation complete for race {}: {} clips cached", raceId, clips.size());
    }

    /**
     * Returns pre-generated clip URL map for the given race. Empty map if not yet generated.
     * Called by {@code AudioClipController} (AUDIO-10).
     */
    public Map<String, String> getClipMap(Long raceId) {
        regenerateIfLost(raceId);
        return clipCache.getOrDefault(raceId, Collections.emptyMap());
    }

    /**
     * Evicts cached clips for a finished race to free memory.
     */
    public void clearClips(Long raceId) {
        clipCache.remove(raceId);
        retryAfter.remove(raceId);
    }

    /** The same wording as the browser's own, such as "1 minute 30 seconds". */
    private static String formatCountdownLabel(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + (rest > 0 ? " " + rest + " seconds" : "");
        }
        return seconds + " seconds";
    }
}
