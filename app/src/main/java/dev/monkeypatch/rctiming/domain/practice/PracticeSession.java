package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

public class PracticeSession implements CreatedAt, UpdatedAt {

    private Long id;

    private String name;

    private Long eventId;  // nullable for standalone sessions

    private PracticeStatus status = PracticeStatus.IDLE;

    private Integer bestLapN = 5;

    /** A transponder quiet for longer than this starts a new run; the pause is not a lap. */
    private Integer pauseSeconds = 180;

    private Long createdByUserId;

    private Instant startedAt;

    private Instant stoppedAt;

    private Instant createdAt;

    private Instant updatedAt;

    // Getters and setters
    public Long getId() { return id; }
    void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public PracticeStatus getStatus() { return status; }
    public void setStatus(PracticeStatus status) { this.status = status; }

    public Integer getBestLapN() { return bestLapN; }
    public void setBestLapN(Integer bestLapN) { this.bestLapN = bestLapN; }

    public Integer getPauseSeconds() { return pauseSeconds; }
    public void setPauseSeconds(Integer pauseSeconds) { this.pauseSeconds = pauseSeconds; }

    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(Instant stoppedAt) { this.stoppedAt = stoppedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // State machine transitions
    public void start() {
        if (this.status != PracticeStatus.IDLE) {
            throw new StateConflictException("Cannot start session in " + this.status + " state");
        }
        this.status = PracticeStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void stop() {
        if (this.status != PracticeStatus.RUNNING) {
            throw new StateConflictException("Cannot stop session in " + this.status + " state");
        }
        this.status = PracticeStatus.STOPPED;
        this.stoppedAt = Instant.now();
    }
}
