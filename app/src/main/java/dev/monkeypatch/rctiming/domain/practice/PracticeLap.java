package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import java.time.Instant;

/**
 * One lap a transponder completed in open practice (table {@code practice_laps}). {@code lapTimeMs} is in milliseconds
 * and {@code crossingTime} is when the transponder crossed the line (UTC microseconds in the database). {@code
 * runNumber} is the run the lap belongs to: a transponder quiet for longer than the session's {@code pauseSeconds}
 * starts a new run, and the pause itself is not a lap. {@code userId} is only set on laps recorded before racers
 * stopped having logins (L10).
 */
public class PracticeLap implements CreatedAt {

    private Long id;

    private Long practiceSessionId;

    private String transponderNumber;

    private Long userId;  // the racer's login, only on laps recorded before L10; newer laps are named by entry

    private Integer lapNumber;

    /** The run the lap belongs to, counted from 1 for each transponder in the session. */
    private Integer runNumber = 1;

    private Long lapTimeMs;

    private Instant crossingTime;

    private Instant createdAt;

    // Getters and setters
    public Long getId() { return id; }
    void setId(Long id) { this.id = id; }

    public Long getPracticeSessionId() { return practiceSessionId; }
    public void setPracticeSessionId(Long practiceSessionId) { this.practiceSessionId = practiceSessionId; }

    public String getTransponderNumber() { return transponderNumber; }
    public void setTransponderNumber(String transponderNumber) { this.transponderNumber = transponderNumber; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getLapNumber() { return lapNumber; }
    public void setLapNumber(Integer lapNumber) { this.lapNumber = lapNumber; }

    public Integer getRunNumber() { return runNumber; }
    public void setRunNumber(Integer runNumber) { this.runNumber = runNumber; }

    public Long getLapTimeMs() { return lapTimeMs; }
    public void setLapTimeMs(Long lapTimeMs) { this.lapTimeMs = lapTimeMs; }

    public Instant getCrossingTime() { return crossingTime; }
    public void setCrossingTime(Instant crossingTime) { this.crossingTime = crossingTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
