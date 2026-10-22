package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RoundType;

import java.util.List;
import java.util.Objects;

/**
 * Helpers for a final run in several legs, as the BRCA A final is (#427). The legs are races of one final's round:
 * leg 1 has heat number 1, the others 2, 3 and so on, and all carry the final's letter. Leg 1 is "the final" for
 * everything that lays out or edits finals (seeding, the line-up panel); the later legs only copy its grid.
 *
 * <p>Static and Spring-free so the seeding and line-up services can call it without taking another dependency. The
 * caller owns the transaction.
 */
final class FinalLegs {

    private FinalLegs() {
    }

    /** Whether the race is leg 2 or later of a final, which carries no line-up of its own. */
    static boolean isExtraLeg(Race race) {
        return race.getFinalLetter() != null && race.getHeatNumber() > 1;
    }

    /**
     * Copies each final's leg 1 grid onto its later legs that have not been called yet, so a seed or an edit of the
     * line-up reaches every leg. Legs already called or started are left alone.
     */
    static void syncGrids(RaceRepository raceRepository, RaceEntryRepository raceEntryRepository, Long eventClassId) {
        List<Race> finals = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL);
        for (Race first : finals) {
            if (first.getFinalLetter() == null || first.getHeatNumber() != 1) {
                continue;
            }
            List<RaceEntry> grid = raceEntryRepository.findByRaceIdOrderByGridPosition(first.getId());
            for (Race leg : finals) {
                boolean laterLeg = isExtraLeg(leg) && leg.getRoundId().equals(first.getRoundId())
                        && Objects.equals(leg.getFinalLetter(), first.getFinalLetter());
                if (!laterLeg || leg.getStatus() != RaceStatus.PENDING) {
                    continue;
                }
                raceEntryRepository.deleteAll(raceEntryRepository.findByRaceIdOrderByGridPosition(leg.getId()));
                for (RaceEntry source : grid) {
                    RaceEntry copy = new RaceEntry();
                    copy.setRaceId(leg.getId());
                    copy.setEntryId(source.getEntryId());
                    copy.setGridPosition(source.getGridPosition());
                    copy.setCarNumber(source.getCarNumber());
                    copy.setBumped(source.isBumped());
                    raceEntryRepository.save(copy);
                }
            }
        }
    }
}
