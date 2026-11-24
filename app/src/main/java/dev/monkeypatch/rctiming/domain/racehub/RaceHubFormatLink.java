package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

/**
 * Links a race format of the club's RaceHub to one of RCTC's format templates, once for the whole club (table
 * {@code racehub_format_links}). The key is the id RaceHub sends as the entry export's {@code event.race_format_id}.
 * A meeting imported with a linked format adds its classes using that template, so the club picks the format in
 * RaceHub and does not choose it again here.
 */
public class RaceHubFormatLink implements CreatedAt, UpdatedAt {

    private Long id;
    private String formatKey;
    private Long templateId;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFormatKey() { return formatKey; }
    public void setFormatKey(String formatKey) { this.formatKey = formatKey; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
