package dev.monkeypatch.rctiming.config;

import dev.monkeypatch.rctiming.infrastructure.storage.StorageFolder;
import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.file.Path;

/**
 * Serves {@code FilesystemObjectStorageService}'s uploads back over HTTP at {@code /storage/**}
 * — permitted unauthenticated (GET only) in {@code SecurityConfig}, since club logos and TTS
 * clips are public content. The app serves the UI too (#23), so uploads come from the same origin. Every upload is
 * sent with {@code Content-Security-Policy: sandbox} and {@code X-Content-Type-Options: nosniff}, so a file that is a
 * script in disguise, such as an SVG logo uploaded before SVG was refused (#525), cannot run on that origin.
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

    /** Adds the sandbox and no-sniff headers to every {@code /storage/**} response, on the request thread. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("Content-Security-Policy", "sandbox");
                response.setHeader("X-Content-Type-Options", "nosniff");
                return true;
            }
        }).addPathPatterns("/storage/**");
    }
}
