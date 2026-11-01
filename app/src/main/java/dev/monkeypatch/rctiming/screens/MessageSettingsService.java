package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads and saves the message presets and event hooks (#716). The settings are one JSON document, checked on the way
 * in so nothing the screens would refuse can be saved, and replaced whole on each save. A saved document that cannot
 * be read (an older shape, a hand edit) is logged and the defaults are used, so the Screens page always opens. Called
 * from request threads and from {@link ScreenMessageHooks}; saving runs in one write transaction with its audit row.
 */
@Service
public class MessageSettingsService {

    private static final Logger log = LoggerFactory.getLogger(MessageSettingsService.class);
    static final int MAX_PRESETS = 30;
    private static final int MAX_NAME = 60;

    private final MessageSettingsRepository repository;
    private final ScreenService screens;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public MessageSettingsService(MessageSettingsRepository repository, ScreenService screens, AuditService audit,
                                  ObjectMapper json) {
        this(repository, screens, audit, json, Clock.systemUTC());
    }

    MessageSettingsService(MessageSettingsRepository repository, ScreenService screens, AuditService audit,
                           ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.screens = screens;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    /** The saved settings, or the defaults when none are saved or the saved ones cannot be read. */
    @ReadTransaction
    public MessageSettings get() {
        return repository.find().map(this::parse).orElseGet(MessageSettings::defaults);
    }

    private MessageSettings parse(String text) {
        try {
            MessageSettings settings = json.readValue(text, MessageSettings.class);
            if (settings.presets() == null || settings.hooks() == null) {
                throw new IllegalStateException("incomplete");
            }
            return settings;
        } catch (JacksonException | IllegalStateException e) {
            log.warn("The saved screen message settings cannot be read; using the defaults: {}", e.getMessage());
            return MessageSettings.defaults();
        }
    }

    /**
     * Replaces the settings with the cleaned version of {@code settings} and returns what was saved. Audited.
     *
     * @throws IllegalArgumentException (400) for a bad preset, a duplicate name, too many presets, or a hook message
     *                                  the screens would refuse
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if the finals playlist does not exist
     */
    @Transactional
    public MessageSettings save(Actor actor, MessageSettings settings) {
        if (settings == null || settings.presets() == null || settings.hooks() == null) {
            throw new IllegalArgumentException("The settings need presets and hooks");
        }
        if (settings.presets().size() > MAX_PRESETS) {
            throw new IllegalArgumentException("There can be at most " + MAX_PRESETS + " saved messages");
        }
        List<MessageSettings.Preset> presets = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (MessageSettings.Preset preset : settings.presets()) {
            String name = preset == null || preset.name() == null ? "" : preset.name().strip();
            if (name.isEmpty() || name.length() > MAX_NAME) {
                throw new IllegalArgumentException("Each saved message needs a name of up to " + MAX_NAME + " characters");
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Two saved messages are called " + name);
            }
            presets.add(new MessageSettings.Preset(name, clean(preset.message())));
        }
        MessageSettings.Hooks hooks = settings.hooks();
        if (hooks.finalsPlaylistId() != null) {
            screens.playlist(hooks.finalsPlaylistId());
        }
        MessageSettings cleaned = new MessageSettings(presets, new MessageSettings.Hooks(hooks.holdAfterQualifying(),
                clean(hooks.holdMessage()), hooks.announceFinals(), hooks.finalsPlaylistId(), clean(hooks.finalsMessage())));
        repository.save(json.writeValueAsString(cleaned), clock.instant());
        audit.entry(actor, "SCREEN_MESSAGES_SAVED").summary("Saved the screen messages: " + presets.size()
                + " preset(s), qualifying hold " + onOff(cleaned.hooks().holdAfterQualifying()) + ", finals announcement "
                + onOff(cleaned.hooks().announceFinals())).record();
        return cleaned;
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }

    /** The message as the screens will get it, so the saved copy is always one they accept. */
    private static ScreenMessage clean(ScreenMessage message) {
        var body = ScreenRules.checkMessage(message);
        return new ScreenMessage(body.title(), body.text(), body.style() == null ? "hold" : body.style(),
                body.level() == null ? "info" : body.level(), body.duration());
    }
}
