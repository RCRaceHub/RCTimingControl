package dev.monkeypatch.rctiming.domain.sponsor;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.OverlayThemeSponsorsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.OverlayThemeSponsors.OVERLAY_THEME_SPONSORS;

/** Repository for {@link OverlayThemeSponsor} ({@code overlay_theme_sponsors}). */
@Repository
public class OverlayThemeSponsorRepository extends JooqRepository<OverlayThemeSponsor, OverlayThemeSponsorsRecord> {

    private static final OverlayThemeSponsorMapper MAPPER = Mappers.getMapper(OverlayThemeSponsorMapper.class);

    public OverlayThemeSponsorRepository(DSLContext dsl) {
        super(dsl, OVERLAY_THEME_SPONSORS, OVERLAY_THEME_SPONSORS.ID);
    }

    /** The theme's sponsors in the order they are shown. */
    public List<OverlayThemeSponsor> findByThemeId(Long themeId) {
        return findWhere(OVERLAY_THEME_SPONSORS.THEME_ID.eq(themeId), OVERLAY_THEME_SPONSORS.SORT_ORDER.asc(), OVERLAY_THEME_SPONSORS.ID.asc());
    }

    @Override
    protected OverlayThemeSponsor toEntity(OverlayThemeSponsorsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(OverlayThemeSponsor s, OverlayThemeSponsorsRecord r) {
        MAPPER.toRecord(s, r);
    }

    @Override
    protected Long idOf(OverlayThemeSponsor s) {
        return s.getId();
    }

    @Override
    protected void setId(OverlayThemeSponsor s, Long id) {
        s.setId(id);
    }
}
