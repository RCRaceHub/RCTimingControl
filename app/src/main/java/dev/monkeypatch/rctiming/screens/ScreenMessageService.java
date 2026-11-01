package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.screens.viewer.ViewerBanner;
import dev.monkeypatch.rctiming.screens.viewer.ViewerClient;
import dev.monkeypatch.rctiming.screens.viewer.ViewerException;
import dev.monkeypatch.rctiming.screens.viewer.ViewerMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What race control says to the people at the venue through the screens (#716): a full-screen message, a banner along
 * the bottom, and "Finals are up", which pushes the finals boards and then announces them. Each goes to the screens
 * given, all at once through {@link ScreenControlService#onEach}, so a screen that is off or refuses (an agent older
 * than 0.6.0 answers 404) is reported in its own {@link ScreenActionResult} and never holds up the rest. The caller
 * can send again to just the screens that failed. Runs on the request thread, or on a worker thread for {@link
 * ScreenMessageHooks}; calls the screens outside any transaction. Every send and clear is audited.
 */
@Service
public class ScreenMessageService {

    private final ScreenService screens;
    private final ScreenControlService control;
    private final ScreenMonitor monitor;
    private final ViewerClient client;

    public ScreenMessageService(ScreenService screens, ScreenControlService control, ScreenMonitor monitor,
                                ViewerClient client) {
        this.screens = screens;
        this.control = control;
        this.monitor = monitor;
        this.client = client;
    }

    /**
     * Puts a message over the whole of each screen, replacing any already up.
     *
     * @throws IllegalArgumentException (400) for a missing title, text that is too long, or a bad style, level or time
     */
    public List<ScreenActionResult> sendMessage(Actor actor, List<Long> screenIds, ScreenMessage message) {
        ViewerMessage.Body body = ScreenRules.checkMessage(message);
        List<ScreenActionResult> results = control.onEach(screens.screens(screenIds), screen -> {
            needing("messages", () -> client.putMessage(control.target(screen), body));
            monitor.poll(screen);
        });
        control.audit(actor, "SCREENS_MESSAGE_SENT", "Put up the message \"" + body.title() + "\""
                + describe(body.style(), body.level(), body.duration()) + " on ", results);
        return results;
    }

    /** Takes the message down on each screen. */
    public List<ScreenActionResult> clearMessage(Actor actor, List<Long> screenIds) {
        List<ScreenActionResult> results = control.onEach(screens.screens(screenIds), screen -> {
            needing("messages", () -> client.clearMessage(control.target(screen)));
            monitor.poll(screen);
        });
        control.audit(actor, "SCREENS_MESSAGE_CLEARED", "Cleared the message on ", results);
        return results;
    }

    /**
     * Puts a banner along the bottom of each screen, replacing any already up.
     *
     * @throws IllegalArgumentException (400) for no text, text over 500 characters, or a bad level or time
     */
    public List<ScreenActionResult> sendBanner(Actor actor, List<Long> screenIds, ScreenBanner banner) {
        ViewerBanner.Body body = ScreenRules.checkBanner(banner);
        List<ScreenActionResult> results = control.onEach(screens.screens(screenIds), screen -> {
            needing("banners", () -> client.putBanner(control.target(screen), body));
            monitor.poll(screen);
        });
        control.audit(actor, "SCREENS_BANNER_SENT", "Put up the banner \"" + abbreviate(body.text()) + "\""
                + describe(null, body.level(), body.duration()) + " on ", results);
        return results;
    }

    /** Takes the banner down on each screen. */
    public List<ScreenActionResult> clearBanner(Actor actor, List<Long> screenIds) {
        List<ScreenActionResult> results = control.onEach(screens.screens(screenIds), screen -> {
            needing("banners", () -> client.clearBanner(control.target(screen)));
            monitor.poll(screen);
        });
        control.audit(actor, "SCREENS_BANNER_CLEARED", "Cleared the banner on ", results);
        return results;
    }

    /**
     * "Finals are up": pushes the playlist to the screens first, then puts the announcement on the screens that took
     * it, so each shows the announcement and then the new boards with no gap. A step's failures are reported apart from
     * the other's. With no playlist, only the announcement is sent.
     *
     * @param playlistId the finals playlist, or null to announce without pushing
     * @throws IllegalArgumentException (400) for an empty playlist or a message the screens would refuse; nothing is sent
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) for an unknown playlist or screen
     */
    public FinalsUpResult finalsUp(Actor actor, Long playlistId, List<Long> askedIds, ScreenMessage message) {
        ScreenRules.checkMessage(message);
        List<Long> screenIds = askedIds.stream().distinct().toList();
        List<ScreenActionResult> pushed = List.of();
        List<Long> toAnnounce = screenIds;
        if (playlistId != null) {
            pushed = control.push(actor, playlistId, screenIds);
            Set<Long> arrived = pushed.stream().filter(ScreenActionResult::ok).map(ScreenActionResult::id)
                    .collect(Collectors.toSet());
            toAnnounce = screenIds.stream().filter(arrived::contains).toList();
        }
        List<ScreenActionResult> announced = toAnnounce.isEmpty() ? List.of() : sendMessage(actor, toAnnounce, message);
        if (toAnnounce.size() < screenIds.size()) {
            announced = withSkipped(screenIds, announced, pushed);
        }
        return new FinalsUpResult(pushed, announced);
    }

    /** The announcement's results for every screen asked, in the order asked, with the ones that got no playlist marked. */
    private static List<ScreenActionResult> withSkipped(List<Long> screenIds, List<ScreenActionResult> announced,
                                                        List<ScreenActionResult> pushed) {
        List<ScreenActionResult> all = new ArrayList<>();
        for (Long id : screenIds) {
            announced.stream().filter(r -> r.id().equals(id)).findFirst().ifPresentOrElse(all::add, () ->
                    pushed.stream().filter(r -> r.id().equals(id)).findFirst().ifPresent(p -> all.add(
                            new ScreenActionResult(p.id(), p.screenId(), p.name(), false,
                                    "Not announced: the finals playlist did not reach this screen"))));
        }
        return all;
    }

    /** Runs a call that only agents 0.6.0 and later have; a 404 means the screen is older. */
    private static void needing(String what, Runnable call) {
        try {
            call.run();
        } catch (ViewerException e) {
            if (e.httpStatus() != null && e.httpStatus() == 404) {
                throw new ViewerException(404, "This screen cannot show " + what + " yet. Update it to 0.6.0", e);
            }
            throw e;
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }

    private static String describe(String style, String level, Integer duration) {
        String text = (style == null ? "" : ", " + style) + (level == null ? "" : ", " + level)
                + (duration == null ? ", until cleared" : ", " + duration + " s");
        return " (" + text.substring(2) + ")";
    }
}
