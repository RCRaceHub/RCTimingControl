package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.retention.RetentionProperties;
import dev.monkeypatch.rctiming.retention.RetentionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** How long personal data is kept (#262), for an admin to read. The periods are set in the app's configuration. */
@RestController
@RequestMapping("/api/v1/admin/retention")
@PreAuthorize("hasRole('ADMIN')")
public class RetentionController {

    private final RetentionService retention;

    public RetentionController(RetentionService retention) {
        this.retention = retention;
    }

    @GetMapping
    public RetentionProperties policy() {
        return retention.policy();
    }
}
