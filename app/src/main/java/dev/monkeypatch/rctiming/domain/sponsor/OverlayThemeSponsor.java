package dev.monkeypatch.rctiming.domain.sponsor;

/**
 * A sponsor owned by an overlay theme (table {@code overlay_theme_sponsors}): a name and an optional logo, shown in the
 * sponsor bar of an overlay opened with that theme. {@code logoUrl} is the address the logo is served from;
 * {@code sortOrder} is the position among the theme's sponsors.
 */
public class OverlayThemeSponsor {

    private Long id;
    private Long themeId;
    private String name;
    private String logoUrl;
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
