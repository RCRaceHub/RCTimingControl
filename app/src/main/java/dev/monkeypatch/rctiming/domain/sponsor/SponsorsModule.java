package dev.monkeypatch.rctiming.domain.sponsor;

import dev.monkeypatch.rctiming.modules.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the sponsors module (#799). While it is off the sponsor admin screens and the public sponsor list answer
 * 404, the event and overlay-designer screens hide their sponsor parts, and the overlay shows no sponsor bar. The
 * sponsors already saved are kept.
 */
@Configuration
public class SponsorsModule {

    /** The module id used by {@code @RequiresModule} and the switch. */
    public static final String ID = "sponsors";

    @Bean
    ModuleDescriptor sponsorsModuleDescriptor() {
        return new ModuleDescriptor(ID, "Sponsors",
                "Sponsor names and logos for each event and overlay theme, shown on the streaming overlay's sponsor bar.",
                "1");
    }
}
