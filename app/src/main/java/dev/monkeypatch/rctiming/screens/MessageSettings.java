package dev.monkeypatch.rctiming.screens;

import java.util.List;

/**
 * Everything race control saves about messages on the screens (#716), kept as one JSON document: the preset messages
 * offered on the Screens page and the two opt-in hooks into the event flow. Read and written by {@link
 * MessageSettingsService}; a club that has saved nothing gets {@link #defaults()}.
 *
 * @param presets the saved messages, in the order shown
 * @param hooks   what RCTC sends by itself as the event moves on
 */
public record MessageSettings(List<Preset> presets, Hooks hooks) {

    /** A named message an official sends with one click. */
    public record Preset(String name, ScreenMessage message) {
    }

    /**
     * The event-flow hooks, both off unless an official switches them on (they go to every screen).
     *
     * @param holdAfterQualifying put up {@code holdMessage} when the last qualifying heat of an event finishes
     * @param announceFinals      when the finals line-ups are confirmed, push {@code finalsPlaylistId} (when set) and
     *                            then put up {@code finalsMessage}
     * @param finalsPlaylistId    the saved playlist for the finals boards, or null to announce without pushing
     */
    public record Hooks(boolean holdAfterQualifying, ScreenMessage holdMessage, boolean announceFinals,
                        Long finalsPlaylistId, ScreenMessage finalsMessage) {
    }

    /** The presets a new club starts with, and both hooks off. */
    public static MessageSettings defaults() {
        return new MessageSettings(List.of(
                new Preset("Finals being calculated",
                        new ScreenMessage("Finals being calculated", "Lists will be up shortly", "hold", "info", null)),
                new Preset("Short break",
                        new ScreenMessage("Short break", "Racing resumes soon", "hold", "info", null)),
                new Preset("Track maintenance",
                        new ScreenMessage("Track maintenance", "Please stay clear of the track", "hold", "warning", null)),
                new Preset("Finals lists are up",
                        new ScreenMessage("Finals lists are up", null, "announce", "info", 30)),
                new Preset("All drivers to the rostrum",
                        new ScreenMessage("Race control: all drivers to the rostrum", null, "announce", "urgent", 60))),
                defaultHooks());
    }

    private static Hooks defaultHooks() {
        return new Hooks(false,
                new ScreenMessage("Finals being calculated", "Lists will be up shortly", "hold", "info", null),
                false, null, new ScreenMessage("Finals lists are up", null, "announce", "info", 30));
    }
}
