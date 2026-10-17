package dev.monkeypatch.rctiming.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the developer Javadoc at {@code /javadoc} (#352), like the user docs, when
 * {@code rctiming.docs.javadoc.enabled} is on. It is off by default and on in the demo.
 *
 * <p>The Javadoc is in the jar only when it was built with {@code -PbundleJavadoc}, under
 * {@code classpath:/javadoc/}, which is not a static location, so nothing serves it unless this class is active.
 * Without it in the jar the paths are plain 404s. The pages are public, like the rest of the frontend: they
 * describe the code, not the club's data.
 */
@Configuration
@ConditionalOnProperty(prefix = "rctiming.docs.javadoc", name = "enabled", havingValue = "true")
public class JavadocConfig implements WebMvcConfigurer {

    static final String LOCATION = "classpath:/javadoc/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/javadoc/**")
                .addResourceLocations(LOCATION)
                .setCacheControl(CacheControl.noCache());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/javadoc", "/javadoc/index.html");
        registry.addRedirectViewController("/javadoc/", "/javadoc/index.html");
    }
}
