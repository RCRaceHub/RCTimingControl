package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Saving and reading the message presets and hooks (#716). */
class MessageSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    private final MessageSettingsRepository repository = mock(MessageSettingsRepository.class);
    private final ScreenService screens = mock(ScreenService.class);
    private final MessageSettingsService service = new MessageSettingsService(repository, screens,
            mock(AuditService.class, RETURNS_DEEP_STUBS), JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    private static ScreenMessage message(String title) {
        return new ScreenMessage(title, null, null, null, null);
    }

    @Test
    void aClubThatSavedNothingGetsTheFiveStarterPresetsAndBothHooksOff() {
        when(repository.find()).thenReturn(Optional.empty());

        MessageSettings settings = service.get();

        assertThat(settings.presets()).extracting(MessageSettings.Preset::name).containsExactly("Finals being calculated",
                "Short break", "Track maintenance", "Finals lists are up", "All drivers to the rostrum");
        assertThat(settings.hooks().holdAfterQualifying()).isFalse();
        assertThat(settings.hooks().announceFinals()).isFalse();
        assertThat(settings.hooks().finalsMessage().duration()).isEqualTo(30);
    }

    @Test
    void savedSettingsAreCleanedWrittenAsJsonAndReadBack() {
        var captured = ArgumentCaptor.forClass(String.class);
        var preset = new MessageSettings.Preset("  Lunch ", new ScreenMessage(" Lunch ", "Back at 1", null, "WARNING", 600));
        var hooks = new MessageSettings.Hooks(true, message("Hold"), true, null, message("Up"));

        MessageSettings saved = service.save(Actor.system("test"), new MessageSettings(List.of(preset), hooks));

        assertThat(saved.presets()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("Lunch");
            assertThat(p.message().title()).isEqualTo("Lunch");
            assertThat(p.message().style()).isEqualTo("hold");
            assertThat(p.message().level()).isEqualTo("warning");
        });
        verify(repository).save(captured.capture(), org.mockito.ArgumentMatchers.eq(NOW));
        when(repository.find()).thenReturn(Optional.of(captured.getValue()));
        assertThat(service.get()).isEqualTo(saved);
    }

    @Test
    void aSwitchedOffHookMayHaveABlankMessageAndKeepsTheDefault() {
        var hooks = new MessageSettings.Hooks(false, message(""), false, null, message("Up"));

        MessageSettings saved = service.save(Actor.system("t"), new MessageSettings(List.of(), hooks));

        assertThat(saved.hooks().holdMessage()).isEqualTo(MessageSettings.defaults().hooks().holdMessage());
    }

    @Test
    void aSavedDocumentMissingAHookMessageFallsBackToTheDefaults() {
        when(repository.find()).thenReturn(Optional.of("{\"presets\":[],\"hooks\":{\"holdAfterQualifying\":true}}"));

        assertThat(service.get()).isEqualTo(MessageSettings.defaults());
    }

    @Test
    void anUnreadableSavedDocumentFallsBackToTheDefaults() {
        when(repository.find()).thenReturn(Optional.of("{\"presets\": 5}"));

        assertThat(service.get()).isEqualTo(MessageSettings.defaults());
    }

    @Test
    void aBadPresetOrHookMessageIsRefusedAndNothingIsSaved() {
        var hooks = new MessageSettings.Hooks(false, message("Hold"), false, null, message("Up"));

        assertThatThrownBy(() -> service.save(Actor.system("t"), new MessageSettings(
                List.of(new MessageSettings.Preset(" ", message("A"))), hooks))).hasMessageContaining("name");
        assertThatThrownBy(() -> service.save(Actor.system("t"), new MessageSettings(
                List.of(new MessageSettings.Preset("A", message("x")), new MessageSettings.Preset("a", message("y"))), hooks)))
                .hasMessageContaining("Two saved messages");
        assertThatThrownBy(() -> service.save(Actor.system("t"), new MessageSettings(
                List.of(new MessageSettings.Preset("A", message(" "))), hooks))).hasMessageContaining("title");
        assertThatThrownBy(() -> service.save(Actor.system("t"), new MessageSettings(List.of(),
                new MessageSettings.Hooks(true, message(""), false, null, message("Up"))))).hasMessageContaining("title");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any(), any());
    }
}
