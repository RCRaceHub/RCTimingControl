package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import java.time.Instant;

public class PracticeLap implements CreatedAt {

    private Long id;

    private Long practiceSessionId;

    private String transponderNumber;

    private Long userId;  // the racer's login, only on laps recorded before L10; newer laps are named by entry

    private Integer lapNumber;

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

    public Long getLapTimeMs() { return lapTimeMs; }
    public void setLapTimeMs(Long lapTimeMs) { this.lapTimeMs = lapTimeMs; }

    public Instant getCrossingTime() { return crossingTime; }
    public void setCrossingTime(Instant crossingTime) { this.crossingTime = crossingTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
