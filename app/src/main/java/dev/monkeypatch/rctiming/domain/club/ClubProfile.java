package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

/**
 * The club's own details (table {@code club_profiles}): name and contact, time zone, logo, the announcer settings,
 * where its decoder is, its privacy notice, the RaceHub results address and its live timing thresholds. RCTC runs for one club at one venue, so
 * there is at most one row; {@link ClubProfileService} keeps it that way.
 *
 * The decoder host, port and protocol are null until the setup wizard has set them, and the decoder listener reads them
 * from here. {@code audioSettings} is stored as JSON text (see {@link ClubAudioSettingsConverter}).
 */
public class ClubProfile implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String websiteUrl;
    private Double latitude;
    private Double longitude;
    /** An IANA zone id such as {@code Europe/London}; {@link #setTimezone} rejects an invalid one. */
    private String timezone = "UTC";
    private String logoUrl;
    private ClubAudioSettings audioSettings = ClubAudioSettings.defaults();
    /** The Piper voice the announcer uses unless a clip asks for another. */
    private String defaultVoiceId = "en_GB-alan-medium";
    private String decoderHost;
    private Integer decoderPort;
    private String decoderProtocol;
    /** The club's own notice text, or null to show the default from {@code PrivacyNoticeDefaults} (#267). */
    private String privacyNotice;
    /**
     * Where results are sent to RaceHub; may carry a secret in its query string, so keep it out of logs and audit rows.
     */
    private String racehubResultsUrl;
    private String resultsPublishTarget;
    /** The club's live timing thresholds (#505); stored as one column each, see {@link LiveTimingSettings}. */
    private LiveTimingSettings liveTimingSettings = LiveTimingSettings.defaults();
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) {
        java.time.ZoneId.of(timezone); // validate on set
        this.timezone = timezone;
    }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public ClubAudioSettings getAudioSettings() { return audioSettings; }
    public void setAudioSettings(ClubAudioSettings audioSettings) { this.audioSettings = audioSettings; }

    public String getDefaultVoiceId() { return defaultVoiceId; }
    public void setDefaultVoiceId(String defaultVoiceId) { this.defaultVoiceId = defaultVoiceId; }

    public String getDecoderHost() { return decoderHost; }
    public void setDecoderHost(String decoderHost) { this.decoderHost = decoderHost; }

    public Integer getDecoderPort() { return decoderPort; }
    public void setDecoderPort(Integer decoderPort) { this.decoderPort = decoderPort; }

    public String getDecoderProtocol() { return decoderProtocol; }
    public void setDecoderProtocol(String decoderProtocol) { this.decoderProtocol = decoderProtocol; }

    public String getRacehubResultsUrl() { return racehubResultsUrl; }
    public void setRacehubResultsUrl(String racehubResultsUrl) { this.racehubResultsUrl = racehubResultsUrl; }

    public String getResultsPublishTarget() { return resultsPublishTarget; }
    public void setResultsPublishTarget(String resultsPublishTarget) { this.resultsPublishTarget = resultsPublishTarget; }

    public LiveTimingSettings getLiveTimingSettings() { return liveTimingSettings; }
    public void setLiveTimingSettings(LiveTimingSettings liveTimingSettings) { this.liveTimingSettings = liveTimingSettings; }

    public String getPrivacyNotice() { return privacyNotice; }
    public void setPrivacyNotice(String privacyNotice) { this.privacyNotice = privacyNotice; }
}
