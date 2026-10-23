package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.DecoderLoopsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.DecoderLoops.DECODER_LOOPS;

/** Repository for {@link DecoderLoop} ({@code decoder_loops}); {@link TrackRepository} saves them with their track. */
@Repository
public class DecoderLoopRepository extends JooqRepository<DecoderLoop, DecoderLoopsRecord> {

    private static final DecoderLoopMapper MAPPER = Mappers.getMapper(DecoderLoopMapper.class);

    public DecoderLoopRepository(DSLContext dsl) {
        super(dsl, DECODER_LOOPS, DECODER_LOOPS.ID);
    }

    public List<DecoderLoop> findByTrackId(Long trackId) {
        return findWhere(DECODER_LOOPS.TRACK_ID.eq(trackId));
    }

    @Override
    protected DecoderLoop toEntity(DecoderLoopsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(DecoderLoop loop, DecoderLoopsRecord r) {
        MAPPER.toRecord(loop, r);
    }

    @Override
    protected Long idOf(DecoderLoop loop) {
        return loop.getId();
    }

    @Override
    protected void setId(DecoderLoop loop, Long id) {
        loop.setId(id);
    }
}
