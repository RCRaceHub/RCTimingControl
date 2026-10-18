package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import org.springframework.stereotype.Component;

/** The words and ids race-control audit rows use to say which race and which driver an action was about. */
@Component
public class RaceAuditLabels {

    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;

    public RaceAuditLabels(RaceRepository raceRepository, RoundRepository roundRepository,
                           EntryRepository entryRepository, CompetitorRepository competitorRepository) {
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
    }

    /** The race as an official knows it, such as {@code A final (race 42)} or {@code heat 2 (race 42)}. */
    public String race(long raceId) {
        return raceRepository.findById(raceId).map(r -> {
            String what = r.getFinalLetter() == null ? "heat " + r.getHeatNumber()
                    : "Z".equals(r.getFinalLetter()) ? "zombie round" : r.getFinalLetter() + " final";
            return what + " (race " + raceId + ")";
        }).orElse("race " + raceId);
    }

    /** The event the race belongs to, or null when the race or its round is gone. */
    public Long eventOf(long raceId) {
        return raceRepository.findById(raceId)
                .flatMap(r -> roundRepository.findById(r.getRoundId()))
                .map(Round::getEventId)
                .orElse(null);
    }

    /** The driver an entry belongs to, falling back to the entry's id. */
    public String driver(Long entryId) {
        if (entryId == null) {
            return "an unknown entry";
        }
        return entryRepository.findById(entryId)
                .map(Entry::getCompetitorId)
                .flatMap(competitorRepository::findById)
                .map(Competitor::getDisplayName)
                .orElse("entry " + entryId);
    }
}
