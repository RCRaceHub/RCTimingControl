package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

/**
 * A named race format that an official can reuse (table {@code race_format_templates}): the config is a {@link
 * RaceFormatConfig} stored as JSON text. A template is only a starting point: a class copies it when it is added to an
 * event, so changing or deleting a template does not affect events already set up (FORMAT-06).
 */
public class RaceFormatTemplate implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private RaceFormatConfig config;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public RaceFormatConfig getConfig() { return config; }
    public void setConfig(RaceFormatConfig config) { this.config = config; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
