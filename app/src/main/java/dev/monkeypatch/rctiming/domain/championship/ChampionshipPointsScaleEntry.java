package dev.monkeypatch.rctiming.domain.championship;


/**
 * The points a finishing position scores in a championship (table {@code championship_points_scale}, CHAMP-04). It has
 * no id of its own: a row is identified by the championship and the position, and a position left out scores 0.
 */
public class ChampionshipPointsScaleEntry {

    private Long championshipId;

    private Integer position;

    private int points;

    public ChampionshipPointsScaleEntry() {}

    public ChampionshipPointsScaleEntry(Long championshipId, Integer position, int points) {
        this.championshipId = championshipId;
        this.position = position;
        this.points = points;
    }

    public Long getChampionshipId() { return championshipId; }
    public void setChampionshipId(Long v) { this.championshipId = v; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer v) { this.position = v; }

    public int getPoints() { return points; }
    public void setPoints(int v) { this.points = v; }
}
