package dev.monkeypatch.rctiming.modules;

/** A request reached a module that is switched off. The API answers 404, as if the feature did not exist (#795). */
public class ModuleDisabledException extends RuntimeException {

    public ModuleDisabledException(String moduleId) {
        super("Not found");
    }
}
