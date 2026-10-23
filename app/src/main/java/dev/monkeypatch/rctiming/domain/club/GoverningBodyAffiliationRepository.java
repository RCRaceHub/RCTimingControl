package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.GoverningBodyAffiliationsRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import static dev.monkeypatch.rctiming.jooq.generated.tables.GoverningBodyAffiliations.GOVERNING_BODY_AFFILIATIONS;

/** Repository for {@link GoverningBodyAffiliation} ({@code governing_body_affiliations}). */
@Repository
public class GoverningBodyAffiliationRepository
        extends JooqRepository<GoverningBodyAffiliation, GoverningBodyAffiliationsRecord> {

    private static final GoverningBodyAffiliationMapper MAPPER = Mappers.getMapper(GoverningBodyAffiliationMapper.class);

    public GoverningBodyAffiliationRepository(DSLContext dsl) {
        super(dsl, GOVERNING_BODY_AFFILIATIONS, GOVERNING_BODY_AFFILIATIONS.ID);
    }

    @Override
    protected GoverningBodyAffiliation toEntity(GoverningBodyAffiliationsRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(GoverningBodyAffiliation a, GoverningBodyAffiliationsRecord r) {
        MAPPER.toRecord(a, r);
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
