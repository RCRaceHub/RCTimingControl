package dev.monkeypatch.rctiming.domain.media;

/** The photo of an entry's car for the event (table {@code entry_photos}); {@code carPhotoUrl} is its address in object storage. */
public class EntryPhoto {

    private Long id;
    private Long entryId;
    private String carPhotoUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public String getCarPhotoUrl() { return carPhotoUrl; }
    public void setCarPhotoUrl(String carPhotoUrl) { this.carPhotoUrl = carPhotoUrl; }
}
