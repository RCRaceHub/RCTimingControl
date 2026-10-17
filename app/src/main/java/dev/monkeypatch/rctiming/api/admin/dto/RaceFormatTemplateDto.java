package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.format.RaceFormatConfig;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplate;

/**
 * A race format template, the answer of the format endpoints under {@code /api/v1/admin/formats}.
 */
public record RaceFormatTemplateDto(Long id, String name, RaceFormatConfig config) {

    public static RaceFormatTemplateDto from(RaceFormatTemplate t) {
        return new RaceFormatTemplateDto(t.getId(), t.getName(), t.getConfig());
    }
}
