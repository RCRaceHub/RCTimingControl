package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.boards.FinalsBoardDto;
import dev.monkeypatch.rctiming.query.boards.PodiumBoardDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the public podium page from the finals board: the first three of each class's A final, and the best-placed
 * driver of each category tag (Junior, Vet and so on, #494) among the class's finals taken together. Categories are
 * the club's own tags on a competitor, so no age or date of birth is read or shown. It only reads. Called from the
 * board controller on a request thread; one call is one read snapshot.
 */
@Service
public class PodiumBoardService {

    private static final int PODIUM_PLACES = 3;

    private final FinalsBoardService finalsBoardService;
    private final CompetitorRepository competitorRepository;

    public PodiumBoardService(FinalsBoardService finalsBoardService, CompetitorRepository competitorRepository) {
        this.finalsBoardService = finalsBoardService;
        this.competitorRepository = competitorRepository;
    }

    /**
     * The podium of every class of the event that has a finished final, in the order of the classes. A class whose
     * finals have not started is left out.
     */
    @ReadTransaction
    public List<PodiumBoardDto.ClassPodium> forEventPublic(Long eventId) {
        List<FinalsBoardDto.ClassFinals> finals = finalsBoardService.forEventPublic(eventId);
        Set<Long> ids = finals.stream()
                .flatMap(c -> c.finals().stream())
                .flatMap(f -> finishedOrder(f).stream())
                .map(Placing::competitorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<String>> tags = new LinkedHashMap<>();
        competitorRepository.findAllById(ids).forEach(c -> tags.put(c.getId(), c.categoryTags()));
        return build(finals, tags);
    }

    /** One driver's place in a final, with the name already as the public may see it. */
    record Placing(Long competitorId, String driverName, String carNumber, boolean dnf) {}

    /**
     * Works out each class's podium and category leaders; pure, so it is tested without a database.
     *
     * @param classes the finals board
     * @param tagsByCompetitor the category tags each competitor holds
     */
    static List<PodiumBoardDto.ClassPodium> build(List<FinalsBoardDto.ClassFinals> classes,
                                                 Map<Long, List<String>> tagsByCompetitor) {
        List<PodiumBoardDto.ClassPodium> out = new ArrayList<>();
        for (FinalsBoardDto.ClassFinals cls : classes) {
            List<Placing> overall = new ArrayList<>();
            List<Placing> aFinal = List.of();
            boolean aAbandoned = false;
            for (FinalsBoardDto.Final f : cls.finals()) {
                if (f.abandoned()) {
                    aAbandoned |= "A".equals(f.letter());
                    continue;
                }
                List<Placing> placings = finishedOrder(f);
                if (placings.isEmpty()) {
                    // A final still to run: lower finals cannot be placed against it
                    break;
                }
                if ("A".equals(f.letter())) {
                    aFinal = placings;
                }
                overall.addAll(placings);
            }
            if (overall.isEmpty()) {
                continue;
            }
            List<PodiumBoardDto.Place> podium = new ArrayList<>();
            for (Placing p : aFinal) {
                if (podium.size() < PODIUM_PLACES && !p.dnf()) {
                    podium.add(new PodiumBoardDto.Place(podium.size() + 1, p.driverName(), p.carNumber()));
                }
            }
            out.add(new PodiumBoardDto.ClassPodium(cls.eventClassId(), cls.className(),
                    cls.finalsFinished() == cls.finalsTotal(), aAbandoned, podium,
                    leaders(overall, tagsByCompetitor)));
        }
        return out;
    }

    private static List<PodiumBoardDto.CategoryLeader> leaders(List<Placing> overall,
                                                              Map<Long, List<String>> tagsByCompetitor) {
        Map<String, PodiumBoardDto.CategoryLeader> byTag = new LinkedHashMap<>();
        for (int i = 0; i < overall.size(); i++) {
            Placing p = overall.get(i);
            if (p.dnf()) {
                continue;
            }
            for (String tag : tagsByCompetitor.getOrDefault(p.competitorId(), List.of())) {
                byTag.putIfAbsent(tag.toLowerCase(Locale.ROOT),
                        new PodiumBoardDto.CategoryLeader(tag, p.driverName(), p.carNumber(), i + 1));
            }
        }
        return byTag.values().stream()
                .sorted(Comparator.comparing(l -> l.category().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** The final's order if it has finished and was not abandoned: the legs' combined order, else its own result. */
    private static List<Placing> finishedOrder(FinalsBoardDto.Final f) {
        if (f.abandoned() || f.status() != RaceStatus.FINISHED) {
            return List.of();
        }
        if (f.legs() != null && !f.legs().rows().isEmpty()) {
            return f.legs().rows().stream()
                    .map(r -> new Placing(r.competitorId(), r.driverName(), r.carNumber(), false)).toList();
        }
        return f.results().stream()
                .map(r -> new Placing(r.competitorId(), r.driverName(), r.carNumber(), r.dnf())).toList();
    }
}
