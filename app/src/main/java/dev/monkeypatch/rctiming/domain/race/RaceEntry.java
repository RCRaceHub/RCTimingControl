package dev.monkeypatch.rctiming.domain.race;


/**
 * An entry's place on a race's grid (table {@code race_entries}): one row per entry per race. {@code gridPosition} is
 * the starting order, {@code carNumber} the car number, {@code bumped} marks a driver moved up into this final from
 * the final below (#45), and {@code dns} a car an official marked as not started.
 */
public class RaceEntry {

    private Long id;

    private Long raceId;

    private Long entryId;

    private Integer gridPosition;

    private boolean bumped = false;

    private Integer carNumber;

    private boolean dns = false;

    private boolean confirmedStarted = false;

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

    /** Whether an official marked the car as not having started this race (DNS). */
    public boolean isDns() { return dns; }
    public void setDns(boolean dns) { this.dns = dns; }

    /**
     * Whether an official confirmed the car raced although the decoder never saw it cross the line, which keeps a
     * finished qualifying heat from marking it DNS automatically.
     */
    public boolean isConfirmedStarted() { return confirmedStarted; }
    public void setConfirmedStarted(boolean confirmedStarted) { this.confirmedStarted = confirmedStarted; }

    public Integer getCarNumber() { return carNumber; }
    public void setCarNumber(Integer carNumber) { this.carNumber = carNumber; }
}
