package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.modules.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the results export module (#799): the outbox that sends results to RaceHub, the publish target choice and
 * the results site. While it is off nothing is queued or sent, the site folder is not written, and its endpoints
 * answer 404. Results that finish while it is off are not queued when it is switched back on; "Send again" on an
 * event queues them. The RaceHub address and key are not part of it: they stay with the RaceHub imports in core.
 */
@Configuration
public class ResultsExportModule {

    /** The module id used by {@code @RequiresModule} and the switch. */
    public static final String ID = "results-export";

    @Bean
    ModuleDescriptor resultsExportModuleDescriptor() {
        return new ModuleDescriptor(ID, "Results export",
                "Sends each finished race's results to RaceHub, and builds the club's results site pages.", "1");
    }
}
