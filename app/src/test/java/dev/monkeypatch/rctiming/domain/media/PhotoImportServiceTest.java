package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.NameVisibility;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.media.PhotoImportService.Outcome;
import dev.monkeypatch.rctiming.domain.media.PhotoImportService.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Which pictures an entry import's links may fetch (#699), and that one failure never stops the rest. */
class PhotoImportServiceTest {

    private static final Actor ACTOR = Actor.system("test");
    private static final byte[] PICTURE = {1, 2, 3};

    private final EntryRepository entries = mock(EntryRepository.class);
    private final CompetitorRepository competitors = mock(CompetitorRepository.class);
    private final DriverMediaRepository media = mock(DriverMediaRepository.class);
    private final EntryPhotoRepository photos = mock(EntryPhotoRepository.class);
    private final DriverMediaService mediaService = mock(DriverMediaService.class);
    private final PhotoFetcher fetcher = mock(PhotoFetcher.class);
    private PhotoImportService service;

    @BeforeEach
    void setUp() throws Exception {
        Entry entry = new Entry();
        entry.setId(9L);
        entry.setCompetitorId(7L);
        when(entries.findByEventId(5L)).thenReturn(List.of(entry));
        when(competitors.findNameVisibilityNotShow()).thenReturn(Map.of());
        when(media.findByCompetitorId(anyLong())).thenReturn(Optional.empty());
        when(photos.findByEntryId(anyLong())).thenReturn(Optional.empty());
        when(fetcher.fetch(any(), anyInt())).thenReturn(PICTURE);
        when(mediaService.importHeadshot(eq(ACTOR), eq(7L), any()))
                .thenReturn(new DriverMediaService.CompetitorMediaView("/files/photos/h.png", List.of()));
        when(mediaService.importCarPhoto(eq(ACTOR), eq(9L), any())).thenReturn("/files/photos/c.png");
        service = new PhotoImportService(mock(EventRepository.class), entries, competitors, media, photos,
                mediaService, fetcher);
    }

    private static PhotoLink headshot(long competitorId, String url) {
        return new PhotoLink(PhotoLink.Kind.HEADSHOT, competitorId, null, url);
    }

    private static PhotoLink car(long entryId, String url) {
        return new PhotoLink(PhotoLink.Kind.CAR_PHOTO, null, entryId, url);
    }

    @Test
    void storesBothKindsAndAnswersWithTheirAddresses() {
        List<Outcome> out = service.fetchAll(ACTOR, 5L,
                List.of(headshot(7L, "https://cdn.example.org/a.png"), car(9L, "https://cdn.example.org/c.jpg")), false);

        assertThat(out).extracting(Outcome::status).containsExactly(Status.STORED, Status.STORED);
        assertThat(out).extracting(Outcome::storedUrl).containsExactly("/files/photos/h.png", "/files/photos/c.png");
    }

    @Test
    void skipsADriverWhoseNameIsNotShownInFull() throws Exception {
        when(competitors.findNameVisibilityNotShow()).thenReturn(Map.of(7L, NameVisibility.INITIALS));

        List<Outcome> out = service.fetchAll(ACTOR, 5L,
                List.of(headshot(7L, "https://cdn.example.org/a.png"), car(9L, "https://cdn.example.org/c.jpg")), false);

        assertThat(out).extracting(Outcome::status).containsExactly(Status.SKIPPED, Status.SKIPPED);
        verify(fetcher, never()).fetch(any(), anyInt());
    }

    @Test
    void keepsAPictureAlreadyHeldUnlessAskedToReplaceIt() throws Exception {
        DriverMedia held = new DriverMedia();
        held.setHeadshotUrl("/files/photos/old.png");
        when(media.findByCompetitorId(7L)).thenReturn(Optional.of(held));
        List<PhotoLink> links = List.of(headshot(7L, "https://cdn.example.org/a.png"));

        assertThat(service.fetchAll(ACTOR, 5L, links, false)).extracting(Outcome::status).containsExactly(Status.SKIPPED);
        verify(fetcher, never()).fetch(any(), anyInt());
        assertThat(service.fetchAll(ACTOR, 5L, links, true)).extracting(Outcome::status).containsExactly(Status.STORED);
    }

    @Test
    void refusesALinkForSomeoneNotInTheEventAndAnAddressThatIsNotHttps() throws Exception {
        List<Outcome> out = service.fetchAll(ACTOR, 5L,
                List.of(headshot(99L, "https://cdn.example.org/a.png"), car(99L, "https://cdn.example.org/c.png"),
                        headshot(7L, "http://cdn.example.org/a.png")), false);

        assertThat(out).extracting(Outcome::status).containsExactly(Status.FAILED, Status.FAILED, Status.FAILED);
        verify(fetcher, never()).fetch(any(), anyInt());
    }

    @Test
    void oneFailedFetchDoesNotStopTheNext() throws Exception {
        when(fetcher.fetch(eq("https://cdn.example.org/bad.png"), anyInt())).thenThrow(new PhotoFetcher.FetchException("The server answered 404"));

        List<Outcome> out = service.fetchAll(ACTOR, 5L,
                List.of(headshot(7L, "https://cdn.example.org/bad.png"), car(9L, "https://cdn.example.org/c.jpg")), false);

        assertThat(out).extracting(Outcome::status).containsExactly(Status.FAILED, Status.STORED);
        assertThat(out.get(0).message()).isEqualTo("The server answered 404");
    }

    @Test
    void aPictureThatIsNotAnImageIsReportedNotThrown() {
        when(mediaService.importHeadshot(eq(ACTOR), eq(7L), any()))
                .thenThrow(new IllegalArgumentException("The picture is not a PNG or JPEG image"));

        List<Outcome> out = service.fetchAll(ACTOR, 5L, List.of(headshot(7L, "https://cdn.example.org/a.svg")), false);

        assertThat(out).singleElement().satisfies(o -> {
            assertThat(o.status()).isEqualTo(Status.FAILED);
            assertThat(o.message()).contains("PNG or JPEG");
        });
    }

    @Test
    void refusesMoreLinksThanOneCallTakes() {
        List<PhotoLink> many = Collections.nCopies(PhotoImportService.MAX_LINKS + 1,
                headshot(7L, "https://cdn.example.org/a.png"));

        assertThatThrownBy(() -> service.fetchAll(ACTOR, 5L, many, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
