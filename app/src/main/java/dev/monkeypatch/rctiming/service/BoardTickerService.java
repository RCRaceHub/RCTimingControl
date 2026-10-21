package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.api.boards.dto.TickerDto;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.query.boards.BoardQuery;
import dev.monkeypatch.rctiming.query.boards.BoardRaceDto;
import dev.monkeypatch.rctiming.query.racecontrol.GridCallSlotDto;
import dev.monkeypatch.rctiming.query.racecontrol.MarshalDutyRowDto;
import dev.monkeypatch.rctiming.query.racecontrol.PreRaceReadinessDto;
import dev.monkeypatch.rctiming.query.racecontrol.PreRaceReadinessQuery;
import dev.monkeypatch.rctiming.query.racecontrol.QualifyingTableDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gathers what the overlay's ticker scrolls: the next race, who is on its grid, who marshals it, and each class's
 * top qualifier. It reads from the same queries race control uses and applies each competitor's public name
 * setting, since the overlay is anonymous. Called per poll from {@code BoardController} on a Tomcat thread; it
 * changes nothing and each query is its own read snapshot.
 */
@Service
public class BoardTickerService {

    private final BoardQuery boardQuery;
    private final PreRaceReadinessQuery preRaceReadinessQuery;
    private final QualifyingTableService qualifyingTableService;
    private final PublicNames publicNames;

    public BoardTickerService(BoardQuery boardQuery, PreRaceReadinessQuery preRaceReadinessQuery,
                              QualifyingTableService qualifyingTableService, PublicNames publicNames) {
        this.boardQuery = boardQuery;
        this.preRaceReadinessQuery = preRaceReadinessQuery;
        this.qualifyingTableService = qualifyingTableService;
        this.publicNames = publicNames;
    }

    /** The ticker for an event, which is already resolved by the caller. */
    public TickerDto forEvent(long eventId) {
        Optional<BoardRaceDto> next = boardQuery.nextRace(eventId);
        List<String> drivers = List.of();
        List<String> marshals = List.of();
        if (next.isPresent()) {
            PreRaceReadinessDto readiness = preRaceReadinessQuery.load(next.get().raceId());
            Map<Long, Long> competitors = boardQuery.competitorIds(entryIds(readiness));
            PublicNames.Snapshot names = publicNames.snapshot();
            drivers = readiness.gridCall().stream()
                    .map(slot -> names.of(competitors.get(slot.entryId()), slot.driverName(), slot.carNumber()))
                    .toList();
            marshals = readiness.marshalDuty().stream()
                    .map(row -> names.of(competitors.get(row.entryId()), row.driverName(), row.carNumber()))
                    .toList();
        }
        return new TickerDto(next.map(BoardRaceDto::label).orElse(null), drivers, marshals, topQualifiers(eventId));
    }

    private static List<Long> entryIds(PreRaceReadinessDto readiness) {
        List<Long> ids = new ArrayList<>();
        readiness.gridCall().stream().map(GridCallSlotDto::entryId).forEach(ids::add);
        readiness.marshalDuty().stream().map(MarshalDutyRowDto::entryId).forEach(ids::add);
        return ids;
    }

    private List<TickerDto.TopQualifier> topQualifiers(long eventId) {
        List<TickerDto.TopQualifier> top = new ArrayList<>();
        for (QualifyingTableDto table : qualifyingTableService.forEventPublic(eventId)) {
            if (table.heatsFinished() > 0 && !table.rows().isEmpty()) {
                QualifyingTableDto.Row first = table.rows().get(0);
                top.add(new TickerDto.TopQualifier(table.className(), first.driverName(), first.bestLapMs()));
            }
        }
        return top;
    }
}
