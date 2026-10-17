package dev.monkeypatch.rctiming.domain.championship;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.ChampionshipExclusionsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipExclusions.CHAMPIONSHIP_EXCLUSIONS;

/**
 * Repository for {@link ChampionshipExclusion} ({@code championship_exclusions}); newest exclusion first when listed.
 */
@Repository
public class ChampionshipExclusionRepository extends JooqRepository<ChampionshipExclusion, ChampionshipExclusionsRecord> {

    public ChampionshipExclusionRepository(DSLContext dsl) {
        super(dsl, CHAMPIONSHIP_EXCLUSIONS, CHAMPIONSHIP_EXCLUSIONS.ID);
    }

    public List<ChampionshipExclusion> findByChampionshipIdOrderByCreatedAtDesc(Long championshipId) {
        return findWhere(CHAMPIONSHIP_EXCLUSIONS.CHAMPIONSHIP_ID.eq(championshipId),
                CHAMPIONSHIP_EXCLUSIONS.CREATED_AT.desc(), CHAMPIONSHIP_EXCLUSIONS.ID.desc());
    }

    public boolean existsByChampionshipIdAndDriverIdAndEventId(Long championshipId, Long driverId, Long eventId) {
        return dsl.fetchExists(CHAMPIONSHIP_EXCLUSIONS, CHAMPIONSHIP_EXCLUSIONS.CHAMPIONSHIP_ID.eq(championshipId)
                .and(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID.eq(driverId))
                .and(CHAMPIONSHIP_EXCLUSIONS.EVENT_ID.eq(eventId)));
    }

    /** How many exclusions name the competitor. */
    public int countByDriverId(Long driverId) {
        return dsl.fetchCount(CHAMPIONSHIP_EXCLUSIONS, CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID.eq(driverId));
    }

    /** Moves one competitor's exclusions to another, as a merge does (#123). Returns how many moved. */
    @Transactional
    public int moveDriver(Long fromDriverId, Long toDriverId) {
        return dsl.update(CHAMPIONSHIP_EXCLUSIONS)
                .set(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID, toDriverId)
                .where(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID.eq(fromDriverId))
                .execute();
    }

    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(CHAMPIONSHIP_EXCLUSIONS.CREATED_AT);
    }

    @Override
    protected ChampionshipExclusion toEntity(ChampionshipExclusionsRecord r) {
        ChampionshipExclusion c = new ChampionshipExclusion();
        c.setId(r.getId());
        c.setChampionshipId(r.getChampionshipId());
        c.setDriverId(r.getDriverId());
        c.setEventId(r.getEventId());
        c.setReason(r.getReason());
        c.setCreatedBy(r.getCreatedBy());
        c.setCreatedAt(r.getCreatedAt());
        return c;
    }

    @Override
    protected void toRecord(ChampionshipExclusion c, ChampionshipExclusionsRecord r) {
        r.setChampionshipId(c.getChampionshipId());
        r.setDriverId(c.getDriverId());
        r.setEventId(c.getEventId());
        r.setReason(c.getReason());
        r.setCreatedBy(c.getCreatedBy());
        r.setCreatedAt(c.getCreatedAt());
    }

    @Override
    protected Long idOf(ChampionshipExclusion c) {
        return c.getId();
    }

    @Override
    protected void setId(ChampionshipExclusion c, Long id) {
        c.setId(id);
    }
}
