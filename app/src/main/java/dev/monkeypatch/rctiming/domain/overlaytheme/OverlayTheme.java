package dev.monkeypatch.rctiming.domain.overlaytheme;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;

import java.time.Instant;

/**
 * A saved overlay theme (table {@code overlay_themes}): a named look for the streaming overlay. The design is a JSON
 * object kept as text; the Overlay Designer writes it and the overlay reads it, so its fields are the front end's.
 */
public class OverlayTheme implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private String design;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDesign() { return design; }
    public void setDesign(String design) { this.design = design; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
