package dev.monkeypatch.rctiming.infrastructure.tts;

/** A Piper voice offered in the announcer settings: its model name, a readable label and whether it is the default. */
public record VoiceInfo(String voiceId, String label, boolean isDefault) {}
