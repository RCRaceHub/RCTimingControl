package dev.monkeypatch.rctiming.timing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for approving or disapproving a wrong-heat flag: the transponder number it was raised for. */
public record WrongHeatDecisionRequestDto(@NotBlank @Size(max = 50) String transponderNumber) {}
