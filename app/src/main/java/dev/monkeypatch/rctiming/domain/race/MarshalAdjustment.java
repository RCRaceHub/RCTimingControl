package dev.monkeypatch.rctiming.domain.race;


import java.time.Instant;

/**
 * A marshal's lap correction for one car (table {@code marshal_adjustments}): {@code lapDelta} is +1 or -1 lap and
 * {@code raceStateAtTime} the race's status when it was made. It is not a state change of the race: each adjustment is
 * kept as an audit trail and triggers a position recalculation and re-broadcast. {@code actingUserName} is kept as text
 * so the row still reads right if the official is renamed.
 */
public class MarshalAdjustment {

    private Long id;

    private Long raceId;

    private Long entryId;

    private String transponderNumber;

    private int lapDelta;

    private String raceStateAtTime;

    private Long actingUserId;

    private String actingUserName;

    private Instant adjustedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public String getTransponderNumber() { return transponderNumber; }
    public void setTransponderNumber(String transponderNumber) { this.transponderNumber = transponderNumber; }

    public int getLapDelta() { return lapDelta; }
    public void setLapDelta(int lapDelta) { this.lapDelta = lapDelta; }

    public String getRaceStateAtTime() { return raceStateAtTime; }
    public void setRaceStateAtTime(String raceStateAtTime) { this.raceStateAtTime = raceStateAtTime; }

    public Long getActingUserId() { return actingUserId; }
    public void setActingUserId(Long actingUserId) { this.actingUserId = actingUserId; }

    public String getActingUserName() { return actingUserName; }
    public void setActingUserName(String actingUserName) { this.actingUserName = actingUserName; }

    public Instant getAdjustedAt() { return adjustedAt; }
    public void setAdjustedAt(Instant adjustedAt) { this.adjustedAt = adjustedAt; }
}
