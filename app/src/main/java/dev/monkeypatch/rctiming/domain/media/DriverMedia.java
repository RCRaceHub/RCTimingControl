package dev.monkeypatch.rctiming.domain.media;

/**
 * A competitor's optional extras for the boards (table {@code competitor_media}): a headshot and the driver's own
 * sponsors. The headshot is personal data; {@link DriverMediaService} removes it when the competitor is anonymised.
 * {@code sponsors} holds one sponsor name per line.
 */
public class DriverMedia {

    private Long id;
    private Long competitorId;
    private String headshotUrl;
    private String sponsors = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompetitorId() { return competitorId; }
    public void setCompetitorId(Long competitorId) { this.competitorId = competitorId; }

    public String getHeadshotUrl() { return headshotUrl; }
    public void setHeadshotUrl(String headshotUrl) { this.headshotUrl = headshotUrl; }

    public String getSponsors() { return sponsors; }
    public void setSponsors(String sponsors) { this.sponsors = sponsors; }
}
