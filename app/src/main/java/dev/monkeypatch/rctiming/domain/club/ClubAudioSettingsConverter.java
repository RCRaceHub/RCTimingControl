package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.persistence.convert.JsonTextConverter;
import tools.jackson.core.type.TypeReference;

/** Stores the club's announcer settings as JSON. */
public class ClubAudioSettingsConverter extends JsonTextConverter<ClubAudioSettings> {

    public ClubAudioSettingsConverter() {
        super(new TypeReference<>() {});
    }
}
