package dev.monkeypatch.rctiming.screens.viewer;

/**
 * A call to a screen's agent failed. {@link #httpStatus()} is the agent's answer (400 bad field, 401 token, 412
 * If-Match and so on, with the agent's own message), or null when there was no answer at all: the screen is off, off
 * the network, or slow.
 */
public class ViewerException extends RuntimeException {

    private final Integer httpStatus;

    public ViewerException(Integer httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** The agent's HTTP status, or null when it did not answer. */
    public Integer httpStatus() {
        return httpStatus;
    }

    /** True when the agent did not answer. */
    public boolean unreachable() {
        return httpStatus == null;
    }
}
