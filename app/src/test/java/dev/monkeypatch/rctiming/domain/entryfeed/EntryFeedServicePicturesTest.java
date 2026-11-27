package dev.monkeypatch.rctiming.domain.entryfeed;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventStatus;
import dev.monkeypatch.rctiming.domain.media.PhotoImportService;
import dev.monkeypatch.rctiming.domain.media.PhotoLink;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubEntryExport;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportResult;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportService;
import dev.monkeypatch.rctiming.security.TokenCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The automatic entry feed fetches the pictures an applied revision names, and only then (#811). */
class EntryFeedServicePicturesTest {

    private static final long EVENT_ID = 5L;
    private static final String BODY = "{\"schema_version\":1,\"event\":{\"id\":\"9\"},\"revision\":3,\"entries\":[]}";
    private static final List<PhotoLink> LINKS =
            List.of(new PhotoLink(PhotoLink.Kind.HEADSHOT, 7L, null, "https://media.example.org/h.png"));

    private final EntryFeedRepository feeds = mock(EntryFeedRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final EntryFeedClient client = mock(EntryFeedClient.class);
    private final RaceHubImportService importService = mock(RaceHubImportService.class);
    private final PhotoImportService photoImport = mock(PhotoImportService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private EntryFeedService service;
    private EntryFeed feed;

    @BeforeEach
    void setUp() {
        feed = new EntryFeed();
        feed.setEventId(EVENT_ID);
        feed.setUrl("https://racehub.example/api/rctc/v1/events/9/entries");
        feed.setAutoFetch(true);
        Event event = new Event();
        event.setStatus(EventStatus.IN_PROGRESS);
        when(events.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(feeds.findByEventId(EVENT_ID)).thenReturn(Optional.of(feed));
        when(feeds.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactions.execute(any())).thenAnswer(i ->
                ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
        when(client.fetch(any(), any(), any())).thenReturn(new EntryFeedClient.Fetched(BODY));
        service = new EntryFeedService(feeds, events, client, importService, mock(TokenCipher.class),
                JsonMapper.builder().build(), transactions, mock(AuditService.class),
                mock(dev.monkeypatch.rctiming.domain.race.RoundRepository.class), photoImport);
    }

    private RaceHubImportResult result(boolean applied, List<PhotoLink> links) {
        return new RaceHubImportResult(false, !applied, applied, "Meeting", 3L, null, List.of(), List.of(),
                List.of(), List.of(), links, null);
    }

    @Test
    void anAppliedRevisionFetchesItsPicturesAndNeverReplacesOnes() {
        when(importService.importEntries(any(), eq(EVENT_ID), any(RaceHubEntryExport.class), anyBoolean()))
                .thenReturn(result(true, LINKS));
        when(photoImport.fetchAll(any(), eq(EVENT_ID), any(), anyBoolean())).thenReturn(List.of(
                new PhotoImportService.Outcome(LINKS.get(0), PhotoImportService.Status.STORED, null, "/files/h.png")));

        service.autoFetch(feed);

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(photoImport).fetchAll(actor.capture(), eq(EVENT_ID), eq(LINKS), eq(false));
        assertThat(actor.getValue().toString()).contains("entry-feed");
        assertThat(feed.getLastError()).isNull();
    }

    @Test
    void aRevisionHeldForAnOfficialFetchesNoPictures() {
        when(importService.importEntries(any(), eq(EVENT_ID), any(RaceHubEntryExport.class), anyBoolean()))
                .thenReturn(result(false, LINKS));

        service.autoFetch(feed);

        verify(photoImport, never()).fetchAll(any(), anyLong(), any(), anyBoolean());
    }

    @Test
    void failedPicturesAreNotedOnTheFeedAndTheEntriesStayApplied() {
        when(importService.importEntries(any(), eq(EVENT_ID), any(RaceHubEntryExport.class), anyBoolean()))
                .thenReturn(result(true, LINKS));
        when(photoImport.fetchAll(any(), eq(EVENT_ID), any(), anyBoolean())).thenReturn(List.of(
                new PhotoImportService.Outcome(LINKS.get(0), PhotoImportService.Status.FAILED, "gone", null)));

        service.autoFetch(feed);

        assertThat(feed.getAppliedRevision()).isEqualTo(3L);
        assertThat(feed.getLastError()).contains("1 picture couldn't be fetched");
    }

    @Test
    void aPictureFaultNeverFailsTheFeed() {
        when(importService.importEntries(any(), eq(EVENT_ID), any(RaceHubEntryExport.class), anyBoolean()))
                .thenReturn(result(true, LINKS));
        when(photoImport.fetchAll(any(), eq(EVENT_ID), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("disk"));

        service.autoFetch(feed);

        assertThat(feed.getAppliedRevision()).isEqualTo(3L);
        assertThat(feed.getLastStatus()).isEqualTo(EntryFeedStatus.APPLIED);
        assertThat(feed.getLastError()).contains("1 picture couldn't be fetched");
    }

    @Test
    void picturesSkippedOnPurposeAreNotAFailure() {
        when(importService.importEntries(any(), eq(EVENT_ID), any(RaceHubEntryExport.class), anyBoolean()))
                .thenReturn(result(true, LINKS));
        when(photoImport.fetchAll(any(), eq(EVENT_ID), any(), anyBoolean())).thenReturn(List.of(
                new PhotoImportService.Outcome(LINKS.get(0), PhotoImportService.Status.SKIPPED, "held", null)));

        service.autoFetch(feed);

        assertThat(feed.getLastError()).isNull();
    }

    @Test
    void noLinksMeansNoPictureCall() {
        when(importService.importEntries(any(), eq(EVENT_ID), any(RaceHubEntryExport.class), anyBoolean()))
                .thenReturn(result(true, List.of()));

        service.autoFetch(feed);

        verify(photoImport, never()).fetchAll(any(), anyLong(), any(), anyBoolean());
    }

    @Test
    void theFailureNoteSurvivesTheNextUnchangedPollAndCountsSeveral() {
        List<PhotoLink> two = List.of(LINKS.get(0),
                new PhotoLink(PhotoLink.Kind.CAR_PHOTO, null, 9L, "https://media.example.org/c.png"));
        when(importService.importEntries(any(), eq(EVENT_ID), any(RaceHubEntryExport.class), anyBoolean()))
                .thenReturn(result(true, two));
        when(photoImport.fetchAll(any(), eq(EVENT_ID), any(), anyBoolean())).thenReturn(List.of(
                new PhotoImportService.Outcome(two.get(0), PhotoImportService.Status.FAILED, "gone", null),
                new PhotoImportService.Outcome(two.get(1), PhotoImportService.Status.FAILED, "gone", null)));

        service.autoFetch(feed);
        service.autoFetch(feed);

        assertThat(feed.getLastError()).contains("2 pictures couldn't be fetched");
    }
}
