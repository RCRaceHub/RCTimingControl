package dev.monkeypatch.rctiming.api.screens;

import dev.monkeypatch.rctiming.screens.viewer.ViewerException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the screens module's own failures into responses, so the core handler does not know the module. */
@RestControllerAdvice(basePackageClasses = ScreensExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScreensExceptionHandler {

    /**
     * A screen's agent could not be reached or refused a call (#708): 502, with its own message as {@code detail}. The
     * fault is the screen's or the network's, not the caller's and not this app's.
     */
    @ExceptionHandler(ViewerException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ProblemDetail handleViewerFailure(ViewerException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
}
