package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.screens.viewer.ViewerInfo;
import dev.monkeypatch.rctiming.security.TokenCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The database side of the screen manager (#708): the known screens, their tokens and the saved playlists. It never
 * calls a screen; {@link ScreenControlService} does that, outside any transaction, and comes here to record the
 * outcome, so the single write connection is never held while the network is slow. Called from request threads and
 * from {@link ScreenDiscovery}'s thread; every change runs in one write transaction with its audit row.
 */
@Service
@Transactional
public class ScreenService {

    private static final Logger log = LoggerFactory.getLogger(ScreenService.class);
    private static final TypeReference<List<PlaylistItem>> ITEMS = new TypeReference<>() { };

    private final ScreenRepository screens;
    private final ScreenPlaylistRepository playlists;
    private final AuditService audit;
    private final ObjectMapper json;
    private final TokenCipher cipher;
    private final Clock clock;

    @Autowired
    public ScreenService(ScreenRepository screens, ScreenPlaylistRepository playlists, AuditService audit,
                         ObjectMapper json, TokenCipher cipher) {
        this(screens, playlists, audit, json, cipher, Clock.systemUTC());
    }

    ScreenService(ScreenRepository screens, ScreenPlaylistRepository playlists, AuditService audit,
                  ObjectMapper json, TokenCipher cipher, Clock clock) {
        this.clock = clock;
        this.screens = screens;
        this.playlists = playlists;
        this.audit = audit;
        this.json = json;
        this.cipher = cipher;
    }

    // ---- screens

    /** Every known screen, by name. */
    @ReadTransaction
    public List<Screen> screens() {
        return screens.findAllByName();
    }

    /** One screen. @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if there is none */
    @ReadTransaction
    public Screen screen(Long id) {
        return screens.getOrThrow(id);
    }

    /** The screens with these ids, in the order given. @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if one is missing */
    @ReadTransaction
    public List<Screen> screens(List<Long> ids) {
        List<Screen> found = new ArrayList<>();
        for (Long id : ids.stream().distinct().toList()) {
            found.add(screens.getOrThrow(id));
        }
        return found;
    }

    /**
     * Adds a screen it has just read the info of, or brings a known one up to date (new address after a DHCP change, a
     * new name or agent version). Matching is by the screen's own id, never its address. A row is written only when
     * something changed, because discovery calls this every half minute for every screen. {@code token} replaces the
     * saved token when it is not null; a blank one clears it. Adding is audited ({@code SCREEN_ADDED}); a screen found
     * by discovery is added under the system actor.
     *
     * @throws IllegalArgumentException (400) if the screen reports no id
     */
    public Screen upsert(Actor actor, ViewerInfo info, String host, int port, String token) {
        if (info.id() == null || info.id().isBlank()) {
            throw new IllegalArgumentException("The screen did not report an id, so it cannot be told apart from others");
        }
        if (info.id().length() > 100) {
            throw new IllegalArgumentException("The screen's id is longer than 100 characters");
        }
        String name = info.name() != null && !info.name().isBlank() ? info.name() : info.id();
        Screen screen = screens.findByScreenId(info.id()).orElse(null);
        boolean added = screen == null;
        if (added) {
            screen = new Screen();
            screen.setScreenId(clip(info.id(), 100));
        }
        boolean changed = added;
        changed |= !Objects.equals(screen.getName(), clip(name, 255));
        changed |= !Objects.equals(screen.getHost(), host) || screen.getPort() != port;
        changed |= !Objects.equals(screen.getAgentVersion(), clip(info.version(), 50));
        changed |= !Objects.equals(screen.getApiVersion(), clip(info.apiVersion(), 20));
        screen.setName(clip(name, 255));
        screen.setHost(host);
        screen.setPort(port);
        screen.setAgentVersion(clip(info.version(), 50));
        screen.setApiVersion(clip(info.apiVersion(), 20));
        if (token != null) {
            screen.setApiToken(token.isBlank() ? null : cipher.encrypt(token.strip()));
            changed = true;
        }
        if (!changed) {
            return screen;
        }
        Screen saved = screens.save(screen);
        if (added) {
            audit.entry(actor, "SCREEN_ADDED").entity("display_screen", saved.getId())
                    .summary("Added the screen " + saved.getName() + " at " + host + ":" + port).record();
        }
        return saved;
    }

    /** Sets one token on every screen listed; a blank token clears it. Audited ({@code SCREEN_TOKEN_CHANGED}), without the token. */
    public List<Screen> setToken(Actor actor, List<Long> ids, String token) {
        List<Screen> changed = new ArrayList<>();
        for (Screen screen : screens(ids)) {
            screen.setApiToken(token == null || token.isBlank() ? null : cipher.encrypt(token.strip()));
            changed.add(screens.save(screen));
        }
        audit.entry(actor, "SCREEN_TOKEN_CHANGED")
                .summary((token == null || token.isBlank() ? "Cleared the token of " : "Set a token on ")
                        + names(changed)).record();
        return changed;
    }

    /** Forgets a screen. It keeps its own playlist; adding it again, or discovery, brings it back. Audited. */
    public void remove(Actor actor, Long id) {
        Screen screen = screens.getOrThrow(id);
        screens.deleteById(id);
        audit.entry(actor, "SCREEN_REMOVED").entity("display_screen", id)
                .summary("Removed the screen " + screen.getName()).record();
    }

    /** What the manager has just set on a screen, kept to spot later changes made elsewhere. */
    public void recordPush(Long screenId, Long playlistId, Long revision, String source) {
        Screen screen = screens.getOrThrow(screenId);
        screen.setPlaylistId(playlistId);
        screen.setPushedRevision(revision);
        screen.setPushedSource(source == null || source.isBlank() ? null : source);
        screen.setPushedAt(clock.instant());
        screens.save(screen);
    }

    /** The screen that reports this id, if it is known. */
    @ReadTransaction
    public Optional<Screen> findByScreenId(String screenId) {
        return screens.findByScreenId(screenId);
    }

    /** The screen's saved token in clear, or empty if there is none or it cannot be read on this computer. */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<String> tokenOf(Screen screen) {
        if (screen.getApiToken() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(cipher.decrypt(screen.getApiToken()));
        } catch (TokenCipher.UnreadableTokenException e) {
            log.warn("The token of screen {} cannot be read; it has to be entered again", screen.getScreenId());
            return Optional.empty();
        }
    }

    /** Records one routine action (a push, a control, a temporary page) against the screens it was sent to. */
    public void auditAction(Actor actor, String action, String summary) {
        audit.entry(actor, action).summary(summary).record();
    }

    // ---- playlists

    /** Every playlist, by name. */
    @ReadTransaction
    public List<ScreenPlaylist> playlists() {
        return playlists.findAllByName();
    }

    /** One playlist. @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if there is none */
    @ReadTransaction
    public ScreenPlaylist playlist(Long id) {
        return playlists.getOrThrow(id);
    }

    /** A playlist's pages, read from its stored JSON; touches no table, so it opens no transaction. */
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PlaylistItem> itemsOf(ScreenPlaylist playlist) {
        try {
            return json.readValue(playlist.getItems(), ITEMS);
        } catch (JacksonException e) {
            throw new IllegalStateException("The pages of playlist " + playlist.getName() + " cannot be read", e);
        }
    }

    /**
     * Adds a playlist and records {@code SCREEN_PLAYLIST_CREATED}.
     *
     * @throws IllegalArgumentException (400) for a bad name, time or page
     * @throws StateConflictException (409) if the name is taken
     */
    public ScreenPlaylist createPlaylist(Actor actor, String name, int defaultDuration, List<PlaylistItem> items) {
        ScreenPlaylist playlist = new ScreenPlaylist();
        fill(playlist, name, defaultDuration, items);
        ScreenPlaylist saved = playlists.save(playlist);
        audit.entry(actor, "SCREEN_PLAYLIST_CREATED").entity("display_playlist", saved.getId())
                .summary("Added the screen playlist " + saved.getName()).after(values(saved)).record();
        return saved;
    }

    /** Changes a playlist (screens keep the old one until it is pushed again) and records {@code SCREEN_PLAYLIST_UPDATED}. */
    public ScreenPlaylist updatePlaylist(Actor actor, Long id, String name, int defaultDuration, List<PlaylistItem> items) {
        ScreenPlaylist playlist = playlists.getOrThrow(id);
        Map<String, Object> before = values(playlist);
        fill(playlist, name, defaultDuration, items);
        ScreenPlaylist saved = playlists.save(playlist);
        audit.entry(actor, "SCREEN_PLAYLIST_UPDATED").entity("display_playlist", id)
                .summary("Changed the screen playlist " + saved.getName()).before(before).after(values(saved)).record();
        return saved;
    }

    /** Removes a playlist and takes it off the screens it was assigned to; they keep showing it until changed. */
    public void deletePlaylist(Actor actor, Long id) {
        ScreenPlaylist playlist = playlists.getOrThrow(id);
        Map<String, Object> before = values(playlist);
        screens.clearPlaylist(id);
        playlists.deleteById(id);
        audit.entry(actor, "SCREEN_PLAYLIST_DELETED").entity("display_playlist", id)
                .summary("Removed the screen playlist " + playlist.getName()).before(before).record();
    }

    private void fill(ScreenPlaylist playlist, String name, int defaultDuration, List<PlaylistItem> items) {
        String cleanName = ScreenRules.cleanName(name);
        playlists.findByName(cleanName).filter(other -> !other.getId().equals(playlist.getId())).ifPresent(other -> {
            throw new StateConflictException("There is already a screen playlist called " + cleanName);
        });
        List<PlaylistItem> checked = ScreenRules.checkItems(items);
        playlist.setName(cleanName);
        playlist.setDefaultDuration(ScreenRules.checkDefaultDuration(defaultDuration));
        try {
            playlist.setItems(json.writeValueAsString(checked));
        } catch (JacksonException e) {
            throw new IllegalStateException("The pages cannot be written", e);
        }
    }

    private static Map<String, Object> values(ScreenPlaylist p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("defaultDuration", p.getDefaultDuration());
        m.put("items", p.getItems());
        return m;
    }

    private static String names(List<Screen> list) {
        return list.stream().map(Screen::getName).reduce((a, b) -> a + ", " + b).orElse("no screens");
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
