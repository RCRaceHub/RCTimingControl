package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.format.BumpUpConfig;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatConfig;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds a class's finals from its qualifying results as soon as the last qualifying heat finishes, so the
 * finals are ready to run without an official having to ask. The layout comes from the class's format: a
 * bump-up format's grid size and bump spots, otherwise the field split evenly over the class's finals with no
 * bumps. The number of finals is the number the generator made for the class.
 *
 * <p>It does nothing when a qualifying heat is still to run, when there are no finals, or when a final has
 * already started, since seeding replaces the finals' grids. An official can still seed again by hand
 * ({@code EventRunOrderService.seedFinals}) until a final starts.
 */
@Service
public class AutoFinalsSeedingService {

    private static final Logger log = LoggerFactory.getLogger(AutoFinalsSeedingService.class);

    private final RaceRepository raceRepository;
    private final EventClassRepository eventClassRepository;
    private final QualifyingStandingsService qualifyingStandingsService;
    private final BumpUpSeedingService bumpUpSeedingService;
    private final RaceFormatService raceFormatService;
    private final AuditService audit;

    public AutoFinalsSeedingService(RaceRepository raceRepository,
                                    EventClassRepository eventClassRepository,
                                    QualifyingStandingsService qualifyingStandingsService,
                                    BumpUpSeedingService bumpUpSeedingService,
                                    RaceFormatService raceFormatService,
                                    AuditService audit) {
        this.raceRepository = raceRepository;
        this.eventClassRepository = eventClassRepository;
        this.qualifyingStandingsService = qualifyingStandingsService;
        this.bumpUpSeedingService = bumpUpSeedingService;
        this.raceFormatService = raceFormatService;
        this.audit = audit;
    }

    /** Call after a qualifying heat of the class has finished and been saved. */
    public void seedIfQualifyingComplete(Long eventClassId) {
        List<Race> qualifiers = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.QUALIFIER);
        if (qualifiers.stream().anyMatch(q -> q.getStatus() != RaceStatus.FINISHED)) {
            return;
        }
        List<Race> finals = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL);
        if (finals.isEmpty() || finals.stream().anyMatch(f -> f.getStatus() != RaceStatus.PENDING)) {
            return;
        }
        List<Long> standings = qualifyingStandingsService.standingsFor(eventClassId);
        if (standings.isEmpty()) {
            log.info("Not seeding the finals of event class {}: no qualifying result", eventClassId);
            return;
        }
        EventClass eventClass = eventClassRepository.getOrThrow(eventClassId);
        int finalsCount = finals.size();
        int[] layout = layout(raceFormatService.getEffectiveConfig(eventClass), standings.size(), finalsCount);
        bumpUpSeedingService.seedFinals(eventClassId, standings, finalsCount, layout[0], layout[1]);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("finalsCount", finalsCount);
        after.put("carsPerFinal", layout[0]);
        after.put("bumpCount", layout[1]);
        after.put("qualifyingOrder", standings);
        audit.entry(Actor.system("finals-seeding"), "FINALS_SEEDED").entity("event_class", eventClassId)
                .event(eventClass.getEventId())
                .summary("Seeded the finals from " + standings.size() + " qualifiers when qualifying finished ("
                        + finalsCount + " finals, " + layout[0] + " cars each, " + layout[1] + " bumping up)")
                .after(after).record();
    }

    /** {cars per final, bump count} for a field of {@code field} drivers over {@code finalsCount} finals. */
    static int[] layout(RaceFormatConfig config, int field, int finalsCount) {
        int cars = (field + finalsCount - 1) / finalsCount;
        int bump = 0;
        if (config instanceof BumpUpConfig bumpUp && bumpUp.gridSize() > 0) {
            cars = bumpUp.gridSize();
            bump = Math.max(0, Math.min(bumpUp.bumpSpots(), cars - 1));
            // The lowest final takes a full grid and each higher one its grid less the bump spots; grow the grid
            // until the field fits rather than leave drivers out of every final
            while (cars + (finalsCount - 1) * (cars - bump) < field) {
                cars++;
            }
        }
        return new int[] {Math.max(1, cars), bump};
    }
}
