package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.api.admin.dto.CreateDecoderLoopRequest;
import dev.monkeypatch.rctiming.api.admin.dto.CreateThresholdRequest;
import dev.monkeypatch.rctiming.api.admin.dto.CreateTrackRequest;
import dev.monkeypatch.rctiming.api.admin.dto.DecoderLoopDto;
import dev.monkeypatch.rctiming.api.admin.dto.TrackDto;
import dev.monkeypatch.rctiming.api.admin.dto.TrackLapThresholdDto;
import dev.monkeypatch.rctiming.domain.track.TrackService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tracks under {@code /api/v1/admin/tracks}: the track, its decoder loops and its lap time thresholds. Reads are open
 * to ADMIN, RACE_DIRECTOR and REFEREE; every change is ADMIN only and audited. A missing track, loop or threshold is a
 * 404.
 */
@RestController
@RequestMapping("/api/v1/admin/tracks")
@RaceControlStaff
public class TrackController {

    private final TrackService trackService;

    public TrackController(TrackService trackService) {
        this.trackService = trackService;
    }

    /** Every track with its loops and thresholds. {@code GET}; read only. */
    @GetMapping
    public List<TrackDto> listTracks() {
        return trackService.findAll().stream().map(TrackDto::from).toList();
    }

    /** One track. {@code GET /{id}}; 404 if it does not exist. */
    @GetMapping("/{id}")
    public TrackDto getTrack(@PathVariable Long id) {
        return TrackDto.from(trackService.findById(id));
    }

    /** Creates a track. {@code POST}; ADMIN; answers 201. Audited. */
    @Audited("audit_log")
    @PostMapping
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public TrackDto createTrack(@RequestBody @Valid CreateTrackRequest request) {
        return TrackDto.from(trackService.create(CurrentOfficial.actor(),
                request.name(), request.venueNotes(), request.trackLength()));
    }

    /** Changes a track's name, notes and length. {@code PUT /{id}}; ADMIN. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @AdminOnly
    public TrackDto updateTrack(@PathVariable Long id, @RequestBody @Valid CreateTrackRequest request) {
        return TrackDto.from(trackService.update(CurrentOfficial.actor(), id,
                request.name(), request.venueNotes(), request.trackLength()));
    }

    /**
     * Deletes a track with its decoder loops and lap thresholds. {@code DELETE /{id}}; ADMIN; 204. 409 if the
     * database refuses because something else still refers to it. Audited.
     */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @AdminOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrack(@PathVariable Long id) {
        trackService.delete(CurrentOfficial.actor(), id);
    }

    /** Adds a decoder loop to a track. {@code POST /{trackId}/loops}; ADMIN; answers 201. Audited. */
    @Audited("audit_log")
    @PostMapping("/{trackId}/loops")
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public DecoderLoopDto addDecoderLoop(@PathVariable Long trackId,
                                          @RequestBody @Valid CreateDecoderLoopRequest request) {
        return DecoderLoopDto.from(trackService.addDecoderLoop(CurrentOfficial.actor(), trackId,
                request.loopId(), request.displayName(), request.loopType(), request.isScoringLoop()));
    }

    /** Changes a decoder loop. {@code PUT /loops/{loopId}}, by the loop's own database id; ADMIN. Audited. */
    @Audited("audit_log")
    @PutMapping("/loops/{loopId}")
    @AdminOnly
    public DecoderLoopDto updateDecoderLoop(@PathVariable Long loopId,
                                             @RequestBody @Valid CreateDecoderLoopRequest request) {
        return DecoderLoopDto.from(trackService.updateDecoderLoop(CurrentOfficial.actor(), loopId,
                request.loopId(), request.displayName(), request.loopType(), request.isScoringLoop()));
    }

    /** Deletes a decoder loop. {@code DELETE /loops/{loopId}}, by the loop's database id; ADMIN; 204. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/loops/{loopId}")
    @AdminOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDecoderLoop(@PathVariable Long loopId) {
        trackService.deleteDecoderLoop(CurrentOfficial.actor(), loopId);
    }

    /**
     * Sets the lap time limits for a class on the track, or for every class without its own when no class is given.
     * {@code POST /{trackId}/thresholds}; ADMIN; answers 201. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{trackId}/thresholds")
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public TrackLapThresholdDto setLapThreshold(@PathVariable Long trackId,
                                                 @RequestBody @Valid CreateThresholdRequest request) {
        return TrackLapThresholdDto.from(trackService.setLapThreshold(CurrentOfficial.actor(), trackId,
                request.racingClassId(), request.minLapMs(), request.maxLastLapMs()));
    }

    /** Deletes a lap threshold. {@code DELETE /thresholds/{thresholdId}}; ADMIN; 204. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/thresholds/{thresholdId}")
    @AdminOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLapThreshold(@PathVariable Long thresholdId) {
        trackService.deleteLapThreshold(CurrentOfficial.actor(), thresholdId);
    }
}
