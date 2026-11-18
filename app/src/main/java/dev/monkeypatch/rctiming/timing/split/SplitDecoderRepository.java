package dev.monkeypatch.rctiming.timing.split;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.SplitDecoders.SPLIT_DECODERS;

/**
 * The {@code split_decoders} table (#672). A plain repository, because a {@link SplitDecoder} is a record with no
 * entity behind it. Writes must run in the caller's write transaction.
 */
@Repository
public class SplitDecoderRepository {

    private final DSLContext dsl;

    public SplitDecoderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Every split decoder, in split order. */
    public List<SplitDecoder> findAll() {
        return dsl.select().from(SPLIT_DECODERS).orderBy(SPLIT_DECODERS.SPLIT_NUMBER).fetch(this::toDecoder);
    }

    /** One split decoder by row id. */
    public Optional<SplitDecoder> findById(long id) {
        return dsl.select().from(SPLIT_DECODERS).where(SPLIT_DECODERS.ID.eq(id)).fetchOptional(this::toDecoder);
    }

    /** True when another row already uses this split number. {@code exceptId} is the row being edited, or null. */
    public boolean splitNumberTaken(int splitNumber, Long exceptId) {
        var condition = SPLIT_DECODERS.SPLIT_NUMBER.eq(splitNumber);
        if (exceptId != null) {
            condition = condition.and(SPLIT_DECODERS.ID.ne(exceptId));
        }
        return dsl.fetchExists(SPLIT_DECODERS, condition);
    }

    /** Inserts a new row and returns it with its id. */
    public SplitDecoder insert(SplitDecoder decoder) {
        long id = dsl.insertInto(SPLIT_DECODERS)
                .set(SPLIT_DECODERS.NAME, decoder.name())
                .set(SPLIT_DECODERS.HOST, decoder.host())
                .set(SPLIT_DECODERS.PORT, decoder.port())
                .set(SPLIT_DECODERS.SPLIT_NUMBER, decoder.splitNumber())
                .set(SPLIT_DECODERS.CLOCK_OFFSET_MS, decoder.clockOffsetMs())
                .set(SPLIT_DECODERS.ENABLED, decoder.enabled())
                .returning(SPLIT_DECODERS.ID).fetchOne().getId();
        return findById(id).orElseThrow();
    }

    /** Replaces every column of the row with this id. */
    public void update(SplitDecoder decoder) {
        dsl.update(SPLIT_DECODERS)
                .set(SPLIT_DECODERS.NAME, decoder.name())
                .set(SPLIT_DECODERS.HOST, decoder.host())
                .set(SPLIT_DECODERS.PORT, decoder.port())
                .set(SPLIT_DECODERS.SPLIT_NUMBER, decoder.splitNumber())
                .set(SPLIT_DECODERS.CLOCK_OFFSET_MS, decoder.clockOffsetMs())
                .set(SPLIT_DECODERS.ENABLED, decoder.enabled())
                .where(SPLIT_DECODERS.ID.eq(decoder.id())).execute();
    }

    /** Deletes the row with this id. */
    public void deleteById(long id) {
        dsl.deleteFrom(SPLIT_DECODERS).where(SPLIT_DECODERS.ID.eq(id)).execute();
    }

    private SplitDecoder toDecoder(Record r) {
        return new SplitDecoder(r.get(SPLIT_DECODERS.ID), r.get(SPLIT_DECODERS.NAME), r.get(SPLIT_DECODERS.HOST),
                r.get(SPLIT_DECODERS.PORT), r.get(SPLIT_DECODERS.SPLIT_NUMBER),
                r.get(SPLIT_DECODERS.CLOCK_OFFSET_MS), r.get(SPLIT_DECODERS.ENABLED));
    }
}
