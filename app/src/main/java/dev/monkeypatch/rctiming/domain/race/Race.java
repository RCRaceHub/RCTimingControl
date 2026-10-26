package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

/**
 * One race: a heat or a final of one class in a round (table {@code races}). {@code status} is the lifecycle in {@link
 * RaceStatus}, moved only by {@link RaceStateMachineService}. {@code heatNumber} numbers the class's heats within a
 * round and {@code finalLetter} is set (A, B, ...) for a final and null for a heat; {@code sequenceInRound} is the
 * running order within the round. The start, finish and abandon times are set by the state machine as it moves the
 * race, and the live positions are never stored here: they are worked out in memory and only the result is stored, as a
 * {@link ResultSnapshot} when the race finishes.
 */
public class Race implements CreatedAt, UpdatedAt {

    private Long id;

    private Long roundId;

    private Long eventClassId;

    private int heatNumber;

    private int sequenceInRound;

    private String finalLetter;

    /** How the race starts; refreshed from the class's format when the grid is called. */
    private StartType startType;

    private RaceStatus status = RaceStatus.PENDING;

    private Instant startedAt;

    private Instant finishedAt;

    /** Set when race control abandons the race (CTRL-08); an abandoned race is otherwise FINISHED. */
    private Instant abandonedAt;

    /**
     * Places at the back of a final kept for drivers bumped up from the final below (#45). Set when
     * finals are seeded and filled when that final finishes; zero for heats and the lowest final.
     */
    private int bumpSlots;

    /**
     * Where the race runs in the whole event once an official has reordered the finals (1 first), null while the
     * run order is still the one the generator laid out (round, then {@code sequenceInRound}). Reordering gives every
     * race of the event a rank at once, so within an event the ranks are all set or all null.
     */
    private Integer runRank;

    private Instant createdAt;

    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }

    public Long getEventClassId() { return eventClassId; }
    public void setEventClassId(Long eventClassId) { this.eventClassId = eventClassId; }

    public int getHeatNumber() { return heatNumber; }
    public void setHeatNumber(int heatNumber) { this.heatNumber = heatNumber; }

    public int getSequenceInRound() { return sequenceInRound; }
    public void setSequenceInRound(int sequenceInRound) { this.sequenceInRound = sequenceInRound; }

    public String getFinalLetter() { return finalLetter; }
    public void setFinalLetter(String finalLetter) { this.finalLetter = finalLetter; }

    public StartType getStartType() { return startType; }
    public void setStartType(StartType startType) { this.startType = startType; }

    public RaceStatus getStatus() { return status; }
    public void setStatus(RaceStatus status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Instant getAbandonedAt() { return abandonedAt; }
    public void setAbandonedAt(Instant abandonedAt) { this.abandonedAt = abandonedAt; }

    public int getBumpSlots() { return bumpSlots; }
    public void setBumpSlots(int bumpSlots) { this.bumpSlots = bumpSlots; }
    public Integer getRunRank() { return runRank; }
    public void setRunRank(Integer runRank) { this.runRank = runRank; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
