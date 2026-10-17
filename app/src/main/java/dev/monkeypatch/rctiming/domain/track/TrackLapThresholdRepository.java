package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.TrackLapThresholdsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.TrackLapThresholds.TRACK_LAP_THRESHOLDS;

/**
 * Repository for {@link TrackLapThreshold} ({@code track_lap_thresholds}); {@link TrackRepository} saves them with
 * their track.
 */
@Repository
public class TrackLapThresholdRepository extends JooqRepository<TrackLapThreshold, TrackLapThresholdsRecord> {

    public TrackLapThresholdRepository(DSLContext dsl) {
        super(dsl, TRACK_LAP_THRESHOLDS, TRACK_LAP_THRESHOLDS.ID);
    }

    public List<TrackLapThreshold> findByTrackId(Long trackId) {
        return findWhere(TRACK_LAP_THRESHOLDS.TRACK_ID.eq(trackId));
    }

    public Optional<TrackLapThreshold> findByTrackIdAndRacingClassId(Long trackId, Long racingClassId) {
        return findOne(TRACK_LAP_THRESHOLDS.TRACK_ID.eq(trackId)
                .and(TRACK_LAP_THRESHOLDS.RACING_CLASS_ID.eq(racingClassId)));
    }

    /** The track's default threshold, the one with no class. */
    public Optional<TrackLapThreshold> findByTrackIdAndRacingClassIsNull(Long trackId) {
        return findOne(TRACK_LAP_THRESHOLDS.TRACK_ID.eq(trackId)
                .and(TRACK_LAP_THRESHOLDS.RACING_CLASS_ID.isNull()));
    }

    /** Loads thresholds with their class names, which the admin screens show. */
    @Override
    protected Optional<TrackLapThreshold> findOne(Condition condition) {
        return withClassName(condition).fetchOptional(this::toEntityWithClassName);
    }

    @Override
    protected List<TrackLapThreshold> findWhere(Condition condition, SortField<?>... order) {
        return withClassName(condition).orderBy(order).fetch(this::toEntityWithClassName);
    }

    private SelectConditionStep<Record> withClassName(Condition condition) {
        return dsl.select(TRACK_LAP_THRESHOLDS.fields())
                .select(RACING_CLASSES.NAME)
                .from(TRACK_LAP_THRESHOLDS)
                .leftJoin(RACING_CLASSES).on(RACING_CLASSES.ID.eq(TRACK_LAP_THRESHOLDS.RACING_CLASS_ID))
                .where(condition == null ? DSL.noCondition() : condition);
    }

    private TrackLapThreshold toEntityWithClassName(Record row) {
        TrackLapThreshold threshold = toEntity(row.into(TRACK_LAP_THRESHOLDS));
        threshold.setRacingClassName(row.get(RACING_CLASSES.NAME));
        return threshold;
    }

    @Override
    protected TrackLapThreshold toEntity(TrackLapThresholdsRecord r) {
        TrackLapThreshold t = new TrackLapThreshold();
        t.setId(r.getId());
        t.setTrackId(r.getTrackId());
        t.setRacingClassId(r.getRacingClassId());
        t.setMinLapMs(r.getMinLapMs());
        t.setMaxLastLapMs(r.getMaxLastLapMs());
        t.setCreatedAt(r.getCreatedAt());
        return t;
    }

    @Override
    protected void toRecord(TrackLapThreshold t, TrackLapThresholdsRecord r) {
        r.setTrackId(t.getTrackId());
        r.setRacingClassId(t.getRacingClassId());
        r.setMinLapMs(t.getMinLapMs());
        r.setMaxLastLapMs(t.getMaxLastLapMs());
        r.setCreatedAt(t.getCreatedAt());
    }

    @Override
    protected Long idOf(TrackLapThreshold t) {
        return t.getId();
    }

    @Override
    protected void setId(TrackLapThreshold t, Long id) {
        t.setId(id);
    }
}
