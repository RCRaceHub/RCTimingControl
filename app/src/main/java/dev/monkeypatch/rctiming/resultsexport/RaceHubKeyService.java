package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *
 * <p>Each change runs in one transaction on the write connection: the feeds and the audit row first, the key file
 * last. The file cannot be rolled back, so writing it last means a failure before it, or in writing it, leaves the
 * old key in place everywhere. A commit that fails after the file was written still splits the two: the key
 * changes while the feeds and the audit log do not, and saving or clearing again puts that right.
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
    @Transactional
    public void save(Actor actor, String key) {
        String cleaned = key == null ? "" : key.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_LENGTH || cleaned.chars().anyMatch(c -> c <= ' ' || c == 127)) {
            throw new IllegalArgumentException("Paste the key exactly as RaceHub shows it, with no spaces");
        }
        int changed = feeds.replaceTokenWhere(endpoint::isRaceHub, cleaned);
        record(actor, "RACEHUB_KEY_CHANGED", "Saved a new RaceHub key", "SAVED", changed);
        store.save(cleaned);
    }

    /** Forgets the saved key; the settings file's key, if there is one, applies again, including on the feeds. */
    @Transactional
    public void clear(Actor actor) {
        int changed = feeds.replaceTokenWhere(endpoint::isRaceHub, endpoint.settingsFileToken());
        record(actor, "RACEHUB_KEY_CLEARED", "Removed the saved RaceHub key",
                endpoint.settingsFileKeySet() ? "SETTINGS_FILE" : "NONE", changed);
        store.clear();
    }

    private void record(Actor actor, String action, String summary, String source, int feedsChanged) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("keySource", source);
        after.put("feedsChanged", feedsChanged);
        audit.entry(actor, action).summary(summary).after(after).record();
    }
}
