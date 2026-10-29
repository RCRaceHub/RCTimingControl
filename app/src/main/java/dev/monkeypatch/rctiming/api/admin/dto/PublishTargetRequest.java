package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.resultsexport.ResultsPublishTarget;

/** @param target where to publish results; null clears the choice, so the default applies */
public record PublishTargetRequest(ResultsPublishTarget target) {}
