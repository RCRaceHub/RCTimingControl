package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;

public class ClubProfile implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String websiteUrl;
    private Double latitude;
    private Double longitude;
    private String timezone = "UTC";
    private String logoUrl;
    private ClubAudioSettings audioSettings = ClubAudioSettings.defaults();
    private String defaultVoiceId = "en_GB-alan-medium";
    private String decoderHost;
    private Integer decoderPort;
    private String decoderProtocol;
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
}
