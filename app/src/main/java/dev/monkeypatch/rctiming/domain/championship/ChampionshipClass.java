package dev.monkeypatch.rctiming.domain.championship;


import dev.monkeypatch.rctiming.persistence.CreatedAt;
import java.time.Instant;

/**
 * A racing class that counts toward a championship (table {@code championship_classes}); a driver is scored per class.
 * A non-null {@code bestXFromYX} or {@code bestXFromYY} overrides the championship's value for this class only.
 */
public class ChampionshipClass implements CreatedAt {

    private Long id;

    private Long championshipId;

    private Long racingClassId;

    private Integer bestXFromYX;

    private Integer bestXFromYY;

    private Instant createdAt;

    public ChampionshipClass() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChampionshipId() { return championshipId; }
    public void setChampionshipId(Long v) { this.championshipId = v; }

    public Long getRacingClassId() { return racingClassId; }
    public void setRacingClassId(Long v) { this.racingClassId = v; }

    public Integer getBestXFromYX() { return bestXFromYX; }
    public void setBestXFromYX(Integer v) { this.bestXFromYX = v; }

    public Integer getBestXFromYY() { return bestXFromYY; }
    public void setBestXFromYY(Integer v) { this.bestXFromYY = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant t) { this.createdAt = t; }
}
