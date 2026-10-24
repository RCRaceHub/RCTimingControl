package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ResultSnapshotsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;

/** Repository for {@link ResultSnapshot} ({@code result_snapshots}); a race has at most one. */
@Repository
public class ResultSnapshotRepository extends JooqRepository<ResultSnapshot, ResultSnapshotsRecord> {

    private static final ResultSnapshotMapper MAPPER = Mappers.getMapper(ResultSnapshotMapper.class);

    public ResultSnapshotRepository(DSLContext dsl) {
        super(dsl, RESULT_SNAPSHOTS, RESULT_SNAPSHOTS.ID);
    }

    public Optional<ResultSnapshot> findByRaceId(Long raceId) {
        return findOne(RESULT_SNAPSHOTS.RACE_ID.eq(raceId));
    }

    @Override
    protected ResultSnapshot toEntity(ResultSnapshotsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(ResultSnapshot resultSnapshot, ResultSnapshotsRecord r) {
        MAPPER.toRecord(resultSnapshot, r);
    }

    @Override
    protected Long idOf(ResultSnapshot resultSnapshot) {
        return resultSnapshot.getId();
    }

    @Override
    protected void setId(ResultSnapshot resultSnapshot, Long id) {
        resultSnapshot.setId(id);
    }
}
