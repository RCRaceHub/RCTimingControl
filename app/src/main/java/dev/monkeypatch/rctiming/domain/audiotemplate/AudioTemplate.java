package dev.monkeypatch.rctiming.domain.audiotemplate;

import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;

import java.time.Instant;

/**
 * A saved audio template (table {@code audio_templates}): a name and a copy of the club's {@link ClubAudioSettings},
 * such as "BRCA meeting", "Club night" or "Practice". The copy never holds the club's own sound files; applying a
 * template leaves those as they are.
 */
public class AudioTemplate implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private ClubAudioSettings settings;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ClubAudioSettings getSettings() { return settings; }
    public void setSettings(ClubAudioSettings settings) { this.settings = settings; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
