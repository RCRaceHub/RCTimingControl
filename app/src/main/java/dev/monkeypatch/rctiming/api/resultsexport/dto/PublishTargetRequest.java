package dev.monkeypatch.rctiming.api.resultsexport.dto;

import dev.monkeypatch.rctiming.domain.club.ResultsPublishTarget;

/** @param target where to publish results; null clears the choice, so the default applies */
public record PublishTargetRequest(ResultsPublishTarget target) {}
