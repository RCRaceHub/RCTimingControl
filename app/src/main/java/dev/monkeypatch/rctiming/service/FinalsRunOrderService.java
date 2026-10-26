package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.format.BumpUpConfig;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderItemDto;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lets an official move a final earlier or later in the event's run order when a final runs in legs, such as the
 * three-leg A final at a national: leg 1 can then run between two of the other finals, leg 2 between two more.
 * Without a final in legs the generated order stays, and finals of a Bump Up class never move, since each of
 * their finals takes drivers from the final below it.
 *
 * <p>A move swaps the final with its neighbour in the run order, which must also be a pending final that can move,
 * and then gives every race of the event a {@code run_rank} (see {@code RunOrderSort}), so the order reads the same
 * everywhere (race control, the boards, the pre-race check). Regenerating the rounds replaces the races, and with
 * them the ranks; adding or removing a final when the finals are recalculated clears them. Runs in one transaction with its audit row, on the caller's thread.
 */
@Service
@Transactional
public class FinalsRunOrderService {

    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final EventClassRepository eventClassRepository;
    private final RaceFormatService raceFormatService;
    private final RunOrderQuery runOrderQuery;
    private final AuditService audit;
    private final AutoRunService autoRunService;

    public FinalsRunOrderService(RaceRepository raceRepository,
                                 RoundRepository roundRepository,
                                 EventClassRepository eventClassRepository,
                                 RaceFormatService raceFormatService,
                                 RunOrderQuery runOrderQuery,
                                 AuditService audit,
                                 AutoRunService autoRunService) {
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.eventClassRepository = eventClassRepository;
        this.raceFormatService = raceFormatService;
        this.runOrderQuery = runOrderQuery;
        this.audit = audit;
        this.autoRunService = autoRunService;
    }

    /**
     * Moves the final one place in its event's run order.
     *
     * @param earlier true to move it earlier in the order, false for later
     *
     * @throws EntityNotFoundException if the race is not in a run order
     * @throws StateConflictException if no final of the event runs in legs, the final or its neighbour has been
     *                                called or started, either is not a final, belongs to a Bump Up class, or the
     *                                move would put a leg before an earlier leg of the same final; or there is
     *                                nowhere to move to
     */
    public void move(Actor actor, Long raceId, boolean earlier) {
        Race race = raceRepository.getOrThrow(raceId);
        long eventId = roundRepository.getOrThrow(race.getRoundId()).getEventId();
        List<RunOrderItemDto> order = runOrderQuery.findForEvent(eventId);
        if (order.stream().noneMatch(i -> i.finalLetter() != null && i.heatNumber() > 1)) {
            throw new StateConflictException("The finals can only be reordered when a final is run in legs");
        }
        int at = indexOf(order, raceId);
        int to = earlier ? at - 1 : at + 1;
        if (to < 0 || to >= order.size()) {
            throw new StateConflictException("That final is already " + (earlier ? "first" : "last")
                    + " in the run order");
        }
        RunOrderItemDto moving = order.get(at);
        RunOrderItemDto neighbour = order.get(to);
        requireMovable(moving);
        requireMovable(neighbour);
        if (sameFinal(moving, neighbour)) {
            throw new StateConflictException("The legs of a final run in order and can't swap places");
        }

        List<RunOrderItemDto> reordered = new ArrayList<>(order);
        reordered.set(at, neighbour);
        reordered.set(to, moving);
        for (int i = 0; i < reordered.size(); i++) {
            Race each = raceRepository.getOrThrow(reordered.get(i).raceId());
            if (!Objects.equals(each.getRunRank(), i + 1)) {
                each.setRunRank(i + 1);
                raceRepository.save(each);
            }
        }

        // Auto run may be counting down to the race that has just moved
        autoRunService.requestReplan(eventId);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("direction", earlier ? "UP" : "DOWN");
        after.put("swappedWithRaceId", neighbour.raceId());
        audit.entry(actor, "FINAL_MOVED").entity("race", raceId).race(raceId).event(eventId)
                .summary("Moved " + label(moving) + " " + (earlier ? "earlier" : "later")
                        + " in the run order, past " + label(neighbour))
                .after(after).record();
    }

    private void requireMovable(RunOrderItemDto item) {
        if (item.finalLetter() == null) {
            throw new StateConflictException("Only finals can be reordered; " + label(item) + " is not a final");
        }
        if (!"PENDING".equals(item.status())) {
            throw new StateConflictException(label(item) + " has been called or started and can't move");
        }
        boolean bumpUp = eventClassRepository.findById(item.eventClassId())
                .map(raceFormatService::getEffectiveConfig).orElse(null) instanceof BumpUpConfig;
        if (bumpUp) {
            throw new StateConflictException(label(item) + " is a bump-up final, which always runs in its set order");
        }
    }

    private static boolean sameFinal(RunOrderItemDto a, RunOrderItemDto b) {
        return a.eventClassId() == b.eventClassId() && Objects.equals(a.finalLetter(), b.finalLetter());
    }

    private static int indexOf(List<RunOrderItemDto> order, Long raceId) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).raceId() == raceId) {
                return i;
            }
        }
        throw new EntityNotFoundException("Race " + raceId + " is not in a run order");
    }

    private static String label(RunOrderItemDto item) {
        if (item.finalLetter() == null) {
            return "Heat " + item.heatNumber() + " of " + item.className();
        }
        return "Final " + item.finalLetter() + " (" + item.className()
                + (item.heatNumber() > 1 ? ", leg " + item.heatNumber() : "") + ")";
    }
}
