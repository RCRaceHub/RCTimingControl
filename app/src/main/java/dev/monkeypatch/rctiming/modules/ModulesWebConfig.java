package dev.monkeypatch.rctiming.modules;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts {@link ModuleInterceptor} in front of every controller (#795). */
@Configuration
public class ModulesWebConfig implements WebMvcConfigurer {

    private final ModuleInterceptor interceptor;

    public ModulesWebConfig(ModuleInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
