package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The club's tracks, the decoder loops on them and their per-class lap thresholds. Every change is
 * audit-logged. Deleting a track takes its loops and thresholds with it, and leaves any meeting that used it
 * with no track.
 */
@Service
@Transactional
public class TrackService {

    private final TrackRepository trackRepository;
    private final DecoderLoopRepository decoderLoopRepository;
    private final TrackLapThresholdRepository thresholdRepository;
    private final RacingClassRepository racingClassRepository;
    private final AuditService audit;

    public TrackService(TrackRepository trackRepository,
                        DecoderLoopRepository decoderLoopRepository,
                        TrackLapThresholdRepository thresholdRepository,
                        RacingClassRepository racingClassRepository,
                        AuditService audit) {
        this.trackRepository = trackRepository;
        this.decoderLoopRepository = decoderLoopRepository;
        this.thresholdRepository = thresholdRepository;
        this.racingClassRepository = racingClassRepository;
        this.audit = audit;
    }

    /** Every track, with its decoder loops and lap thresholds. */
    @ReadTransaction
    public List<Track> findAll() {
        return trackRepository.findAll();
    }

    /** A track with its loops and thresholds; a 404 if there is none. */
    @ReadTransaction
    public Track findById(Long id) {
        return getTrackOrThrow(id);
    }

    /** Adds a track and records {@code TRACK_CREATED}. */
    public Track create(Actor actor, String name, String venueNotes, Double trackLength) {
        Track track = new Track();
        track.setName(name);
        track.setVenueNotes(venueNotes);
        track.setTrackLength(trackLength);
        Track saved = trackRepository.save(track);
        audit.entry(actor, "TRACK_CREATED").entity("track", saved.getId())
                .summary("Added the track " + saved.getName())
                .after(trackValues(saved)).record();
        return saved;
    }

    /** Changes a track's own details and records {@code TRACK_UPDATED}; a 404 if there is none. */
    public Track update(Actor actor, Long id, String name, String venueNotes, Double trackLength) {
        Track track = getTrackOrThrow(id);
        Map<String, Object> before = trackValues(track);
        track.setName(name);
        track.setVenueNotes(venueNotes);
        track.setTrackLength(trackLength);
        Track saved = trackRepository.save(track);
        audit.entry(actor, "TRACK_UPDATED").entity("track", id)
                .summary("Changed the track " + saved.getName())
                .before(before).after(trackValues(saved)).record();
        return saved;
    }

    /** Removes a track with its loops and thresholds and records {@code TRACK_DELETED}; a 404 if there is none. */
    public void delete(Actor actor, Long id) {
        Track track = getTrackOrThrow(id);
        Map<String, Object> before = trackValues(track);
        trackRepository.deleteById(id);
        audit.entry(actor, "TRACK_DELETED").entity("track", id)
                .summary("Removed the track " + track.getName() + " with its decoder loops and lap thresholds")
                .before(before).record();
    }

    /** Adds a decoder loop to a track and records {@code DECODER_LOOP_ADDED}; a 404 if there is no such track. */
    public DecoderLoop addDecoderLoop(Actor actor, Long trackId, String loopId, String displayName,
                                      LoopType loopType, boolean scoringLoop) {
        Track track = getTrackOrThrow(trackId);
        DecoderLoop loop = new DecoderLoop();
        loop.setTrackId(track.getId());
        loop.setLoopId(loopId);
        loop.setDisplayName(displayName);
        loop.setLoopType(loopType);
        loop.setScoringLoop(scoringLoop);
        DecoderLoop saved = decoderLoopRepository.save(loop);
        audit.entry(actor, "DECODER_LOOP_ADDED").entity("decoder_loop", saved.getId())
                .summary("Added decoder loop " + loopLabel(saved) + " to " + track.getName())
                .after(loopValues(saved)).record();
        return saved;
    }

    /** Changes a decoder loop and records {@code DECODER_LOOP_UPDATED}; a 404 if there is none. */
    public DecoderLoop updateDecoderLoop(Actor actor, Long id, String loopId, String displayName,
                                         LoopType loopType, boolean scoringLoop) {
        DecoderLoop loop = decoderLoopRepository.getOrThrow(id);
        Map<String, Object> before = loopValues(loop);
        loop.setLoopId(loopId);
        loop.setDisplayName(displayName);
        loop.setLoopType(loopType);
        loop.setScoringLoop(scoringLoop);
        DecoderLoop saved = decoderLoopRepository.save(loop);
        audit.entry(actor, "DECODER_LOOP_UPDATED").entity("decoder_loop", id)
                .summary("Changed decoder loop " + loopLabel(saved) + " of " + trackName(saved.getTrackId()))
                .before(before).after(loopValues(saved)).record();
        return saved;
    }

    /** Removes a decoder loop and records {@code DECODER_LOOP_DELETED}; a 404 if there is none. */
    public void deleteDecoderLoop(Actor actor, Long id) {
        DecoderLoop loop = decoderLoopRepository.getOrThrow(id);
        decoderLoopRepository.deleteById(id);
        audit.entry(actor, "DECODER_LOOP_DELETED").entity("decoder_loop", id)
                .summary("Removed decoder loop " + loopLabel(loop) + " from " + trackName(loop.getTrackId()))
                .before(loopValues(loop)).record();
    }

    /**
     * Sets the lap thresholds for a class on a track, or for every class when {@code racingClassId} is null, replacing
     * any already set for that combination. Records {@code LAP_THRESHOLD_ADDED} or {@code LAP_THRESHOLD_CHANGED}.
     */
    public TrackLapThreshold setLapThreshold(Actor actor, Long trackId, Long racingClassId, Integer minLapMs,
                                             Integer maxLastLapMs) {
        Track track = getTrackOrThrow(trackId);

        // Find existing threshold for this track + class combination (upsert)
        TrackLapThreshold threshold;
        if (racingClassId == null) {
            threshold = thresholdRepository.findByTrackIdAndRacingClassIsNull(trackId)
                    .orElseGet(TrackLapThreshold::new);
        } else {
            threshold = thresholdRepository
                    .findByTrackIdAndRacingClassId(trackId, racingClassId)
                    .orElseGet(TrackLapThreshold::new);
        }

        boolean isNew = threshold.getId() == null;
        Map<String, Object> before = isNew ? null : thresholdValues(threshold);
        threshold.setTrackId(track.getId());
        threshold.setMinLapMs(minLapMs);
        threshold.setMaxLastLapMs(maxLastLapMs);

        if (racingClassId != null) {
            RacingClass racingClass = racingClassRepository.getOrThrow(racingClassId);
            threshold.setRacingClassId(racingClass.getId());
            threshold.setRacingClassName(racingClass.getName());
        } else {
            threshold.setRacingClassId(null);
            threshold.setRacingClassName(null);
        }

        TrackLapThreshold saved = thresholdRepository.save(threshold);
        audit.entry(actor, isNew ? "LAP_THRESHOLD_ADDED" : "LAP_THRESHOLD_CHANGED")
                .entity("lap_threshold", saved.getId())
                .summary("Set the lap thresholds for " + (saved.getRacingClassName() == null ? "every class"
                        : saved.getRacingClassName()) + " on " + track.getName())
                .before(before).after(thresholdValues(saved)).record();
        return saved;
    }

    /** Removes a lap threshold and records {@code LAP_THRESHOLD_DELETED}; a 404 if there is none. */
    public void deleteLapThreshold(Actor actor, Long thresholdId) {
        TrackLapThreshold threshold = thresholdRepository.getOrThrow(thresholdId);
        thresholdRepository.deleteById(thresholdId);
        audit.entry(actor, "LAP_THRESHOLD_DELETED").entity("lap_threshold", thresholdId)
                .summary("Removed the lap thresholds for " + (threshold.getRacingClassName() == null ? "every class"
                        : threshold.getRacingClassName()) + " on " + trackName(threshold.getTrackId()))
                .before(thresholdValues(threshold)).record();
    }

    private String trackName(Long trackId) {
        return trackRepository.findById(trackId).map(Track::getName).orElse("track " + trackId);
    }

    private static String loopLabel(DecoderLoop loop) {
        return loop.getDisplayName() != null && !loop.getDisplayName().isBlank()
                ? loop.getDisplayName() : String.valueOf(loop.getLoopId());
    }

    private static Map<String, Object> trackValues(Track t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", t.getName());
        m.put("venueNotes", t.getVenueNotes());
        m.put("trackLength", t.getTrackLength());
        return m;
    }

    private static Map<String, Object> loopValues(DecoderLoop l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trackId", l.getTrackId());
        m.put("loopId", l.getLoopId());
        m.put("displayName", l.getDisplayName());
        m.put("loopType", l.getLoopType());
        m.put("scoringLoop", l.isScoringLoop());
        return m;
    }

    private static Map<String, Object> thresholdValues(TrackLapThreshold t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trackId", t.getTrackId());
        m.put("racingClassId", t.getRacingClassId());
        m.put("racingClassName", t.getRacingClassName());
        m.put("minLapMs", t.getMinLapMs());
        m.put("maxLastLapMs", t.getMaxLastLapMs());
        return m;
    }

    private Track getTrackOrThrow(Long id) {
        return trackRepository.getOrThrow(id);
    }
}
