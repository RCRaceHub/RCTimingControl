package dev.monkeypatch.rctiming.domain.practice;

/** A practice session's state: {@code IDLE} then {@code RUNNING} then {@code STOPPED}. It cannot be restarted. */
public enum PracticeStatus {
    IDLE,
    RUNNING,
    STOPPED
}
