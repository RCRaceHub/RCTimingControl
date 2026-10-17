package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.race.MarshalAdjustment;
import dev.monkeypatch.rctiming.domain.race.MarshalAdjustmentRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.resultsexport.FinishedRaceCorrected;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.dto.MarshalAdjustmentDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Marshal lap adjustments (CTRL-03): a lap added to or taken off a car by hand. Each one is kept as a
 * {@link MarshalAdjustment} row, with who made it and the race's state at the time, and is never a state change.
 */
@Service
@Transactional
public class MarshalService {

    private final RaceRepository raceRepository;
    private final MarshalAdjustmentRepository marshalAdjustmentRepository;
    private final UserRepository userRepository;
    private final LapTimingService lapTimingService;
    private final ApplicationEventPublisher eventPublisher;

    public MarshalService(RaceRepository raceRepository,
                          MarshalAdjustmentRepository marshalAdjustmentRepository,
                          UserRepository userRepository,
                          LapTimingService lapTimingService,
                          ApplicationEventPublisher eventPublisher) {
        this.raceRepository = raceRepository;
        this.marshalAdjustmentRepository = marshalAdjustmentRepository;
        this.userRepository = userRepository;
        this.lapTimingService = lapTimingService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Records the adjustment and applies it: to the live timing while the race runs, or, once it has finished,
     * by sending its corrected results again. A lap delta other than +1 or -1 is refused before anything is read,
     * as the table allows only those. Runs on a request thread in one transaction; live timing is told only after
     * the commit, so a rolled-back adjustment never reaches the boards.
     *
     * @param actingUserId the official making the adjustment
     * @return the adjustment as saved
     */
    public MarshalAdjustment adjust(long raceId, long entryId, String transponderNumber, int lapDelta,
                                    long actingUserId) {
        if (lapDelta != 1 && lapDelta != -1) {
            throw new IllegalArgumentException("A marshal adjustment adds or takes off one lap, not " + lapDelta);
        }
        Race race = raceRepository.getOrThrow(raceId);
        String actingUserName = officialName(actingUserId);

        MarshalAdjustment adjustment = new MarshalAdjustment();
        adjustment.setRaceId(raceId);
        adjustment.setEntryId(entryId);
        adjustment.setTransponderNumber(transponderNumber);
        adjustment.setLapDelta(lapDelta);
        adjustment.setRaceStateAtTime(race.getStatus().name());
        adjustment.setActingUserId(actingUserId);
        adjustment.setActingUserName(actingUserName);
        adjustment.setAdjustedAt(Instant.now());
        marshalAdjustmentRepository.save(adjustment);

        if (race.getStatus() == RaceStatus.FINISHED) {
            // No live timing is left to adjust once a race finishes; send its results again instead (#27)
            eventPublisher.publishEvent(new FinishedRaceCorrected(raceId));
        } else {
            MarshalAdjustmentDto dto = new MarshalAdjustmentDto(raceId, entryId, transponderNumber, lapDelta,
                    actingUserName, adjustment.getAdjustedAt().toEpochMilli());
            AfterCommit.run(() -> lapTimingService.applyMarshalAdjustment(raceId, entryId, lapDelta, dto));
        }
        return adjustment;
    }

    /** The official's name as the race director sees it on the adjustment, or their email when they have none. */
    private String officialName(long userId) {
        return userRepository.findById(userId)
                .map(u -> {
                    String name = (u.getFirstName() != null ? u.getFirstName() + " " : "")
                                + (u.getLastName() != null ? u.getLastName() : "");
                    return name.isBlank() ? u.getEmail() : name.trim();
                })
                .orElse("Unknown");
    }
}
