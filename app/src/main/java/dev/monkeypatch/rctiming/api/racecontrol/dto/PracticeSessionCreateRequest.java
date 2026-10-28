package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/v1/practice-sessions}.
 *
 * @param name         what the session is called; may be null
 * @param eventId      the event the session belongs to; null for none
 * @param bestLapN     how many consecutive laps the average is taken over (default 5)
 * @param pauseSeconds how long a transponder may be quiet before its next lap starts a new run (default 180)
 */
public record PracticeSessionCreateRequest(@Size(max = 200) String name, Long eventId, Integer bestLapN,
                                           Integer pauseSeconds) {}
