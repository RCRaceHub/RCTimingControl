package dev.monkeypatch.rctiming.modules;

/**
 * Published once a module's club-wide switch change has committed (#799), so a module that holds connections or
 * background work (a decoder listener, say) can start or stop it. Published on the thread that made the change.
 *
 * @param moduleId the {@link ModuleDescriptor#id()}
 * @param enabled  the new state
 */
public record ModuleSwitchedEvent(String moduleId, boolean enabled) {}
