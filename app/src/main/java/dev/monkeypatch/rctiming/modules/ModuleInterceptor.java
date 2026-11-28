package dev.monkeypatch.rctiming.modules;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Turns a request for a switched-off module into a 404 before the controller runs (#795). Runs on the request thread
 * for every handler; a handler with no {@link RequiresModule} on its method or class passes straight through.
 */
@Component
public class ModuleInterceptor implements HandlerInterceptor {

    private final ModuleSwitch moduleSwitch;

    public ModuleInterceptor(ModuleSwitch moduleSwitch) {
        this.moduleSwitch = moduleSwitch;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            RequiresModule required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresModule.class);
            if (required == null) {
                required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresModule.class);
            }
            if (required != null) {
                moduleSwitch.require(required.value());
            }
        }
        return true;
    }
}
