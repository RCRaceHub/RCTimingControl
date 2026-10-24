package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.RaceFormatTemplatesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceFormatTemplates.RACE_FORMAT_TEMPLATES;

/**
 * Repository for {@link RaceFormatTemplate} ({@code race_format_templates}); the config is converted with {@link
 * RaceFormatConfigConverter}.
 */
@Repository
public class RaceFormatTemplateRepository extends JooqRepository<RaceFormatTemplate, RaceFormatTemplatesRecord> {

    static final RaceFormatConfigConverter CONFIG = new RaceFormatConfigConverter();

    private static final RaceFormatTemplateMapper MAPPER = Mappers.getMapper(RaceFormatTemplateMapper.class);

    public RaceFormatTemplateRepository(DSLContext dsl) {
        super(dsl, RACE_FORMAT_TEMPLATES, RACE_FORMAT_TEMPLATES.ID);
    }

    @Override
    protected RaceFormatTemplate toEntity(RaceFormatTemplatesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(RaceFormatTemplate t, RaceFormatTemplatesRecord r) {
        MAPPER.toRecord(t, r);
    }

    /** The creation time is set once, on insert. */
    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(RACE_FORMAT_TEMPLATES.CREATED_AT);
    }

    @Override
    protected Long idOf(RaceFormatTemplate t) {
        return t.getId();
    }

    @Override
    protected void setId(RaceFormatTemplate t, Long id) {
        t.setId(id);
    }
}
