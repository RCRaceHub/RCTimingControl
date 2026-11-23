package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rotating the club's RaceHub key from the admin panel without a restart. Saving or clearing the key changes what
 * {@link RaceHubResultsEndpoint#token()} answers, so the results sender, the events and classes lookups and
 * the entry feeds' next fetches use it at once. Entry feeds keep their own encrypted copy of the key, so the
 * ones pointing at RaceHub's host are given the new key too.
 *
 * <p>Called from {@code ResultsExportController} on a request thread, ADMIN only. Every change is audited with the
 * source and the number of feeds changed, never the key.
 */
@Service
public class RaceHubKeyService {

    private static final int MAX_LENGTH = 500;

    private final RaceHubKeyStore store;
    private final RaceHubResultsEndpoint endpoint;
    private final EntryFeedService feeds;
    private final AuditService audit;

    public RaceHubKeyService(RaceHubKeyStore store, RaceHubResultsEndpoint endpoint, EntryFeedService feeds,
                             AuditService audit) {
        this.store = store;
        this.endpoint = endpoint;
        this.feeds = feeds;
        this.audit = audit;
    }

    /**
     * Saves a new key, and gives it to the entry feeds that point at RaceHub.
     *
     * @throws IllegalArgumentException (a 400) if the key is blank, too long or has spaces or control characters
     */
    public void save(Actor actor, String key) {
        String cleaned = key == null ? "" : key.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_LENGTH || cleaned.chars().anyMatch(c -> c <= ' ' || c == 127)) {
            throw new IllegalArgumentException("Paste the key exactly as RaceHub shows it, with no spaces");
        }
        store.save(cleaned);
        record(actor, "RACEHUB_KEY_CHANGED", "Saved a new RaceHub key", "SAVED", feeds.replaceTokenWhere(endpoint::isRaceHub, cleaned));
    }

    /** Forgets the saved key; the settings file's key, if there is one, applies again, including on the feeds. */
    public void clear(Actor actor) {
        store.clear();
        record(actor, "RACEHUB_KEY_CLEARED", "Removed the saved RaceHub key", endpoint.settingsFileKeySet() ? "SETTINGS_FILE" : "NONE",
                feeds.replaceTokenWhere(endpoint::isRaceHub, endpoint.token()));
    }

    private void record(Actor actor, String action, String summary, String source, int feedsChanged) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("keySource", source);
        after.put("feedsChanged", feedsChanged);
        audit.entry(actor, action).summary(summary).after(after).record();
    }
}
