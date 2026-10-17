package dev.monkeypatch.rctiming.infrastructure.tts;

/** Piper cannot be used: TTS is switched off, or the server is not there or stopped answering. Callers degrade to no clip. */
public class TtsUnavailableException extends RuntimeException {
    public TtsUnavailableException(String message) {
        super(message);
    }

    public TtsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
