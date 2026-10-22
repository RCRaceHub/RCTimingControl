package dev.monkeypatch.rctiming.domain.scoring;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;

import java.time.Instant;

/**
 * A named, reusable set of scoring rules (table {@code scoring_systems}). A built-in system is written by a migration
 * and is locked: it cannot be changed or deleted, only cloned. The {@link #getKind() kind} always matches the config.
 */
public class ScoringSystem implements CreatedAt, UpdatedAt {

    private Long id;
    private ScoringKind kind;
    private String name;
    private boolean builtIn;
    private ScoringConfig config;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ScoringKind getKind() { return kind; }
    public void setKind(ScoringKind kind) { this.kind = kind; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }

    public ScoringConfig getConfig() { return config; }
    public void setConfig(ScoringConfig config) { this.config = config; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
