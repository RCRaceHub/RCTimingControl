package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.Names;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedClient;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsEndpoint;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shows the club's RaceHub classes next to the racing classes they are linked to, so an official can link each once
 * (the "RaceHub classes" screen), also on a newly attached instance with no history.
 *
 * <p>It asks RaceHub's class list ({@code GET /api/rctc/v1/classes}) with the club's key. The address is the saved
 * events address with its last segment {@code events} swapped for {@code classes}, so the key goes only to the host
 * the club already named. Listing blocks for the feed client's timeouts (5 seconds to connect, 20 to read), so the
 * controller calls it with no transaction open. A failure comes back as {@link Listing#error}, a message an
 * official can read.
 *
 * <p>For a class the club has not linked, {@link ClubClass#suggestedRacingClassId} offers the one racing class with the
 * same name as the class's {@code rc_class_name} (or {@code name}); it is only a suggestion on the screen, and an
 * import never places a class by name.
 */
@Service
public class RaceHubClassesService {

    private final ClubProfileService clubProfileService;
    private final RaceHubResultsEndpoint endpoint;
    private final EntryFeedClient client;
    private final RaceHubClassLinkRepository linkRepository;
    private final RacingClassRepository racingClassRepository;
    private final ObjectMapper objectMapper;

    public RaceHubClassesService(ClubProfileService clubProfileService, RaceHubResultsEndpoint endpoint,
                                 EntryFeedClient client, RaceHubClassLinkRepository linkRepository,
                                 RacingClassRepository racingClassRepository, ObjectMapper objectMapper) {
        this.clubProfileService = clubProfileService;
        this.endpoint = endpoint;
        this.client = client;
        this.linkRepository = linkRepository;
        this.racingClassRepository = racingClassRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * One RaceHub class and where it stands.
     *
     * @param linkedRacingClassId    the racing class the club linked it to; null when not linked
     * @param suggestedRacingClassId the one racing class with the same name, for an unlinked class; null when none
     *                               or several match
     */
    public record ClubClass(String id, String name, String rcClassName, boolean active,
                            Long linkedRacingClassId, Long suggestedRacingClassId) {
    }

    /** What asking RaceHub produced: the classes, or {@code error} saying why not (then {@code classes} is empty). */
    public record Listing(List<ClubClass> classes, String error) {
    }

    /**
     * The club's classes from RaceHub, ordered by name, with their links. A RaceHub that cannot be reached or refuses
     * the key comes back as {@code error}, not an exception, so the screen can show why and offer a retry.
     */
    public Listing list() {
        try {
            return new Listing(load(), null);
        } catch (StateConflictException e) {
            return new Listing(List.of(), e.getMessage());
        }
    }

    private List<ClubClass> load() {
        List<RaceHubClassList> fetched = fetch();
        Map<String, Long> links = linkRepository.findAllOrderByClassKey().stream()
                .collect(Collectors.toMap(RaceHubClassLink::getClassKey, RaceHubClassLink::getRacingClassId));
        Map<String, List<Long>> byName = racingClassRepository.findAll().stream()
                .collect(Collectors.groupingBy(c -> Names.matchKey(c.getName()),
                        Collectors.mapping(RacingClass::getId, Collectors.toList())));
        List<ClubClass> result = new ArrayList<>();
        for (RaceHubClassList c : fetched) {
            Long linked = links.get(c.id());
            result.add(new ClubClass(c.id(), c.name(), c.rcClassName(),
                    !Boolean.FALSE.equals(c.active()), linked, linked != null ? null : suggestion(c, byName)));
        }
        return result;
    }

    private static Long suggestion(RaceHubClassList c, Map<String, List<Long>> byName) {
        String label = c.rcClassName() != null && !c.rcClassName().isBlank() ? c.rcClassName() : c.name();
        if (label == null || label.isBlank()) {
            return null;
        }
        List<Long> matches = byName.getOrDefault(Names.matchKey(label), List.of());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private List<RaceHubClassList> fetch() {
        String saved = clubProfileService.getRaceHubEventsUrl().orElseThrow(() ->
                new StateConflictException("Set the RaceHub events address first."));
        String token = endpoint.token();
        if (token == null) {
            throw new StateConflictException(
                    "The club's key for RaceHub is not set (rctiming.racehub.token in the settings file).");
        }
        URI url = classesUrl(RaceHubResultsEndpoint.parse(saved));
        return switch (client.fetch(url, token)) {
            case EntryFeedClient.Fetched fetched -> parse(fetched.body());
            case EntryFeedClient.AuthFailed failed -> throw new StateConflictException(failed.message());
            case EntryFeedClient.Failed failed -> throw new StateConflictException(failed.message());
        };
    }

    /** The class list lives beside the events list: same host and path, ending {@code classes}, and no query. */
    static URI classesUrl(URI eventsUrl) {
        String path = eventsUrl.getPath() == null ? "" : eventsUrl.getPath();
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (!trimmed.endsWith("/events")) {
            throw new StateConflictException("The RaceHub events address should end in /events, so the class list "
                    + "can be found beside it.");
        }
        return URI.create(eventsUrl.getScheme() + "://" + eventsUrl.getRawAuthority()
                + trimmed.substring(0, trimmed.length() - "events".length()) + "classes");
    }

    /** Reads either a bare array of classes or an object holding it under {@code classes}. */
    private List<RaceHubClassList> parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode array = root.isArray() ? root : root.path("classes");
            if (!array.isArray()) {
                throw new StateConflictException("RaceHub's answer is not a list of classes.");
            }
            List<RaceHubClassList> classes = new ArrayList<>();
            for (JsonNode node : array) {
                RaceHubClassList c = objectMapper.treeToValue(node, RaceHubClassList.class);
                if (c != null && c.id() != null && !c.id().isBlank()) {
                    classes.add(c);
                }
            }
            classes.sort(Comparator.comparing(c -> c.name() == null ? "" : c.name().toLowerCase()));
            return classes;
        } catch (JacksonException e) {
            throw new StateConflictException("RaceHub's answer is not a list of classes.");
        }
    }
}
