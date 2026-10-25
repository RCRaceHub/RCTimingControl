package dev.monkeypatch.rctiming.infrastructure.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsFileTest {

    @TempDir
    Path folder;

    @Test
    void createsTheFileAndReadsTheValueBack() {
        SettingsFile settings = new SettingsFile(folder.resolve("data").resolve("application.properties"));

        assertThat(settings.get("rctiming.https.enabled")).isEmpty();
        settings.set("rctiming.https.enabled", "true");

        assertThat(settings.get("rctiming.https.enabled")).contains("true");
    }

    @Test
    void changesOnlyTheNamedLineAndKeepsCommentsAndOtherSettings() throws Exception {
        Path file = folder.resolve("application.properties");
        Files.writeString(file, "# The port\nserver.port = 80\n\n#rctiming.https.enabled=true\nrctiming.https.enabled: false\n");
        SettingsFile settings = new SettingsFile(file);

        settings.set("rctiming.https.enabled", "true");

        assertThat(Files.readAllLines(file)).containsExactly(
                "# The port", "server.port = 80", "", "#rctiming.https.enabled=true", "rctiming.https.enabled=true");
        assertThat(settings.get("server.port")).contains("80");
    }

    @Test
    void aKeyThatOnlyStartsTheSameIsAnotherKey() throws Exception {
        Path file = folder.resolve("application.properties");
        Files.writeString(file, "rctiming.https.enabled.extra=1\n");
        SettingsFile settings = new SettingsFile(file);

        settings.set("rctiming.https.enabled", "true");

        assertThat(Files.readAllLines(file)).containsExactly("rctiming.https.enabled.extra=1", "rctiming.https.enabled=true");
    }
}
