package dev.monkeypatch.rctiming.modules;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModuleSwitchTest {

    private static final Instant NOW = Instant.parse("2026-11-25T09:00:00Z");

    private ModuleSwitchRepository repository;
    private AuditService audit;
    private ModuleSwitch moduleSwitch;

    @BeforeEach
    void setUp() {
        repository = mock(ModuleSwitchRepository.class);
        audit = mock(AuditService.class, RETURNS_DEEP_STUBS);
        when(repository.findAllSwitches()).thenReturn(Map.of());
        when(repository.findAllEventOffs()).thenReturn(Set.of());
        when(repository.eventExists(anyLong())).thenReturn(true);
        ModuleRegistry registry = new ModuleRegistry(List.of(
                new ModuleDescriptor("screens", "Screens", "Display screens", "1")));
        moduleSwitch = new ModuleSwitch(registry, repository, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aModuleIsOnUntilSomeoneSwitchesItOff() {
        assertThat(moduleSwitch.isEnabled("screens")).isTrue();
    }

    @Test
    void anUnknownModuleIsOffSoATypoNeverRunsAFeature() {
        assertThat(moduleSwitch.isEnabled("screns")).isFalse();
        assertThatThrownBy(() -> moduleSwitch.require("screns")).isInstanceOf(ModuleDisabledException.class);
    }

    @Test
    void switchingOffTakesEffectAtOnceSavesAndAudits() {
        moduleSwitch.setEnabled("screens", false, Actor.official(7));

        assertThat(moduleSwitch.isEnabled("screens")).isFalse();
        assertThatThrownBy(() -> moduleSwitch.require("screens")).isInstanceOf(ModuleDisabledException.class);
        verify(repository).saveSwitch("screens", false, 7L, NOW);
        verify(audit).entry(any(Actor.class), eq("MODULE_SWITCHED"));
    }

    @Test
    void switchingAgainToTheSameStateIsSavedButNotAuditedTwice() {
        moduleSwitch.setEnabled("screens", true, Actor.official(7));

        verify(repository).saveSwitch("screens", true, 7L, NOW);
        verify(audit, never()).entry(any(), any());
    }

    @Test
    void anEventCanTurnAModuleOffButNeverOnWhenTheClubHasItOff() {
        moduleSwitch.setEventOff(3, "screens", true, Actor.official(7));

        assertThat(moduleSwitch.isEnabled("screens", 3)).isFalse();
        assertThat(moduleSwitch.isEnabled("screens", 4)).isTrue();

        moduleSwitch.setEventOff(3, "screens", false, Actor.official(7));
        assertThat(moduleSwitch.isEnabled("screens", 3)).isTrue();

        moduleSwitch.setEnabled("screens", false, Actor.official(7));
        assertThat(moduleSwitch.isEnabled("screens", 3)).isFalse();
    }

    @Test
    void savedSwitchesAreReadOnceAndKeptInMemory() {
        when(repository.findAllSwitches()).thenReturn(Map.of("screens", false));

        assertThat(moduleSwitch.isEnabled("screens")).isFalse();
        assertThat(moduleSwitch.isEnabled("screens")).isFalse();

        verify(repository, org.mockito.Mockito.times(1)).findAllSwitches();
    }

    @Test
    void switchingAnUnknownModuleIs404() {
        assertThatThrownBy(() -> moduleSwitch.setEnabled("nope", false, Actor.official(7)))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> moduleSwitch.setEventOff(1, "nope", true, Actor.official(7)))
                .isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).addEventOff(anyLong(), any());
    }
}
