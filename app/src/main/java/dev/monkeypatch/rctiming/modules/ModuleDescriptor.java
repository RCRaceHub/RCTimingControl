package dev.monkeypatch.rctiming.modules;

/**
 * What a module is: a built-in feature a club can switch off (#795). A module declares itself by exposing one
 * {@code @Bean ModuleDescriptor}; {@link ModuleRegistry} collects them at start-up.
 *
 * <p>The id is the key everywhere: the {@code modules} table, the {@link RequiresModule} annotation and the calls to
 * {@link ModuleSwitch}. Keep it short, lower case and stable, since renaming it loses the saved switch.
 *
 * @param id          lower-case letters, digits and hyphens, at most 50 characters
 * @param name        what officials see on the Modules page
 * @param description one sentence on what the module does and what switching it off hides
 * @param version     the module's own version, shown on the Modules page
 */
public record ModuleDescriptor(String id, String name, String description, String version) {

    private static final String ID_PATTERN = "[a-z][a-z0-9-]{0,49}";

    public ModuleDescriptor {
        if (id == null || !id.matches(ID_PATTERN)) {
            throw new IllegalArgumentException("A module id is lower case letters, digits and hyphens, not '" + id + "'");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Module " + id + " needs a name");
        }
        description = description == null ? "" : description;
        version = version == null || version.isBlank() ? "1" : version;
    }
}
