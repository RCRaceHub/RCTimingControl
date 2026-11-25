package dev.monkeypatch.rctiming.modules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a package, and every package below it, belongs to one module (#798). Put it on the package's
 * {@code package-info.java}, with the same id as the module's {@link ModuleDescriptor}.
 *
 * <p>{@code ModuleBoundariesTest} then enforces the module rules: a module depends on core and never on another
 * module, core never depends on a module, and every event listener, scheduled job and message handler in a module
 * checks {@link ModuleSwitch}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface ModulePackage {

    /** The {@link ModuleDescriptor#id()} of the module that owns the package. */
    String value();
}
