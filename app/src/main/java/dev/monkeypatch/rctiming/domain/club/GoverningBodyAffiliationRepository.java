package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.GoverningBodyAffiliationsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static dev.monkeypatch.rctiming.jooq.generated.tables.GoverningBodyAffiliations.GOVERNING_BODY_AFFILIATIONS;

/** Repository for {@link GoverningBodyAffiliation} ({@code governing_body_affiliations}). */
@Repository
public class GoverningBodyAffiliationRepository
        extends JooqRepository<GoverningBodyAffiliation, GoverningBodyAffiliationsRecord> {

    public GoverningBodyAffiliationRepository(DSLContext dsl) {
        super(dsl, GOVERNING_BODY_AFFILIATIONS, GOVERNING_BODY_AFFILIATIONS.ID);
    }

    @Override
    protected GoverningBodyAffiliation toEntity(GoverningBodyAffiliationsRecord r) {
        GoverningBodyAffiliation a = new GoverningBodyAffiliation();
        a.setId(r.getId());
        a.setCode(r.getCode());
        a.setDisplayName(r.getDisplayName());
        a.setMembershipRequired(r.getMembershipRequired());
        a.setCreatedAt(r.getCreatedAt());
        return a;
    }

    @Override
    protected void toRecord(GoverningBodyAffiliation a, GoverningBodyAffiliationsRecord r) {
        r.setCode(a.getCode());
        r.setDisplayName(a.getDisplayName());
        r.setMembershipRequired(a.isMembershipRequired());
        r.setCreatedAt(a.getCreatedAt());
    }

    @Override
    protected Long idOf(GoverningBodyAffiliation a) {
        return a.getId();
    }

    @Override
    protected void setId(GoverningBodyAffiliation a, Long id) {
        a.setId(id);
    }
}
