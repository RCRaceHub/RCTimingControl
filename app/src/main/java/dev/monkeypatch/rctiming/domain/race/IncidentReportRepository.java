package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.IncidentReportsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static dev.monkeypatch.rctiming.jooq.generated.tables.IncidentReports.INCIDENT_REPORTS;

/** Repository for {@link IncidentReport} ({@code incident_reports}). */
@Repository
public class IncidentReportRepository extends JooqRepository<IncidentReport, IncidentReportsRecord> {

    public IncidentReportRepository(DSLContext dsl) {
        super(dsl, INCIDENT_REPORTS, INCIDENT_REPORTS.ID);
    }

    @Override
    protected IncidentReport toEntity(IncidentReportsRecord r) {
        IncidentReport i = new IncidentReport();
        i.setId(r.getId());
        i.setRaceId(r.getRaceId());
        i.setEntryId(r.getEntryId());
        i.setIncidentType(r.getIncidentType());
        i.setDescription(r.getDescription());
        i.setRaisedBy(r.getRaisedBy());
        i.setRaisedAt(r.getRaisedAt());
        return i;
    }

    @Override
    protected void toRecord(IncidentReport i, IncidentReportsRecord r) {
        r.setRaceId(i.getRaceId());
        r.setEntryId(i.getEntryId());
        r.setIncidentType(i.getIncidentType());
        r.setDescription(i.getDescription());
        r.setRaisedBy(i.getRaisedBy());
        r.setRaisedAt(i.getRaisedAt());
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
