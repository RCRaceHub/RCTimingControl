package dev.monkeypatch.rctiming.domain.media;

/**
 * The colours of an entry's car (table {@code entry_car_colours}), for screens that show a colour where there is no
 * photo. {@code colours} holds one to four {@code #rrggbb} values separated by commas, in the order given.
 */
public class EntryCarColours {

    private Long id;
    private Long entryId;
    private String colours;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public String getColours() { return colours; }
    public void setColours(String colours) { this.colours = colours; }
}
