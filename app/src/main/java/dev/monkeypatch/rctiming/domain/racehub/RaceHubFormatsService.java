package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedClient;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplate;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsEndpoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The club's RaceHub race formats next to the RCTC format templates they are linked to, and the linking itself, so an
 * organiser picks a format in RaceHub for a meeting and RCTC runs it with the template the club linked once.
 *
 * <p>{@link #list} asks RaceHub's {@code GET /api/rctc/v1/formats} with the club's key, at the events address under the
 * club's one RaceHub address with its last segment swapped (see {@link RaceHubClassesService}); it blocks for the feed client's timeouts, so the
 * controller calls it with no transaction open. A failure comes back as {@link Listing#error}. {@link #link} and
 * {@link #unlink} are one write transaction each, ADMIN only at the controller. A format with no link changes nothing:
 * the import then asks the official for a format as it always did, so RCTC works without RaceHub.
 */
@Service
public class RaceHubFormatsService {

    private final RaceHubResultsEndpoint endpoint;
    private final EntryFeedClient client;
    private final RaceHubFormatLinkRepository linkRepository;
    private final RaceFormatService raceFormatService;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public RaceHubFormatsService(RaceHubResultsEndpoint endpoint,
                                 EntryFeedClient client, RaceHubFormatLinkRepository linkRepository,
                                 RaceFormatService raceFormatService, AuditService audit, ObjectMapper objectMapper) {
        this.endpoint = endpoint;
        this.client = client;
        this.linkRepository = linkRepository;
        this.raceFormatService = raceFormatService;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    /** One RaceHub format and the RCTC template it is linked to ({@code linkedTemplateId} null when not linked). */
    public record ClubFormat(String id, String name, boolean active, Long linkedTemplateId) {
    }

    /** The formats, or {@code error} saying why RaceHub could not be asked (then {@code formats} is empty). */
    public record Listing(List<ClubFormat> formats, String error) {
    }

    /** The club's formats from RaceHub, ordered by name, with their links. Never throws for an unreachable RaceHub. */
    public Listing list() {
        try {
            Map<String, Long> links = linkRepository.findAllOrderByFormatKey().stream()
                    .collect(Collectors.toMap(RaceHubFormatLink::getFormatKey, RaceHubFormatLink::getTemplateId));
            List<ClubFormat> formats = new ArrayList<>();
            for (RaceHubFormatList f : fetch()) {
                formats.add(new ClubFormat(f.id(), f.name(), !Boolean.FALSE.equals(f.active()), links.get(f.id())));
            }
            return new Listing(formats, null);
        } catch (StateConflictException e) {
            return new Listing(List.of(), e.getMessage());
        } catch (IllegalArgumentException e) {
            return new Listing(List.of(), "The RaceHub address cannot be used. Set it again.");
        }
    }

    /**
     * Links a RaceHub format to a format template for the whole club, replacing an earlier link for the same format,
     * and records {@code RACEHUB_FORMAT_LINKED}.
     *
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException if the template does not exist (HTTP 404)
     * @throws IllegalArgumentException if the format id is blank (HTTP 400)
     */
    @Transactional
    public void link(Actor actor, String formatKey, Long templateId) {
        if (formatKey == null || formatKey.isBlank()) {
            throw new IllegalArgumentException("A RaceHub format id is required");
        }
        String key = formatKey.strip();
        RaceFormatTemplate template = raceFormatService.findById(templateId);
        RaceHubFormatLink link = linkRepository.findByFormatKey(key).orElseGet(() -> {
            RaceHubFormatLink l = new RaceHubFormatLink();
            l.setFormatKey(key);
            return l;
        });
        link.setTemplateId(template.getId());
        linkRepository.save(link);
        audit.entry(actor, "RACEHUB_FORMAT_LINKED").entity("race_format_template", template.getId())
                .summary("Linked the RaceHub race format " + key + " to " + template.getName())
                .record();
    }

    /** Removes the club's link for a format, if there is one, and records {@code RACEHUB_FORMAT_UNLINKED}. */
    @Transactional
    public void unlink(Actor actor, String formatKey) {
        linkRepository.findByFormatKey(formatKey == null ? "" : formatKey.strip()).ifPresent(link -> {
            linkRepository.deleteById(link.getId());
            audit.entry(actor, "RACEHUB_FORMAT_UNLINKED").entity("race_format_template", link.getTemplateId())
                    .summary("Removed the link for the RaceHub race format " + link.getFormatKey())
                    .record();
        });
    }

    private List<RaceHubFormatList> fetch() {
        URI eventsUrl = endpoint.eventsUrl();
        if (eventsUrl == null) {
            throw new StateConflictException("Set the RaceHub address first.");
        }
        String token = endpoint.token();
        if (token == null) {
            throw new StateConflictException("The club's key for RaceHub is not set (paste it on Results to RaceHub, "
                    + "or set rctiming.racehub.token in the settings file).");
        }
        URI url = RaceHubClassesService.siblingUrl(eventsUrl, "formats", "format list");
        return switch (client.fetch(url, token)) {
            case EntryFeedClient.Fetched fetched -> parse(fetched.body());
            case EntryFeedClient.AuthFailed failed -> throw new StateConflictException(failed.message());
            case EntryFeedClient.Failed failed -> throw new StateConflictException(failed.message());
            // Never asked conditionally, so RaceHub has no reason to answer 304
            case EntryFeedClient.NotModified notModified -> throw new StateConflictException("RaceHub sent no list");
        };
    }

    /** Reads either a bare array of formats or an object holding it under {@code formats}. */
    private List<RaceHubFormatList> parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode array = root.isArray() ? root : root.path("formats");
            if (!array.isArray()) {
                throw new StateConflictException("RaceHub's answer is not a list of race formats.");
            }
            List<RaceHubFormatList> formats = new ArrayList<>();
            for (JsonNode node : array) {
                RaceHubFormatList f = objectMapper.treeToValue(node, RaceHubFormatList.class);
                if (f != null && f.id() != null && !f.id().isBlank()) {
                    formats.add(new RaceHubFormatList(f.id().strip(), f.name(), f.active()));
                }
            }
            formats.sort(Comparator.comparing(f -> f.name() == null ? "" : f.name().toLowerCase(Locale.ROOT)));
            return formats;
        } catch (JacksonException e) {
            throw new StateConflictException("RaceHub's answer is not a list of race formats.");
        }
    }
}
