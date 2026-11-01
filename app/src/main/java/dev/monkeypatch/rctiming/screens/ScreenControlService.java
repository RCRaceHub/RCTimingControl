package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.screens.viewer.ViewerClient;
import dev.monkeypatch.rctiming.screens.viewer.ViewerException;
import dev.monkeypatch.rctiming.screens.viewer.ViewerInfo;
import dev.monkeypatch.rctiming.screens.viewer.ViewerPlaylist;
import dev.monkeypatch.rctiming.screens.viewer.ViewerStatus;
import dev.monkeypatch.rctiming.screens.viewer.ViewerTarget;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * What an official asks of the screens (#708): add one, push a playlist, press next or pause, put up a temporary page.
 * It calls the screens' agents through {@link ViewerClient}, deliberately outside any database transaction (a dead
 * screen costs seconds), then records the outcome through {@link ScreenService}. A command to several screens runs on
 * all of them at once on virtual threads, and a screen that refuses or cannot be reached is reported in its own {@link
 * ScreenActionResult} without stopping the rest. Runs on the request thread. Every command that goes out is audited.
 */
@Service
@EnableConfigurationProperties(ScreensProperties.class)
public class ScreenControlService {

    /** Most screens discovery will add by itself; more are added by hand. */
    static final int MAX_DISCOVERED = 100;

    private final ScreenService screens;
    private final ViewerClient client;
    private final ScreenMonitor monitor;
    private final ScreenPagePolicy pagePolicy;
    private final PendingScreens pending;
    private final boolean autoAdd;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public ScreenControlService(ScreenService screens, ViewerClient client, ScreenMonitor monitor,
                                ScreenPagePolicy pagePolicy, PendingScreens pending, ScreensProperties properties) {
        this.pagePolicy = pagePolicy;
        this.pending = pending;
        this.autoAdd = properties.autoAdd();
        this.screens = screens;
        this.client = client;
        this.monitor = monitor;
    }

    /** Every known screen with its live state and its playlist's name, by screen name. */
    public List<ScreenOverview> overview() {
        Map<Long, String> names = new HashMap<>();
        screens.playlists().forEach(p -> names.put(p.getId(), p.getName()));
        return screens.screens().stream()
                .map(s -> new ScreenOverview(s, monitor.stateOf(s), names.get(s.getPlaylistId())))
                .toList();
    }

    /** One screen with its live state and playlist name. */
    public ScreenOverview overviewOf(Screen screen) {
        ScreenPlaylist playlist = screen.getPlaylistId() == null ? null : screens.playlist(screen.getPlaylistId());
        return new ScreenOverview(screen, monitor.stateOf(screen), playlist == null ? null : playlist.getName());
    }

    /**
     * Adds a screen by hand from its host and port (for venues where mDNS is blocked), reading its info to learn its
     * id. Adding a screen that is already known updates its address and token.
     *
     * @param token the screen's bearer token, or null to leave it as it is
     * @throws IllegalArgumentException (400) for a bad host or port, or a screen that reports no id
     * @throws ViewerException if the screen cannot be reached or answers an error (502)
     */
    public Screen add(Actor actor, String host, int port, String token) {
        String cleanHost = ScreenRules.checkHost(host);
        int cleanPort = ScreenRules.checkPort(port);
        ViewerInfo info = client.info(new ViewerTarget(cleanHost, cleanPort, null));
        Screen saved = screens.upsert(actor, info, cleanHost, cleanPort, token);
        pending.forget(info.id());
        return saved;
    }

    /** Screens mDNS has found that nobody has added yet, for the Screens page's "Found, not added" list. */
    public List<PendingScreens.Found> pending() {
        return pending.list();
    }

    /**
     * Handles a screen found by mDNS at {@code host}:{@code port}, called by {@link ScreenDiscovery} on its own
     * thread. A screen already added is brought up to date (a new address after a DHCP change). One not added yet is
     * only remembered in {@link PendingScreens} for an official to add, unless {@code rctiming.screens.auto-add} is on.
     * A screen that does not answer {@code /info} is skipped by the caller.
     */
    public void discovered(String host, int port) {
        // Already known at this address and answering: nothing to learn, and no call to make
        if (screens.screens().stream().anyMatch(s -> s.getHost().equals(host) && s.getPort() == port
                && monitor.stateOf(s).connection() == ScreenState.Connection.ONLINE)) {
            return;
        }
        ViewerInfo info = client.info(new ViewerTarget(host, port, null));
        // Anyone on the network can advertise a screen. A known screen that still answers at its saved address is not
        // moved (its saved token would go to the new host), and the number of screens found this way is capped
        Screen known = screens.findByScreenId(info.id()).orElse(null);
        if (known != null && monitor.stateOf(known).connection() == ScreenState.Connection.ONLINE) {
            return;
        }
        if (known == null && !autoAdd) {
            pending.put(info, host, port);
            return;
        }
        if (known == null && screens.screens().size() >= MAX_DISCOVERED) {
            return;
        }
        screens.upsert(Actor.system("screen-discovery"), info, host, port, null);
    }

    /**
     * Replaces the playlist on each screen with a saved one, tagged {@code rctc:playlist-<id>} so a later change made
     * elsewhere shows as drift, and remembers the revision each screen gave back.
     *
     * @throws IllegalArgumentException (400) if the playlist has no pages
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) for an unknown playlist or screen
     */
    public List<ScreenActionResult> push(Actor actor, Long playlistId, List<Long> screenIds) {
        ScreenPlaylist playlist = screens.playlist(playlistId);
        List<PlaylistItem> items = screens.itemsOf(playlist);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("The playlist has no pages");
        }
        pagePolicy.checkItems(items);
        ViewerPlaylist.Body body = new ViewerPlaylist.Body(
                items.stream().map(i -> new ViewerPlaylist.Item(i.url(), i.duration(), i.label(), i.id())).toList(),
                playlist.getDefaultDuration(), 0, "rctc:playlist-" + playlist.getId());
        List<ScreenActionResult> results = onEach(screens.screens(screenIds), screen -> {
            ViewerPlaylist saved = client.putPlaylist(target(screen), body);
            screens.recordPush(screen.getId(), playlist.getId(), saved.revision(), saved.source());
            monitor.poll(screens.screen(screen.getId()));
        });
        audit(actor, "SCREENS_PUSHED", "Sent the playlist " + playlist.getName() + " to ", results);
        return results;
    }

    /**
     * Pushes a screen's assigned playlist to it again, to put right a screen that was changed elsewhere.
     *
     * @throws IllegalArgumentException (400) if no playlist is assigned
     */
    public List<ScreenActionResult> repush(Actor actor, Long screenId) {
        Screen screen = screens.screen(screenId);
        if (screen.getPlaylistId() == null) {
            throw new IllegalArgumentException(screen.getName() + " has no playlist assigned from here");
        }
        return push(actor, screen.getPlaylistId(), List.of(screenId));
    }

    /**
     * Sends a playback control to each screen.
     *
     * @param index the playlist position for {@link ControlAction#GOTO}, ignored otherwise
     * @throws IllegalArgumentException (400) for {@code GOTO} without an index
     */
    public List<ScreenActionResult> control(Actor actor, List<Long> screenIds, ControlAction action, Integer index) {
        if (action == ControlAction.GOTO && (index == null || index < 0)) {
            throw new IllegalArgumentException("Go to needs a page number, starting at 1");
        }
        List<ScreenActionResult> results = onEach(screens.screens(screenIds), screen -> {
            ViewerTarget target = target(screen);
            ViewerStatus status = action == ControlAction.GOTO ? client.goTo(target, index)
                    : client.control(target, action.path());
            monitor.record(screen, status);
        });
        audit(actor, "SCREENS_CONTROLLED", "Sent " + action.path() + " to ", results);
        return results;
    }

    /**
     * Puts a temporary page on each screen for {@code duration} seconds, or until cleared when null; the screens then
     * return to their playlists.
     *
     * @throws IllegalArgumentException (400) for a URL that is not a full http(s) address, or a bad time
     */
    public List<ScreenActionResult> show(Actor actor, List<Long> screenIds, String url, Integer duration, String label) {
        String cleanUrl = ScreenRules.checkWebUrl(url, "The page");
        pagePolicy.check(cleanUrl, "The page");
        Integer cleanDuration = ScreenRules.checkShowDuration(duration);
        List<ScreenActionResult> results = onEach(screens.screens(screenIds), screen ->
                monitor.record(screen, client.show(target(screen), cleanUrl, cleanDuration, label)));
        audit(actor, "SCREENS_SHOWN", "Showed " + cleanUrl + " on ", results);
        return results;
    }

    /** Ends the temporary page on each screen. */
    public List<ScreenActionResult> clearShow(Actor actor, List<Long> screenIds) {
        List<ScreenActionResult> results = onEach(screens.screens(screenIds), screen ->
                monitor.record(screen, client.clearShow(target(screen))));
        audit(actor, "SCREENS_CONTROLLED", "Cleared the temporary page on ", results);
        return results;
    }

    private interface ScreenCall {
        void run(Screen screen);
    }

    /** Runs the call on every screen at once and collects each screen's outcome, in the order given. */
    private List<ScreenActionResult> onEach(List<Screen> targets, ScreenCall call) {
        Function<Screen, ScreenActionResult> one = screen -> {
            try {
                call.run(screen);
                return ScreenActionResult.ok(screen);
            } catch (ViewerException e) {
                return ScreenActionResult.failed(screen, e.httpStatus() != null && e.httpStatus() == 401
                        ? "The screen needs a token. Set it on the Screens page" : e.getMessage());
            }
        };
        List<Future<ScreenActionResult>> futures = new ArrayList<>();
        for (Screen screen : targets) {
            futures.add(workers.submit(() -> one.apply(screen)));
        }
        List<ScreenActionResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).get());
            } catch (ExecutionException e) {
                results.add(ScreenActionResult.failed(targets.get(i), "Could not send the command"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.add(ScreenActionResult.failed(targets.get(i), "Interrupted"));
            }
        }
        return results;
    }

    private ViewerTarget target(Screen screen) {
        return new ViewerTarget(screen.getHost(), screen.getPort(), screens.tokenOf(screen).orElse(null));
    }

    private void audit(Actor actor, String action, String what, List<ScreenActionResult> results) {
        String names = results.stream().map(r -> r.ok() ? r.name() : r.name() + " (failed)")
                .reduce((a, b) -> a + ", " + b).orElse("no screens");
        screens.auditAction(actor, action, what + names);
    }

    @PreDestroy
    void stop() {
        workers.shutdownNow();
    }
}
