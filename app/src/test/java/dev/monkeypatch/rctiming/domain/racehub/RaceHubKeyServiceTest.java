package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedService;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Which key the entry feeds get when the saved one is cleared and the settings file has one of its own. */
class RaceHubKeyServiceTest {

    private final RaceHubKeyStore store = mock(RaceHubKeyStore.class);
    private final RaceHubResultsEndpoint endpoint = mock(RaceHubResultsEndpoint.class);
    private final EntryFeedService feeds = mock(EntryFeedService.class);
    private final AuditService audit = mock(AuditService.class, Answers.RETURNS_DEEP_STUBS);
    private final RaceHubKeyService service = new RaceHubKeyService(store, endpoint, feeds, audit);

    @Test
    void clearingGivesTheFeedsTheSettingsFileKeyBeforeRemovingTheSavedOne() {
        when(endpoint.settingsFileKeySet()).thenReturn(true);
        when(endpoint.settingsFileToken()).thenReturn("settings-file-key");

        service.clear(mock(Actor.class));

        var order = inOrder(feeds, store);
        order.verify(feeds).replaceTokenWhere(any(), eq("settings-file-key"));
        order.verify(store).clear();
    }
}
