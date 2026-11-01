package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedClient;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsEndpoint;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The "RaceHub classes" screen's source: RaceHub's class list, beside the links the club has made. */
class RaceHubClassesServiceTest {

    private static final String EVENTS_URL = "https://racehub.example/api/rctc/v1/events?state=ready";
    private static final URI CLASSES_URL = URI.create("https://racehub.example/api/rctc/v1/classes");

    private final ClubProfileService clubProfileService = mock(ClubProfileService.class);
    private final EntryFeedClient client = mock(EntryFeedClient.class);
    private final RaceHubClassLinkRepository linkRepository = mock(RaceHubClassLinkRepository.class);
    private final RacingClassRepository racingClassRepository = mock(RacingClassRepository.class);

    private RaceHubClassesService service(String token) {
        RaceHubResultsEndpoint endpoint = new RaceHubResultsEndpoint(
                new RaceHubResultsProperties(null, token), clubProfileService);
        return new RaceHubClassesService(clubProfileService, endpoint, client, linkRepository,
                racingClassRepository, JsonMapper.builder().build());
    }

    @BeforeEach
    void defaults() {
        when(clubProfileService.getRaceHubEventsUrl()).thenReturn(Optional.of(EVENTS_URL));
        when(linkRepository.findAllOrderByClassKey()).thenReturn(List.of());
        when(racingClassRepository.findAll()).thenReturn(List.of());
    }

    private static RacingClass racingClass(long id, String name) {
        RacingClass c = new RacingClass();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static RaceHubClassLink link(String key, long racingClassId) {
        RaceHubClassLink l = new RaceHubClassLink();
        l.setClassKey(key);
        l.setRacingClassId(racingClassId);
        return l;
    }

    @Test
    void saysWhichSettingIsMissingWithoutAskingRaceHub() {
        when(clubProfileService.getRaceHubEventsUrl()).thenReturn(Optional.empty());

        RaceHubClassesService.Listing listing = service(null).list();

        assertThat(listing.eventsUrl()).isNull();
        assertThat(listing.tokenSet()).isFalse();
        assertThat(listing.error()).isNotBlank();
        verifyNoInteractions(client);
    }

    @Test
    void asksForTheClassListBesideTheEventsAddressWithTheClubKey() {
        when(client.fetch(CLASSES_URL, "key")).thenReturn(new EntryFeedClient.Fetched("[]"));

        assertThat(service("key").list()).isEqualTo(new RaceHubClassesService.Listing(List.of(), null, EVENTS_URL, true));
        verify(client).fetch(CLASSES_URL, "key");
    }

    @Test
    void readsABareArrayOrAnObjectHoldingIt_andKeepsInactiveClasses() {
        String classes = """
                [{"id": 12, "name": "Zed", "rc_class_name": "Zed", "active": true},
                 {"id": "7", "name": "2WD Buggy", "rc_class_name": "2WD Buggy", "active": false}]
                """;
        when(client.fetch(any(), eq("key"))).thenReturn(new EntryFeedClient.Fetched(classes));
        RaceHubClassesService.Listing bare = service("key").list();

        when(client.fetch(any(), eq("key"))).thenReturn(new EntryFeedClient.Fetched("{\"classes\": " + classes + "}"));
        RaceHubClassesService.Listing wrapped = service("key").list();

        assertThat(wrapped).isEqualTo(bare);
        assertThat(bare.error()).isNull();
        assertThat(bare.classes()).extracting(RaceHubClassesService.ClubClass::id, RaceHubClassesService.ClubClass::active)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("7", false),
                        org.assertj.core.groups.Tuple.tuple("12", true));
    }

    @Test
    void marksTheLinkedRacingClass_andSuggestsOnlyAnUnlinkedClassWithExactlyOneSameNamedRacingClass() {
        when(client.fetch(any(), eq("key"))).thenReturn(new EntryFeedClient.Fetched("""
                [{"id": "1", "name": "A", "rc_class_name": "2WD Buggy", "active": true},
                 {"id": "2", "name": "B", "rc_class_name": "4wd buggy", "active": true},
                 {"id": "3", "name": "C", "rc_class_name": "Truck", "active": true},
                 {"id": "4", "name": "D", "rc_class_name": "No Match", "active": true}]"""));
        when(linkRepository.findAllOrderByClassKey()).thenReturn(List.of(link("1", 50L)));
        when(racingClassRepository.findAll()).thenReturn(List.of(racingClass(10, "2WD Buggy"),
                racingClass(11, "4WD Buggy"), racingClass(12, "Truck"), racingClass(13, "truck")));

        List<RaceHubClassesService.ClubClass> classes = service("key").list().classes();

        // Linked: shown as linked, never re-suggested
        assertThat(classes.get(0).linkedRacingClassId()).isEqualTo(50L);
        assertThat(classes.get(0).suggestedRacingClassId()).isNull();
        // One same-named racing class (ignoring case): suggested
        assertThat(classes.get(1).suggestedRacingClassId()).isEqualTo(11L);
        // Two same-named racing classes, or none: no suggestion
        assertThat(classes.get(2).suggestedRacingClassId()).isNull();
        assertThat(classes.get(3).suggestedRacingClassId()).isNull();
    }

    @Test
    void failuresComeBackAsAnErrorToShow() {
        assertThat(service(null).list().error()).contains("rctiming.racehub.token");
        verify(client, never()).fetch(any(), any());

        when(client.fetch(any(), eq("key"))).thenReturn(new EntryFeedClient.AuthFailed("refused"));
        assertThat(service("key").list().error()).isEqualTo("refused");

        when(client.fetch(any(), eq("key"))).thenReturn(new EntryFeedClient.Fetched("<html>"));
        assertThat(service("key").list().error()).contains("not a list of classes");

        when(client.fetch(any(), eq("key"))).thenReturn(new EntryFeedClient.Fetched("{\"nope\": 1}"));
        assertThat(service("key").list().error()).contains("not a list of classes");

        when(clubProfileService.getRaceHubEventsUrl()).thenReturn(Optional.empty());
        assertThat(service("key").list().error()).contains("events address");
    }

    @Test
    void theClassListIsFoundBesideTheEventsListOnly() {
        assertThat(RaceHubClassesService.classesUrl(URI.create("https://h.example:8443/api/rctc/v1/events/")))
                .isEqualTo(URI.create("https://h.example:8443/api/rctc/v1/classes"));
        assertThatThrownBy(() -> RaceHubClassesService.classesUrl(URI.create("https://h.example/api/rctc/v1/stuff")))
                .isInstanceOf(StateConflictException.class);
    }
}
