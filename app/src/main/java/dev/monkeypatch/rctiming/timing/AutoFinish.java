package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStateMachineService;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finishes a running race on the system's own account, the way the finish command does, so the result, the next
 * heat's grid and the exports follow, with an audit row. It does so when every car has taken the flag after the
 * clock ran out, and for the {@link LastLapTimeout} backstop.
 */
@Component
public class AutoFinish {

    private static final Logger log = LoggerFactory.getLogger(AutoFinish.class);

    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final RaceStateMachineService stateMachine;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    public AutoFinish(RaceRepository raceRepository, RoundRepository roundRepository,
                      RaceStateMachineService stateMachine, AuditService audit, TransactionTemplate transactions) {
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.stateMachine = stateMachine;
        this.audit = audit;
        this.transactions = transactions;
    }

    /** Every car took the flag: the race is over. Runs on the timing thread, straight after the last passing. */
    @EventListener
    public void onAllCarsFlagged(AllCarsFlaggedEvent event) {
        try {
            finish(event.raceId(), "flag-finish", "Finished the race automatically: every car took the flag");
        } catch (RuntimeException e) {
            // The last lap timeout still finishes it
            log.warn("Could not finish race {} after every car took the flag", event.raceId(), e);
        }
    }

    /** Finishes the race if it is still running; {@code actor} is the system actor's name. */
    public void finish(long raceId, String actor, String summary) {
        transactions.executeWithoutResult(status -> {
            // Look again inside the transaction: the director may have finished or restarted it meanwhile
            Race race = raceRepository.findById(raceId).orElse(null);
            if (race == null || race.getStatus() != RaceStatus.RUNNING) {
                return;
            }
            stateMachine.finish(race);
            Long eventId = roundRepository.findById(race.getRoundId()).map(Round::getEventId).orElse(null);
            audit.entry(Actor.system(actor), "RACE_FINISHED")
                    .entity("race", raceId).race(raceId).event(eventId)
                    .summary(summary)
                    .before(RaceStatus.RUNNING).after(RaceStatus.FINISHED)
                    .record();
            log.info("Race {} finished automatically ({})", raceId, actor);
        });
    }
}
