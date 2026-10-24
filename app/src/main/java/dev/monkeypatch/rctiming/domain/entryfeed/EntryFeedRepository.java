package dev.monkeypatch.rctiming.domain.entryfeed;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryFeedsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryFeeds.ENTRY_FEEDS;

/** Repository for {@link EntryFeed} ({@code entry_feeds}); an event has at most one. */
@Repository
public class EntryFeedRepository extends JooqRepository<EntryFeed, EntryFeedsRecord> {

    private static final EntryFeedMapper MAPPER = Mappers.getMapper(EntryFeedMapper.class);

    public EntryFeedRepository(DSLContext dsl) {
        super(dsl, ENTRY_FEEDS, ENTRY_FEEDS.ID);
    }

    public Optional<EntryFeed> findByEventId(long eventId) {
        return findOne(ENTRY_FEEDS.EVENT_ID.eq(eventId));
    }

    public List<EntryFeed> findByAutoFetchTrue() {
        return findWhere(ENTRY_FEEDS.AUTO_FETCH.isTrue());
    }

    @Transactional
    public void deleteByEventId(long eventId) {
        dsl.deleteFrom(ENTRY_FEEDS).where(ENTRY_FEEDS.EVENT_ID.eq(eventId)).execute();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(ENTRY_FEEDS.CREATED_AT);
    }

    @Override
    protected EntryFeed toEntity(EntryFeedsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(EntryFeed f, EntryFeedsRecord r) {
        MAPPER.toRecord(f, r);
    }

    @Override
    protected Long idOf(EntryFeed f) {
        return f.getId();
    }

    @Override
    protected void setId(EntryFeed f, Long id) {
        f.setId(id);
    }
}
