package dev.monkeypatch.rctiming.query.boards;

import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.query.racecontrol.FinalLegsDto;

import java.util.List;

/**
 * The finals of each class of an event, for anyone on the venue network. Names are as the public may see them
 * (#265). Answer of {@code GET /api/v1/boards/finals}; {@code eventId} and {@code eventName} are null, and
 * {@code classes} empty, when no event is found.
 */
public record FinalsBoardDto(Long eventId, String eventName, List<ClassFinals> classes) {

    /**
     * One class's finals, A first.
     *
     * @param eventClassId   the class in this event
     * @param className      the racing class's name
     * @param finalsFinished how many of its finals have finished
     * @param finalsTotal    how many finals the class has
     * @param finals         the finals in letter order
     */
    public record ClassFinals(Long eventClassId, String className, int finalsFinished, int finalsTotal,
                              List<Final> finals) {}

    /**
     * One final.
     *
     * @param letter the final's letter, "A" for the top final
     * @param status where the final stands
     * @param results the finished final's result rows, best first; empty until it has finished. For an A final run in
     *                legs these are the first leg's
     * @param legs   the A final's combined result when it is run in legs, else null; it is the final's result
     */
    public record Final(String letter, RaceStatus status, List<ResultSnapshotDto.ResultRow> results,
                        FinalLegsDto legs) {}
}
