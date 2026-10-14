package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import java.time.Instant;

/** Who a transponder is in one practice session (#151). */
public class PracticeDriver implements CreatedAt {

    private Long id;

    private Long practiceSessionId;

    private String transponderNumber;

    private Long competitorId;

    private Instant createdAt;

    public Long getId() { return id; }
    void setId(Long id) { this.id = id; }

    public Long getPracticeSessionId() { return practiceSessionId; }
    public void setPracticeSessionId(Long practiceSessionId) { this.practiceSessionId = practiceSessionId; }

    public String getTransponderNumber() { return transponderNumber; }
    public void setTransponderNumber(String transponderNumber) { this.transponderNumber = transponderNumber; }

    public Long getCompetitorId() { return competitorId; }
    public void setCompetitorId(Long competitorId) { this.competitorId = competitorId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
