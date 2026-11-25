package dev.monkeypatch.rctiming.modules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleRegistryTest {

    private static ModuleDescriptor module(String id) {
        return new ModuleDescriptor(id, "Name " + id, "What it does", "1");
    }

    @Test
    void findsAModuleByIdAndListsThemInOrder() {
        ModuleRegistry registry = new ModuleRegistry(List.of(module("screens"), module("practice")));

        assertThat(registry.find("practice")).isPresent();
        assertThat(registry.find("nope")).isEmpty();
        assertThat(registry.all()).extracting(ModuleDescriptor::id).containsExactly("screens", "practice");
    }

    @Test
    void refusesTwoModulesWithOneId() {
        assertThatThrownBy(() -> new ModuleRegistry(List.of(module("screens"), module("screens"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("screens");
    }

    @Test
    void refusesAnIdThatIsNotLowerCaseWithHyphens() {
        assertThatThrownBy(() -> module("Split Timing")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> module("")).isInstanceOf(IllegalArgumentException.class);
    }
}
