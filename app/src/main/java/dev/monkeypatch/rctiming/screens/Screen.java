package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;

import java.time.Instant;

/**
 * A display screen (table {@code display_screens}): one EventScreenDisplay viewer, a Raspberry Pi showing a playlist of
 * pages in a full-screen browser (#708). It is keyed by {@link #screenId}, the id the screen reports, never by
 * {@link #host}, because addresses change; host and port are where it was last seen.
 *
 * <p>{@link #apiToken} is the bearer token the screen asks for on changes, kept encrypted by {@code TokenCipher} and
 * never sent to a browser. {@link #pushedRevision} and {@link #pushedSource} are what the manager last set on the
 * screen, so a screen changed by someone else can be flagged.
 */
public class Screen implements CreatedAt, UpdatedAt {

    private Long id;
    private String screenId;
    private String name;
    private String host;
    private int port;
    private String agentVersion;
    private String apiVersion;
    private String apiToken;
    private Long playlistId;
    private Long pushedRevision;
    private String pushedSource;
    private Instant pushedAt;
    private Instant lastSeenAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getScreenId() { return screenId; }
    public void setScreenId(String screenId) { this.screenId = screenId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getAgentVersion() { return agentVersion; }
    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public Long getPlaylistId() { return playlistId; }
    public void setPlaylistId(Long playlistId) { this.playlistId = playlistId; }

    public Long getPushedRevision() { return pushedRevision; }
    public void setPushedRevision(Long pushedRevision) { this.pushedRevision = pushedRevision; }

    public String getPushedSource() { return pushedSource; }
    public void setPushedSource(String pushedSource) { this.pushedSource = pushedSource; }

    public Instant getPushedAt() { return pushedAt; }
    public void setPushedAt(Instant pushedAt) { this.pushedAt = pushedAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
