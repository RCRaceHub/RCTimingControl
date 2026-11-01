package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.Screen;
import dev.monkeypatch.rctiming.screens.ScreenOverview;
import dev.monkeypatch.rctiming.screens.ScreenState;
import dev.monkeypatch.rctiming.screens.viewer.ViewerStatus;

import java.time.Instant;
import java.util.List;

/**
 * A screen on the Screens page (#708): who it is, whether it answers, what it shows, and what an official should look
 * at. The saved token is never included, only whether there is one. Times in {@code live} are the screen's own clock in
 * Unix seconds, {@code time} being the screen's clock when it was read; {@code lastSeenAt} is written when the screen goes offline.
 *
 * @param connection {@code ONLINE}, {@code UNREACHABLE}, {@code OFFLINE} or {@code UNKNOWN}
 * @param features   what the screen's agent offers ({@code message}, {@code banner} and so on); null until read
 * @param live       the last status the screen gave, or null if it has not answered since the app started
 */
public record ScreenDto(Long id, String screenId, String name, String host, int port, String agentVersion,
                        String apiVersion, boolean hasToken, Long playlistId, String playlistName, Long pushedRevision,
                        String pushedSource, Instant pushedAt, Instant lastSeenAt, String connection, String error,
                        Instant statusAt, Double clockSkewSeconds, boolean drifted, List<String> features, List<String> warnings, Live live) {

    /** A full-screen message that is up; times are the screen's clock in Unix seconds, {@code until} null when open-ended. */
    public record Message(String style, String title, String text, String level, Double since, Double until) {
    }

    /** A banner that is up; {@code visible} is false when the screen cannot draw it over the page now showing. */
    public record Banner(String text, String level, Double since, Double until, boolean visible) {
    }

    /** What is on screen now. */
    public record Live(String state, boolean paused, Double time, Integer index, Integer playlistLength, Long playlistRevision,
                       String playlistSource, String currentUrl, String currentLabel, Double currentEndsAt,
                       String overrideUrl, Double overrideUntil, Boolean pageReachable, boolean browserRunning,
                       Integer browserRestarts, String browserLastError, Message message, Banner banner) {

        static Live from(ViewerStatus s) {
            return new Live(s.state(), s.paused(), s.time(), s.index(),
                    s.playlist() == null ? null : s.playlist().length(),
                    s.playlist() == null ? null : s.playlist().revision(),
                    s.playlist() == null ? null : s.playlist().source(),
                    s.current() == null ? null : s.current().url(),
                    s.current() == null ? null : s.current().label(),
                    s.current() == null ? null : s.current().endsAt(),
                    s.override() == null ? null : s.override().url(),
                    s.override() == null ? null : s.override().until(),
                    s.network() == null ? null : s.network().pageReachable(),
                    s.browser() != null && s.browser().running(),
                    s.browser() == null ? null : s.browser().restarts(),
                    s.browser() == null ? null : s.browser().lastError(),
                    s.message() == null ? null : new Message(s.message().style(), s.message().title(), s.message().text(),
                            s.message().level(), s.message().since(), s.message().until()),
                    s.banner() == null ? null : new Banner(s.banner().text(), s.banner().level(), s.banner().since(),
                            s.banner().until(), s.banner().visible()));
        }
    }

    public static ScreenDto from(ScreenOverview o) {
        Screen s = o.screen();
        ScreenState st = o.state();
        return new ScreenDto(s.getId(), s.getScreenId(), s.getName(), s.getHost(), s.getPort(), s.getAgentVersion(),
                s.getApiVersion(), s.getApiToken() != null, s.getPlaylistId(), o.playlistName(), s.getPushedRevision(),
                s.getPushedSource(), s.getPushedAt(), s.getLastSeenAt(), st.connection().name(), st.error(),
                st.statusAt(), st.clockSkewSeconds(), st.drifted(), st.features(), st.warnings(),
                st.status() == null ? null : Live.from(st.status()));
    }
}
