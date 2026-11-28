package dev.monkeypatch.rctiming.timing.split;

import dev.monkeypatch.rctiming.modules.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the split-timing module (#799). While it is off the split decoders are not read, no sector times are
 * worked out or saved, the split endpoints answer 404 and the screens show no sector columns. Sector times already
 * saved are kept.
 */
@Configuration
public class SplitTimingModule {

    /** The module id used by {@code @RequiresModule} and the switch. */
    public static final String ID = "split-timing";

    @Bean
    ModuleDescriptor splitTimingModuleDescriptor() {
        return new ModuleDescriptor(ID, "Split timing",
                "Extra decoders on the track that give each car's sector times, shown on race control, the boards and the overlay.",
                "1");
    }
}
