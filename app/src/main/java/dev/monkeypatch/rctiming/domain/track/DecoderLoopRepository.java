package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.DecoderLoopsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.DecoderLoops.DECODER_LOOPS;

/** Repository for {@link DecoderLoop} ({@code decoder_loops}); {@link TrackRepository} saves them with their track. */
@Repository
public class DecoderLoopRepository extends JooqRepository<DecoderLoop, DecoderLoopsRecord> {

    public DecoderLoopRepository(DSLContext dsl) {
        super(dsl, DECODER_LOOPS, DECODER_LOOPS.ID);
    }

    public List<DecoderLoop> findByTrackId(Long trackId) {
        return findWhere(DECODER_LOOPS.TRACK_ID.eq(trackId));
    }

    @Override
    protected DecoderLoop toEntity(DecoderLoopsRecord r) {
        DecoderLoop loop = new DecoderLoop();
        loop.setId(r.getId());
        loop.setTrackId(r.getTrackId());
        loop.setLoopId(r.getLoopId());
        loop.setDisplayName(r.getDisplayName());
        loop.setLoopType(r.getLoopType() == null ? null : LoopType.valueOf(r.getLoopType()));
        loop.setScoringLoop(r.getIsScoringLoop());
        loop.setCreatedAt(r.getCreatedAt());
        return loop;
    }

    @Override
    protected void toRecord(DecoderLoop loop, DecoderLoopsRecord r) {
        r.setTrackId(loop.getTrackId());
        r.setLoopId(loop.getLoopId());
        r.setDisplayName(loop.getDisplayName());
        r.setLoopType(loop.getLoopType() == null ? null : loop.getLoopType().name());
        r.setIsScoringLoop(loop.isScoringLoop());
        r.setCreatedAt(loop.getCreatedAt());
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
