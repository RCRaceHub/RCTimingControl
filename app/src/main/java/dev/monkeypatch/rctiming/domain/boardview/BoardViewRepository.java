package dev.monkeypatch.rctiming.domain.boardview;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.BoardViewsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.BoardViews.BOARD_VIEWS;

/** Repository for {@link BoardView} ({@code board_views}). */
@Repository
public class BoardViewRepository extends JooqRepository<BoardView, BoardViewsRecord> {

    private static final BoardViewMapper MAPPER = Mappers.getMapper(BoardViewMapper.class);

    public BoardViewRepository(DSLContext dsl) {
        super(dsl, BOARD_VIEWS, BOARD_VIEWS.ID);
    }

    /** The view with this address name, if there is one. */
    public Optional<BoardView> findBySlug(String slug) {
        return findOne(BOARD_VIEWS.SLUG.eq(slug));
    }

    /** Every view, by name. */
    public List<BoardView> findAllByName() {
        return findWhere(BOARD_VIEWS.ID.isNotNull(), BOARD_VIEWS.NAME.asc());
    }

    @Override
    protected BoardView toEntity(BoardViewsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(BoardView v, BoardViewsRecord r) {
        MAPPER.toRecord(v, r);
    }

    /** The creation time is set once, on insert. */
    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(BOARD_VIEWS.CREATED_AT);
    }

    @Override
    protected Long idOf(BoardView v) {
        return v.getId();
    }

    @Override
    protected void setId(BoardView v, Long id) {
        v.setId(id);
    }
}
