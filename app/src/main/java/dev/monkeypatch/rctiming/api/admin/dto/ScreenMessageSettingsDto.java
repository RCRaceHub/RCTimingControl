package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.MessageSettings;
import dev.monkeypatch.rctiming.screens.ScreenMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The saved message presets and event hooks (#716), both the answer of {@code GET /api/v1/admin/screens/message-settings}
 * and the body of {@code PUT} to it. A message's {@code seconds} is null for until cleared.
 */
public record ScreenMessageSettingsDto(@NotNull @Size(max = 100) List<@NotNull @Valid Preset> presets,
                                       @NotNull @Valid Hooks hooks) {

    /** A message with no name, as the hooks hold it. */
    public record Message(@Size(max = 1000) String title, @Size(max = 2000) String text, @Size(max = 20) String style,
                          @Size(max = 20) String level, Integer seconds) {

        static Message from(ScreenMessage m) {
            return new Message(m.title(), m.text(), m.style(), m.level(), m.duration());
        }

        ScreenMessage toMessage() {
            return new ScreenMessage(title, text, style, level, seconds);
        }
    }

    /** A named message. */
    public record Preset(@Size(max = 200) String name, @NotNull @Valid Message message) {
    }

    /** The two opt-in hooks; see {@link MessageSettings.Hooks}. */
    public record Hooks(boolean holdAfterQualifying, @NotNull @Valid Message holdMessage, boolean announceFinals,
                        Long finalsPlaylistId, @NotNull @Valid Message finalsMessage) {
    }

    public static ScreenMessageSettingsDto from(MessageSettings s) {
        MessageSettings.Hooks h = s.hooks();
        return new ScreenMessageSettingsDto(
                s.presets().stream().map(p -> new Preset(p.name(), Message.from(p.message()))).toList(),
                new Hooks(h.holdAfterQualifying(), Message.from(h.holdMessage()), h.announceFinals(),
                        h.finalsPlaylistId(), Message.from(h.finalsMessage())));
    }

    /** The settings in the form the service takes. */
    public MessageSettings toSettings() {
        return new MessageSettings(
                presets.stream().map(p -> new MessageSettings.Preset(p.name(), p.message().toMessage())).toList(),
                new MessageSettings.Hooks(hooks.holdAfterQualifying(), hooks.holdMessage().toMessage(),
                        hooks.announceFinals(), hooks.finalsPlaylistId(), hooks.finalsMessage().toMessage()));
    }
}
