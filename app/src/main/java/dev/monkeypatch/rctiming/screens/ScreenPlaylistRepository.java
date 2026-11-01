package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.DisplayPlaylistsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.DisplayPlaylists.DISPLAY_PLAYLISTS;

/** Repository for {@link ScreenPlaylist} ({@code display_playlists}). */
@Repository
public class ScreenPlaylistRepository extends JooqRepository<ScreenPlaylist, DisplayPlaylistsRecord> {

    private static final ScreenPlaylistMapper MAPPER = Mappers.getMapper(ScreenPlaylistMapper.class);

    public ScreenPlaylistRepository(DSLContext dsl) {
        super(dsl, DISPLAY_PLAYLISTS, DISPLAY_PLAYLISTS.ID);
    }

    /** The playlist with this name, if there is one. */
    public Optional<ScreenPlaylist> findByName(String name) {
        return findOne(DISPLAY_PLAYLISTS.NAME.eq(name));
    }

    /** Every playlist, by name. */
    public List<ScreenPlaylist> findAllByName() {
        return findWhere(DISPLAY_PLAYLISTS.ID.isNotNull(), DISPLAY_PLAYLISTS.NAME.asc());
    }

    @Override
    protected ScreenPlaylist toEntity(DisplayPlaylistsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(ScreenPlaylist p, DisplayPlaylistsRecord r) {
        MAPPER.toRecord(p, r);
    }

    /** The creation time is set once, on insert. */
    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(DISPLAY_PLAYLISTS.CREATED_AT);
    }

    @Override
    protected Long idOf(ScreenPlaylist p) {
        return p.getId();
    }

    @Override
    protected void setId(ScreenPlaylist p, Long id) {
        p.setId(id);
    }
}
