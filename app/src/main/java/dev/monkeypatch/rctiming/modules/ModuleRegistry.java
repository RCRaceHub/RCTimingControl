package dev.monkeypatch.rctiming.modules;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every module the app has, collected from the {@link ModuleDescriptor} beans (#795). Built once at start-up, so it is
 * safe to read from any thread. Two modules with one id stop the app starting, since they would share one switch.
 */
@Component
public class ModuleRegistry {

    private final Map<String, ModuleDescriptor> byId = new LinkedHashMap<>();

    public ModuleRegistry(List<ModuleDescriptor> descriptors) {
        for (ModuleDescriptor d : descriptors) {
            if (byId.put(d.id(), d) != null) {
                throw new IllegalStateException("Two modules are called '" + d.id() + "'");
            }
        }
    }

    /** All modules, in the order their beans were registered. */
    public List<ModuleDescriptor> all() {
        return List.copyOf(byId.values());
    }

    /** The module with this id, if the app has one. */
    public Optional<ModuleDescriptor> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
