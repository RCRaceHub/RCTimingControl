package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.DisplayScreensRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.DisplayScreens.DISPLAY_SCREENS;

/** Repository for {@link Screen} ({@code display_screens}). */
@Repository
public class ScreenRepository extends JooqRepository<Screen, DisplayScreensRecord> {

    private static final ScreenMapper MAPPER = Mappers.getMapper(ScreenMapper.class);

    public ScreenRepository(DSLContext dsl) {
        super(dsl, DISPLAY_SCREENS, DISPLAY_SCREENS.ID);
    }

    /** The screen that reports this id, if it is known. */
    public Optional<Screen> findByScreenId(String screenId) {
        return findOne(DISPLAY_SCREENS.SCREEN_ID.eq(screenId));
    }

    /** Every known screen, by name and then id. */
    public List<Screen> findAllByName() {
        return findWhere(DISPLAY_SCREENS.ID.isNotNull(), DISPLAY_SCREENS.NAME.asc(), DISPLAY_SCREENS.SCREEN_ID.asc());
    }

    /** The screens a playlist is assigned to. */
    public List<Screen> findByPlaylistId(Long playlistId) {
        return findWhere(DISPLAY_SCREENS.PLAYLIST_ID.eq(playlistId));
    }

    /** Records when a screen was last reachable, without touching its other columns or its update time. */
    public void recordLastSeen(Long id, Instant seenAt) {
        dsl.update(DISPLAY_SCREENS).set(DISPLAY_SCREENS.LAST_SEEN_AT, seenAt).where(DISPLAY_SCREENS.ID.eq(id)).execute();
    }

    /** Takes a deleted playlist off every screen it was assigned to. */
    public void clearPlaylist(Long playlistId) {
        dsl.update(DISPLAY_SCREENS).setNull(DISPLAY_SCREENS.PLAYLIST_ID)
                .where(DISPLAY_SCREENS.PLAYLIST_ID.eq(playlistId)).execute();
    }

    @Override
    protected Screen toEntity(DisplayScreensRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(Screen s, DisplayScreensRecord r) {
        MAPPER.toRecord(s, r);
    }

    /** The creation time is set once, on insert. */
    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(DISPLAY_SCREENS.CREATED_AT);
    }

    @Override
    protected Long idOf(Screen s) {
        return s.getId();
    }

    @Override
    protected void setId(Screen s, Long id) {
        s.setId(id);
    }
}
