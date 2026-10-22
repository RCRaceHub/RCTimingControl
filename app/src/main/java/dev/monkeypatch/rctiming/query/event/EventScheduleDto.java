package dev.monkeypatch.rctiming.query.event;

import java.time.LocalDate;
import java.util.List;

/**
 * One meeting on the public schedule ({@link EventScheduleQuery}).
 *
 * @param finishedRaces the meeting's finished races in run order, empty when none has finished
 * @param championshipId the championship the meeting counts towards, or null when it is not linked to one
 */
public record EventScheduleDto(
        Long id,
        String name,
        LocalDate eventDate,
        List<FinishedRace> finishedRaces,
        Long championshipId
) {

    /**
     * A finished race with enough to say what its results are before anyone opens them.
     *
     * @param id the race, as used by {@code /results/{id}}
     * @param roundLabel the round in words ("Qualifying 2", "B Final"), the same wording as {@code RaceLabel}
     * @param className the racing class
     * @param heatNumber the heat within the round and class; meaningless for a final
     * @param isFinal true for a final, whose link needs no heat number
     */
    public record FinishedRace(
            Long id,
            String roundLabel,
            String className,
            int heatNumber,
            boolean isFinal
    ) {
    }
}
