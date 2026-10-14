package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.UnknownTransponderLinkRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static dev.monkeypatch.rctiming.jooq.generated.tables.UnknownTransponderLink.UNKNOWN_TRANSPONDER_LINK;

/**
 * Repository for retroactive transponder link audit records.
 */
@Repository
public class UnknownTransponderLinkAuditRepository
        extends JooqRepository<UnknownTransponderLinkAudit, UnknownTransponderLinkRecord> {

    public UnknownTransponderLinkAuditRepository(DSLContext dsl) {
        super(dsl, UNKNOWN_TRANSPONDER_LINK, UNKNOWN_TRANSPONDER_LINK.ID);
    }

    @Override
    protected UnknownTransponderLinkAudit toEntity(UnknownTransponderLinkRecord r) {
        return new UnknownTransponderLinkAudit(r.getId(), r.getRaceId(), r.getTransponderNumber(), r.getEntryId(),
                r.getLinkedByUserId(), r.getLinkedAt());
    }

    @Override
    protected void toRecord(UnknownTransponderLinkAudit a, UnknownTransponderLinkRecord r) {
        r.setRaceId(a.getRaceId());
        r.setTransponderNumber(a.getTransponderNumber());
        r.setEntryId(a.getEntryId());
        r.setLinkedByUserId(a.getLinkedByUserId());
        r.setLinkedAt(a.getLinkedAt());
    }

    @Override
    protected Long idOf(UnknownTransponderLinkAudit a) {
        return a.getId();
    }

    @Override
    protected void setId(UnknownTransponderLinkAudit a, Long id) {
        a.setId(id);
    }
}
