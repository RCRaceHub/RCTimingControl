package dev.monkeypatch.rctiming.domain.audiotemplate;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.AudioTemplatesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.AudioTemplates.AUDIO_TEMPLATES;

/** Repository for {@link AudioTemplate} ({@code audio_templates}). */
@Repository
public class AudioTemplateRepository extends JooqRepository<AudioTemplate, AudioTemplatesRecord> {

    private static final AudioTemplateMapper MAPPER = Mappers.getMapper(AudioTemplateMapper.class);

    public AudioTemplateRepository(DSLContext dsl) {
        super(dsl, AUDIO_TEMPLATES, AUDIO_TEMPLATES.ID);
    }

    /** The template with this name, ignoring case, if there is one. */
    public Optional<AudioTemplate> findByName(String name) {
        return findOne(AUDIO_TEMPLATES.NAME.equalIgnoreCase(name));
    }

    /** Every template, by name. */
    public List<AudioTemplate> findAllByName() {
        return findWhere(AUDIO_TEMPLATES.ID.isNotNull(), AUDIO_TEMPLATES.NAME.asc());
    }

    @Override
    protected AudioTemplate toEntity(AudioTemplatesRecord r) {
        return MAPPER.toEntity(r);
    }

    @Override
    protected void toRecord(AudioTemplate t, AudioTemplatesRecord r) {
        MAPPER.toRecord(t, r);
    }

    /** The creation time is set once, on insert. */
    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(AUDIO_TEMPLATES.CREATED_AT);
    }

    @Override
    protected Long idOf(AudioTemplate t) {
        return t.getId();
    }

    @Override
    protected void setId(AudioTemplate t, Long id) {
        t.setId(id);
    }
}
