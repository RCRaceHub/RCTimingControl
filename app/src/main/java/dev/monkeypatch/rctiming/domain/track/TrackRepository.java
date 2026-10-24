package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.TracksRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Tracks.TRACKS;

/**
 * Tracks, loaded with their decoder loops and lap thresholds. Saving a track also saves its loops
 * and thresholds and deletes the ones no longer in its lists, as the JPA cascade did. Deleting a
 * track deletes them through the foreign keys.
 */
@Repository
public class TrackRepository extends JooqRepository<Track, TracksRecord> {

    private final DecoderLoopRepository decoderLoops;
    private final TrackLapThresholdRepository lapThresholds;

    private static final TrackMapper MAPPER = Mappers.getMapper(TrackMapper.class);

    public TrackRepository(DSLContext dsl, DecoderLoopRepository decoderLoops,
                           TrackLapThresholdRepository lapThresholds) {
        super(dsl, TRACKS, TRACKS.ID);
        this.decoderLoops = decoderLoops;
        this.lapThresholds = lapThresholds;
    }

    @Override
    @Transactional
    public Track save(Track track) {
        super.save(track);
        Long trackId = track.getId();
        track.getDecoderLoops().forEach(loop -> loop.setTrackId(trackId));
        decoderLoops.saveAll(track.getDecoderLoops());
        List<Long> keptLoops = track.getDecoderLoops().stream().map(DecoderLoop::getId).toList();
        decoderLoops.findByTrackId(trackId).stream()
                .map(DecoderLoop::getId)
                .filter(id -> !keptLoops.contains(id))
                .forEach(decoderLoops::deleteById);

        track.getLapThresholds().forEach(threshold -> threshold.setTrackId(trackId));
        lapThresholds.saveAll(track.getLapThresholds());
        List<Long> keptThresholds = track.getLapThresholds().stream().map(TrackLapThreshold::getId).toList();
        lapThresholds.findByTrackId(trackId).stream()
                .map(TrackLapThreshold::getId)
                .filter(id -> !keptThresholds.contains(id))
                .forEach(lapThresholds::deleteById);
        return track;
    }

    @Override
    protected Optional<Track> findOne(Condition condition) {
        return super.findOne(condition).map(this::withChildren);
    }

    @Override
    protected List<Track> findWhere(Condition condition, SortField<?>... order) {
        return super.findWhere(condition, order).stream().map(this::withChildren).toList();
    }

    private Track withChildren(Track track) {
        track.setDecoderLoops(decoderLoops.findByTrackId(track.getId()));
        track.setLapThresholds(lapThresholds.findByTrackId(track.getId()));
        return track;
    }

    @Override
    protected Track toEntity(TracksRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(Track t, TracksRecord r) {
        MAPPER.toRecord(t, r);
    }

    @Override
    protected Long idOf(Track t) {
        return t.getId();
    }

    @Override
    protected void setId(Track t, Long id) {
        t.setId(id);
    }
}
