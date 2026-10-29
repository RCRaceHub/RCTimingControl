package dev.monkeypatch.rctiming.domain.overlaytheme;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.OverlayThemesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.OverlayThemes.OVERLAY_THEMES;

/** Repository for {@link OverlayTheme} ({@code overlay_themes}). */
@Repository
public class OverlayThemeRepository extends JooqRepository<OverlayTheme, OverlayThemesRecord> {

    private static final OverlayThemeMapper MAPPER = Mappers.getMapper(OverlayThemeMapper.class);

    public OverlayThemeRepository(DSLContext dsl) {
        super(dsl, OVERLAY_THEMES, OVERLAY_THEMES.ID);
    }

    /** The theme with exactly this name, if there is one. */
    public Optional<OverlayTheme> findByName(String name) {
        return findOne(OVERLAY_THEMES.NAME.eq(name));
    }

    /** The club's default theme, if one is set. */
    public Optional<OverlayTheme> findDefault() {
        return findOne(OVERLAY_THEMES.IS_DEFAULT.eq(true));
    }

    /** Every theme, by name. */
    public List<OverlayTheme> findAllByName() {
        return findWhere(OVERLAY_THEMES.ID.isNotNull(), OVERLAY_THEMES.NAME.asc());
    }

    @Override
    protected OverlayTheme toEntity(OverlayThemesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(OverlayTheme t, OverlayThemesRecord r) {
        MAPPER.toRecord(t, r);
    }

    /** The creation time is set once, on insert. */
    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(OVERLAY_THEMES.CREATED_AT);
    }

    @Override
    protected Long idOf(OverlayTheme t) {
        return t.getId();
    }

    @Override
    protected void setId(OverlayTheme t, Long id) {
        t.setId(id);
    }
}
