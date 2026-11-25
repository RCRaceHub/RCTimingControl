package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.modules.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the open practice module (#799). While it is off the practice session endpoints answer 404, a passing
 * with no race running is not counted as a practice lap, and the menu entry is hidden. Saved sessions and laps are
 * kept, and the competitor merge and data export still cover them.
 */
@Configuration
public class PracticeModule {

    /** The module id used by {@code @RequiresModule} and the switch. */
    public static final String ID = "practice";

    @Bean
    ModuleDescriptor practiceModuleDescriptor() {
        return new ModuleDescriptor(ID, "Open practice",
                "Practice sessions outside any heat: laps grouped into runs, average over the best laps and a day view across sessions.",
                "1");
    }
}
