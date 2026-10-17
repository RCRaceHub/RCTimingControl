package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ResultSnapshotsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;

/** Repository for {@link ResultSnapshot} ({@code result_snapshots}); a race has at most one. */
@Repository
public class ResultSnapshotRepository extends JooqRepository<ResultSnapshot, ResultSnapshotsRecord> {

    public ResultSnapshotRepository(DSLContext dsl) {
        super(dsl, RESULT_SNAPSHOTS, RESULT_SNAPSHOTS.ID);
    }

    public Optional<ResultSnapshot> findByRaceId(Long raceId) {
        return findOne(RESULT_SNAPSHOTS.RACE_ID.eq(raceId));
    }

    @Override
    protected ResultSnapshot toEntity(ResultSnapshotsRecord r) {
        ResultSnapshot resultSnapshot = new ResultSnapshot();
        resultSnapshot.setId(r.getId());
        resultSnapshot.setRaceId(r.getRaceId());
        resultSnapshot.setFinishedAt(r.getFinishedAt());
        resultSnapshot.setPositionsJson(r.getPositionsJson());
        resultSnapshot.setTimedPositionsJson(r.getTimedPositionsJson());
        resultSnapshot.setLapHistoryJson(r.getLapHistoryJson());
        resultSnapshot.setCreatedAt(r.getCreatedAt());
        return resultSnapshot;
    }

    @Override
    protected void toRecord(ResultSnapshot resultSnapshot, ResultSnapshotsRecord r) {
        r.setRaceId(resultSnapshot.getRaceId());
        r.setFinishedAt(resultSnapshot.getFinishedAt());
        r.setPositionsJson(resultSnapshot.getPositionsJson());
        r.setTimedPositionsJson(resultSnapshot.getTimedPositionsJson());
        r.setLapHistoryJson(resultSnapshot.getLapHistoryJson());
        r.setCreatedAt(resultSnapshot.getCreatedAt());
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
