package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryPhotosRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryPhotos.ENTRY_PHOTOS;

/** Repository for {@link EntryPhoto} ({@code entry_photos}). */
@Repository
public class EntryPhotoRepository extends JooqRepository<EntryPhoto, EntryPhotosRecord> {

    private static final EntryPhotoMapper MAPPER = Mappers.getMapper(EntryPhotoMapper.class);

    public EntryPhotoRepository(DSLContext dsl) {
        super(dsl, ENTRY_PHOTOS, ENTRY_PHOTOS.ID);
    }

    /** The entry's car photo, if it has one. */
    public Optional<EntryPhoto> findByEntryId(Long entryId) {
        return findOne(ENTRY_PHOTOS.ENTRY_ID.eq(entryId));
    }

    /** The car photos of these entries. */
    public List<EntryPhoto> findByEntryIds(Collection<Long> entryIds) {
        return entryIds.isEmpty() ? List.of() : findWhere(ENTRY_PHOTOS.ENTRY_ID.in(entryIds));
    }

    @Override
    protected EntryPhoto toEntity(EntryPhotosRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(EntryPhoto p, EntryPhotosRecord r) {
        MAPPER.toRecord(p, r);
    }

    @Override
    protected Long idOf(EntryPhoto p) {
        return p.getId();
    }

    @Override
    protected void setId(EntryPhoto p, Long id) {
        p.setId(id);
    }

    @Override
    protected String entityName() {
        return "Entry photo";
    }
}
