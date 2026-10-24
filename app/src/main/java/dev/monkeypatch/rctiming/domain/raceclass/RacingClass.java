package dev.monkeypatch.rctiming.domain.raceclass;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

/**
 * A kind of racing the club runs, such as {@code Stock Buggy} (table {@code racing_classes}). It is the club-wide
 * definition; {@code EventClass} is that class entered in one event with its race format. Imports place rows in a class
 * by its name ({@link dev.monkeypatch.rctiming.domain.Names#matchKey}).
 */
public class RacingClass implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private String description;
    private String colour;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** The colour that marks this class on line-ups and live timing, as {@code #rrggbb}, or null for none (#482). */
    public String getColour() { return colour; }
    public void setColour(String colour) { this.colour = colour; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
