package dev.monkeypatch.rctiming.domain.entryfeed;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryFeedsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryFeeds.ENTRY_FEEDS;

/** Repository for {@link EntryFeed} ({@code entry_feeds}); an event has at most one. */
@Repository
public class EntryFeedRepository extends JooqRepository<EntryFeed, EntryFeedsRecord> {

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
        EntryFeed f = new EntryFeed();
        f.setId(r.getId());
        f.setEventId(r.getEventId());
        f.setUrl(r.getUrl());
        f.setTokenEncrypted(r.getTokenEncrypted());
        f.setTokenHint(r.getTokenHint());
        f.setAutoFetch(Boolean.TRUE.equals(r.getAutoFetch()));
        f.setLastFetchAt(r.getLastFetchAt());
        f.setLastStatus(r.getLastStatus() == null ? null : EntryFeedStatus.valueOf(r.getLastStatus()));
        f.setLastError(r.getLastError());
        f.setAppliedRevision(r.getAppliedRevision());
        f.setHeldDocument(r.getHeldDocument());
        f.setHeldRevision(r.getHeldRevision());
        f.setCreatedAt(r.getCreatedAt());
        f.setUpdatedAt(r.getUpdatedAt());
        return f;
    }

    @Override
    protected void toRecord(EntryFeed f, EntryFeedsRecord r) {
        r.setEventId(f.getEventId());
        r.setUrl(f.getUrl());
        r.setTokenEncrypted(f.getTokenEncrypted());
        r.setTokenHint(f.getTokenHint());
        r.setAutoFetch(f.isAutoFetch());
        r.setLastFetchAt(f.getLastFetchAt());
        r.setLastStatus(f.getLastStatus() == null ? null : f.getLastStatus().name());
        r.setLastError(f.getLastError());
        r.setAppliedRevision(f.getAppliedRevision());
        r.setHeldDocument(f.getHeldDocument());
        r.setHeldRevision(f.getHeldRevision());
        r.setCreatedAt(f.getCreatedAt());
        r.setUpdatedAt(f.getUpdatedAt());
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
