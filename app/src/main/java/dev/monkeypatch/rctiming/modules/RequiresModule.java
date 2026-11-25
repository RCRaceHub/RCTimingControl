package dev.monkeypatch.rctiming.modules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller (or one of its methods) as belonging to a module (#795). While the module is switched off, a
 * request to it answers 404 and the controller never runs; {@link ModuleInterceptor} does the check. Listeners and
 * scheduled jobs of a module call {@link ModuleSwitch#isEnabled(String)} instead.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresModule {

    /** The {@link ModuleDescriptor#id()} of the module. */
    String value();
}
