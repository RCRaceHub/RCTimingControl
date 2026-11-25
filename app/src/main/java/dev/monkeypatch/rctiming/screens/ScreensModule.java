package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.modules.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the screens manager module (#799). While it is off the Screens admin endpoints answer 404, nothing polls,
 * discovers or messages a display screen, and the menu entry is hidden. The screens, playlists and message settings
 * already saved are kept.
 */
@Configuration
public class ScreensModule {

    /** The module id used by {@code @RequiresModule} and the switch. */
    public static final String ID = "screens";

    @Bean
    ModuleDescriptor screensModuleDescriptor() {
        return new ModuleDescriptor(ID, "Screens manager",
                "Finds the club's Raspberry Pi display screens, shows what each one is doing and sends them pages, messages and banners.",
                "1");
    }
}
