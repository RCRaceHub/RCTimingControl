package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedClient;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplate;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsEndpoint;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The race format links: RaceHub's format list beside the links, and making and removing a link. */
class RaceHubFormatsServiceTest {

    private static final String EVENTS_URL = "https://racehub.example/api/rctc/v1/events?state=ready";
    private static final URI FORMATS_URL = URI.create("https://racehub.example/api/rctc/v1/formats");

    private final ClubProfileService clubProfileService = mock(ClubProfileService.class);
    private final EntryFeedClient client = mock(EntryFeedClient.class);
    private final RaceHubFormatLinkRepository linkRepository = mock(RaceHubFormatLinkRepository.class);
    private final RaceFormatService raceFormatService = mock(RaceFormatService.class);
    private final AuditService audit = mock(AuditService.class, Answers.RETURNS_DEEP_STUBS);
    private final Actor actor = mock(Actor.class);

    private RaceHubFormatsService service(String token) {
        RaceHubResultsEndpoint endpoint = new RaceHubResultsEndpoint(
                new RaceHubResultsProperties(null, token), clubProfileService);
        return new RaceHubFormatsService(endpoint, client, linkRepository, raceFormatService,
                audit, JsonMapper.builder().build());
    }

    @BeforeEach
    void defaults() {
        when(clubProfileService.getRaceHubUrl()).thenReturn(Optional.of("https://racehub.example"));
        when(linkRepository.findAllOrderByFormatKey()).thenReturn(List.of());
    }

    private static RaceHubFormatLink link(String key, long templateId) {
        RaceHubFormatLink l = new RaceHubFormatLink();
        l.setId(5L);
        l.setFormatKey(key);
        l.setTemplateId(templateId);
        return l;
    }

    private static RaceFormatTemplate template(long id, String name) {
        RaceFormatTemplate t = new RaceFormatTemplate();
        t.setId(id);
        t.setName(name);
        return t;
    }

    @Test
    void saysWhichSettingIsMissingWithoutAskingRaceHub() {
        when(clubProfileService.getRaceHubUrl()).thenReturn(Optional.empty());

        RaceHubFormatsService.Listing listing = service("key").list();

        assertThat(listing.error()).isNotBlank();
        assertThat(listing.formats()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void asksForTheFormatListBesideTheEventsAddress_keepsInactiveOnes_andMarksLinks() {
        when(client.fetch(any(), eq("key"))).thenReturn(new EntryFeedClient.Fetched("""
                {"formats": [{"id": "2", "name": "Timed", "active": false},
                             {"id": 1, "name": "BRCA bump-up", "active": true}]}"""));
        when(linkRepository.findAllOrderByFormatKey()).thenReturn(List.of(link("1", 40L)));

        RaceHubFormatsService.Listing listing = service("key").list();

        verify(client).fetch(FORMATS_URL, "key");
        assertThat(listing.error()).isNull();
        assertThat(listing.formats()).containsExactly(
                new RaceHubFormatsService.ClubFormat("1", "BRCA bump-up", true, 40L),
                new RaceHubFormatsService.ClubFormat("2", "Timed", false, null));
    }

    @Test
    void anAnswerThatIsNotAFormatListComesBackAsAnError() {
        when(client.fetch(any(), eq("key"))).thenReturn(new EntryFeedClient.Fetched("{\"oops\": 1}"));

        assertThat(service("key").list().error()).contains("not a list of race formats");
    }

    @Test
    void linkingStoresTheTemplateForTheFormatKey_andAnEarlierLinkMovesToTheNewTemplate() {
        when(raceFormatService.findById(41L)).thenReturn(template(41L, "Bump-up 5"));
        when(linkRepository.findByFormatKey("1")).thenReturn(Optional.of(link("1", 40L)));

        service("key").link(actor, " 1 ", 41L);

        verify(linkRepository).save(argThat(l ->
                "1".equals(l.getFormatKey()) && Long.valueOf(41L).equals(l.getTemplateId())));
    }

    @Test
    void linkingABlankFormatSavesNothing() {
        assertThatThrownBy(() -> service("key").link(actor, " ", 41L)).isInstanceOf(IllegalArgumentException.class);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void unlinkingRemovesTheLinkIfThereIsOne() {
        when(linkRepository.findByFormatKey("1")).thenReturn(Optional.of(link("1", 40L)));

        service("key").unlink(actor, "1");
        service("key").unlink(actor, "9");

        verify(linkRepository).deleteById(5L);
        verify(linkRepository, times(1)).deleteById(any());
    }
}
