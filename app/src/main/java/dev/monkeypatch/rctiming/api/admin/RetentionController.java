package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.retention.RetentionProperties;
import dev.monkeypatch.rctiming.retention.RetentionService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How long personal data is kept (#262), for an admin to read. The periods are set in the app's configuration.
 * {@code GET /api/v1/admin/retention}; ADMIN only; read only.
 */
@RestController
@RequestMapping("/api/v1/admin/retention")
@AdminOnly
public class RetentionController {

    private final RetentionService retention;

    public RetentionController(RetentionService retention) {
        this.retention = retention;
    }

    /** The retention periods in force. */
    @GetMapping
    public RetentionProperties policy() {
        return retention.policy();
    }
}
