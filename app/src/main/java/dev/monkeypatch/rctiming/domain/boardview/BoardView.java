package dev.monkeypatch.rctiming.domain.boardview;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;

import java.time.Instant;

/**
 * A saved board view (table {@code board_views}): a named screen built from panels and opened at {@code
 * /boards/view/{slug}}. The panels are kept as JSON text; {@link BoardViewService} reads and writes them.
 */
public class BoardView implements CreatedAt, UpdatedAt {

    private Long id;
    private String slug;
    private String name;
    private int columns;
    private String panels;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; }

    public String getPanels() { return panels; }
    public void setPanels(String panels) { this.panels = panels; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
