package dev.monkeypatch.rctiming.modules;

/** A request reached a module that is switched off. The API answers 404, as if the feature did not exist (#795). */
public class ModuleDisabledException extends RuntimeException {

    private final String moduleId;

    public ModuleDisabledException(String moduleId) {
        super("Not found");
        this.moduleId = moduleId;
    }

    /** Which module was asked for, for logs; the 404 body never says. */
    public String moduleId() {
        return moduleId;
    }
}
