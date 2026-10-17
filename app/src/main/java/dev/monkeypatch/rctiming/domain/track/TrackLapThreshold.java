package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import java.time.Instant;

/**
 * Lap time limits for a track, for one class or, with no class, for every class without its own (table {@code
 * track_lap_thresholds}, TRACK-02, TRACK-03). {@code minLapMs} is the shortest lap that counts: a passing sooner than
 * that after the car's last counted one is ignored as a double read or a cut. {@code maxLastLapMs} is the longest a
 * timed race waits for a quiet car once its clock has expired before it is finished automatically. Both are in
 * milliseconds.
 */
public class TrackLapThreshold implements CreatedAt {

    private Long id;
    private Long trackId;
    /** Null for the track's default threshold, which applies to every class without its own. */
    private Long racingClassId;
    /** The class's name, filled in when the threshold is loaded; not saved. */
    private String racingClassName;
    private int minLapMs;
    private Integer maxLastLapMs;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTrackId() { return trackId; }
    public void setTrackId(Long trackId) { this.trackId = trackId; }

    public Long getRacingClassId() { return racingClassId; }
    public void setRacingClassId(Long racingClassId) { this.racingClassId = racingClassId; }

    public String getRacingClassName() { return racingClassName; }
    public void setRacingClassName(String racingClassName) { this.racingClassName = racingClassName; }

    public int getMinLapMs() { return minLapMs; }
    public void setMinLapMs(int minLapMs) { this.minLapMs = minLapMs; }

    public Integer getMaxLastLapMs() { return maxLastLapMs; }
    public void setMaxLastLapMs(Integer maxLastLapMs) { this.maxLastLapMs = maxLastLapMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
