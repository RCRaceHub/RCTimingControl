package dev.monkeypatch.rctiming.domain.sponsor;

/**
 * A sponsor of an event (table {@code event_sponsors}): a name and an optional logo, shown in the overlay's sponsor
 * bar. {@code logoUrl} is the address the logo is served from; {@code sortOrder} is the position among the event's
 * sponsors.
 */
public class EventSponsor {

    private Long id;
    private Long eventId;
    private String name;
    private String logoUrl;
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
