package dev.monkeypatch.rctiming.domain.championship;


import dev.monkeypatch.rctiming.persistence.CreatedAt;
import java.time.Instant;

/**
 * Links an event to a championship as one of its rounds (table {@code championship_event_links}). The round number is
 * unique within the championship, and an event can be linked to a championship only once.
 */
public class ChampionshipEventLink implements CreatedAt {

    private Long id;

    private Long championshipId;

    private Long eventId;

    private int roundNumber;

    private Instant createdAt;

    public ChampionshipEventLink() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChampionshipId() { return championshipId; }
    public void setChampionshipId(Long v) { this.championshipId = v; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long v) { this.eventId = v; }

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int v) { this.roundNumber = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant t) { this.createdAt = t; }
}
