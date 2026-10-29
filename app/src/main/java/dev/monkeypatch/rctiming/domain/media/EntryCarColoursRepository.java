package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryCarColoursRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryCarColours.ENTRY_CAR_COLOURS;

/** Repository for {@link EntryCarColours} ({@code entry_car_colours}). */
@Repository
public class EntryCarColoursRepository extends JooqRepository<EntryCarColours, EntryCarColoursRecord> {

    private static final EntryCarColoursMapper MAPPER = Mappers.getMapper(EntryCarColoursMapper.class);

    public EntryCarColoursRepository(DSLContext dsl) {
        super(dsl, ENTRY_CAR_COLOURS, ENTRY_CAR_COLOURS.ID);
    }

    /** The entry's car colours, if it has any. */
    public Optional<EntryCarColours> findByEntryId(Long entryId) {
        return findOne(ENTRY_CAR_COLOURS.ENTRY_ID.eq(entryId));
    }

    /** The car colours of these entries. */
    public List<EntryCarColours> findByEntryIds(Collection<Long> entryIds) {
        return entryIds.isEmpty() ? List.of() : findWhere(ENTRY_CAR_COLOURS.ENTRY_ID.in(entryIds));
    }

    @Override
    protected EntryCarColours toEntity(EntryCarColoursRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(EntryCarColours c, EntryCarColoursRecord r) {
        MAPPER.toRecord(c, r);
    }

    @Override
    protected Long idOf(EntryCarColours c) {
        return c.getId();
    }

    @Override
    protected void setId(EntryCarColours c, Long id) {
        c.setId(id);
    }

    @Override
    protected String entityName() {
        return "Entry car colours";
    }
}
