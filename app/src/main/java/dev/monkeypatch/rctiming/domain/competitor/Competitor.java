package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

/**
 * A driver who races, independent of any login. Entries point at a competitor, so a racing
 * history can outlive a single meeting and does not depend on an account (L4, #12).
 */
public class Competitor implements CreatedAt, UpdatedAt {

    private Long id;
    private String displayName;
    private String externalSource;
    private String externalId;
    private String brcaNumber;
    private String homeClub;
    private String spokenName;
    private NameVisibility nameVisibility = NameVisibility.SHOW;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getExternalSource() { return externalSource; }
    public void setExternalSource(String externalSource) { this.externalSource = externalSource; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getBrcaNumber() { return brcaNumber; }
    public void setBrcaNumber(String brcaNumber) { this.brcaNumber = brcaNumber; }

    public String getHomeClub() { return homeClub; }
    public void setHomeClub(String homeClub) { this.homeClub = homeClub; }

    /**
     * The admin's override for how the name is said aloud, or null for none (#119). With none, what is
     * spoken is the display name tidied for speech: see {@link #speechName()}.
     */
    public String getSpokenName() { return spokenName; }
    public void setSpokenName(String spokenName) { this.spokenName = spokenName; }

    /**
     * The text to speak for this competitor: the spoken name when set, else the display name tidied for
     * speech (#119, #120).
     */
    public String speechName() {
        return SpeechName.of(spokenName, displayName);
    }

    /** How the name is shown on public screens (#265); officials' own screens always show it in full. */
    public NameVisibility getNameVisibility() { return nameVisibility; }
    public void setNameVisibility(NameVisibility nameVisibility) { this.nameVisibility = nameVisibility; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
