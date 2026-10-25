package dev.monkeypatch.rctiming.domain.sponsor;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EventSponsorsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EventSponsors.EVENT_SPONSORS;

/** Repository for {@link EventSponsor} ({@code event_sponsors}). */
@Repository
public class EventSponsorRepository extends JooqRepository<EventSponsor, EventSponsorsRecord> {

    private static final EventSponsorMapper MAPPER = Mappers.getMapper(EventSponsorMapper.class);

    public EventSponsorRepository(DSLContext dsl) {
        super(dsl, EVENT_SPONSORS, EVENT_SPONSORS.ID);
    }

    /** The event's sponsors in the order they are shown. */
    public List<EventSponsor> findByEventId(Long eventId) {
        return findWhere(EVENT_SPONSORS.EVENT_ID.eq(eventId), EVENT_SPONSORS.SORT_ORDER.asc(), EVENT_SPONSORS.ID.asc());
    }

    @Override
    protected EventSponsor toEntity(EventSponsorsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(EventSponsor s, EventSponsorsRecord r) {
        MAPPER.toRecord(s, r);
    }

    @Override
    protected Long idOf(EventSponsor s) {
        return s.getId();
    }

    @Override
    protected void setId(EventSponsor s, Long id) {
        s.setId(id);
    }
}
