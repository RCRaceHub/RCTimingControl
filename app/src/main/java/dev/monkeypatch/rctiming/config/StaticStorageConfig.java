package dev.monkeypatch.rctiming.config;

import dev.monkeypatch.rctiming.infrastructure.storage.StorageFolder;
import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serves {@code FilesystemObjectStorageService}'s uploads back over HTTP at {@code /storage/**}
 * — permitted unauthenticated (GET only) in {@code SecurityConfig}, since club logos and TTS
 * clips are public content. The app serves the UI too (#23), so uploads come from the same origin.
 */
@Configuration
public class StaticStorageConfig implements WebMvcConfigurer {

    private final Path folder;

    public StaticStorageConfig(@Value("${storage.local-path:}") String localPath, DatabaseProperties database) {
        this.folder = StorageFolder.resolve(localPath, database);
    }

    /** Maps {@code /storage/**} to the storage folder, the same one {@code FilesystemObjectStorageService} writes to. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = folder.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/storage/**")
                .addResourceLocations(location);
    }
}
