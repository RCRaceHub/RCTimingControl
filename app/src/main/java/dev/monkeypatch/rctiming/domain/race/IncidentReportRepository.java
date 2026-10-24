package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.IncidentReportsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import static dev.monkeypatch.rctiming.jooq.generated.tables.IncidentReports.INCIDENT_REPORTS;

/** Repository for {@link IncidentReport} ({@code incident_reports}). */
@Repository
public class IncidentReportRepository extends JooqRepository<IncidentReport, IncidentReportsRecord> {

    private static final IncidentReportMapper MAPPER = Mappers.getMapper(IncidentReportMapper.class);

    public IncidentReportRepository(DSLContext dsl) {
        super(dsl, INCIDENT_REPORTS, INCIDENT_REPORTS.ID);
    }

    @Override
    protected IncidentReport toEntity(IncidentReportsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(IncidentReport i, IncidentReportsRecord r) {
        MAPPER.toRecord(i, r);
    }

    @Override
    protected Long idOf(IncidentReport i) {
        return i.getId();
    }

    @Override
    protected void setId(IncidentReport i, Long id) {
        i.setId(id);
    }
}
