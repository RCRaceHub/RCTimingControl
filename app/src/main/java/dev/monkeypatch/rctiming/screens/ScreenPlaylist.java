package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;

import java.time.Instant;

/**
 * A saved playlist (table {@code display_playlists}): an ordered list of pages with a dwell time and a label each,
 * pushed to one or more screens (#708). The items are kept as JSON text; {@link ScreenService} reads and writes them.
 */
public class ScreenPlaylist implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private int defaultDuration;
    private String items;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDefaultDuration() { return defaultDuration; }
    public void setDefaultDuration(int defaultDuration) { this.defaultDuration = defaultDuration; }

    public String getItems() { return items; }
    public void setItems(String items) { this.items = items; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
