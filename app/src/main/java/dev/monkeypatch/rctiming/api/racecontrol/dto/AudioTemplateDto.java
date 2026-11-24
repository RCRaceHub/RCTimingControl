package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.audiotemplate.AudioTemplate;

/** A template and the settings it holds (never the club's own sound files, and no voice). */
public record AudioTemplateDto(Long id, String name, AudioSettingsDto settings) {
    public static AudioTemplateDto of(AudioTemplate t) {
        return new AudioTemplateDto(t.getId(), t.getName(), AudioSettingsDto.of(t.getSettings()));
    }
}
