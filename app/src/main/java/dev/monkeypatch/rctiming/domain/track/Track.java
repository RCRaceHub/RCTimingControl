package dev.monkeypatch.rctiming.domain.track;

import dev.monkeypatch.rctiming.persistence.CreatedAt;
import dev.monkeypatch.rctiming.persistence.UpdatedAt;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A track the club races at (table {@code tracks}). {@code decoderLoops} and {@code lapThresholds} are loaded and saved
 * with it by {@link TrackRepository}.
 */
public class Track implements CreatedAt, UpdatedAt {

    private Long id;
    private String name;
    private String venueNotes;
    private Double trackLength;
    private List<DecoderLoop> decoderLoops = new ArrayList<>();
    private List<TrackLapThreshold> lapThresholds = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVenueNotes() { return venueNotes; }
    public void setVenueNotes(String venueNotes) { this.venueNotes = venueNotes; }

    public Double getTrackLength() { return trackLength; }
    public void setTrackLength(Double trackLength) { this.trackLength = trackLength; }

    public List<DecoderLoop> getDecoderLoops() { return decoderLoops; }
    public void setDecoderLoops(List<DecoderLoop> decoderLoops) { this.decoderLoops = decoderLoops; }

    public List<TrackLapThreshold> getLapThresholds() { return lapThresholds; }
    public void setLapThresholds(List<TrackLapThreshold> lapThresholds) { this.lapThresholds = lapThresholds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
