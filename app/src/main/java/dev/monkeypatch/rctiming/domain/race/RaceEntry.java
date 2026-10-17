package dev.monkeypatch.rctiming.domain.race;


/**
 * An entry's place on a race's grid (table {@code race_entries}): one row per entry per race. {@code gridPosition} is
 * the starting order, {@code carNumber} the car number, and {@code bumped} marks a driver moved up into this final from
 * the final below (#45).
 */
public class RaceEntry {

    private Long id;

    private Long raceId;

    private Long entryId;

    private Integer gridPosition;

    private boolean bumped = false;

    private Integer carNumber;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public Integer getGridPosition() { return gridPosition; }
    public void setGridPosition(Integer gridPosition) { this.gridPosition = gridPosition; }

    public boolean isBumped() { return bumped; }
    public void setBumped(boolean bumped) { this.bumped = bumped; }

    public Integer getCarNumber() { return carNumber; }
    public void setCarNumber(Integer carNumber) { this.carNumber = carNumber; }
}
