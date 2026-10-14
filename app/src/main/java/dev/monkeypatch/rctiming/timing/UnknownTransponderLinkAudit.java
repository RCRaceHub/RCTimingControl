package dev.monkeypatch.rctiming.timing;

import java.time.Instant;

/**
 * Record of a retroactive transponder link (TIMING-08): who linked which unknown transponder number to which
 * entry in which race. Maps to the {@code unknown_transponder_link} table.
 */
public class UnknownTransponderLinkAudit {

    private Long id;

    private Long raceId;

    private String transponderNumber;

    private Long entryId;

    private Long linkedByUserId;

    private Instant linkedAt;

    public UnknownTransponderLinkAudit() {}

    public UnknownTransponderLinkAudit(Long raceId, String transponderNumber,
                                       Long entryId, Long linkedByUserId) {
        this.raceId = raceId;
        this.transponderNumber = transponderNumber;
        this.entryId = entryId;
        this.linkedByUserId = linkedByUserId;
        this.linkedAt = Instant.now();
    }

    /** For the repository: an audit row as stored. */
    UnknownTransponderLinkAudit(Long id, Long raceId, String transponderNumber, Long entryId,
                                Long linkedByUserId, Instant linkedAt) {
        this(raceId, transponderNumber, entryId, linkedByUserId);
        this.id = id;
        this.linkedAt = linkedAt;
    }

    public Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    public Long getRaceId() { return raceId; }
    public String getTransponderNumber() { return transponderNumber; }
    public Long getEntryId() { return entryId; }
    public Long getLinkedByUserId() { return linkedByUserId; }
    public Instant getLinkedAt() { return linkedAt; }
}
