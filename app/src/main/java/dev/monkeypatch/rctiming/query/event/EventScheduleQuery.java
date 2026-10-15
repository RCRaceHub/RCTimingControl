package dev.monkeypatch.rctiming.query.event;

import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipEventLinks.CHAMPIONSHIP_EVENT_LINKS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

@Service
@ReadTransaction
public class EventScheduleQuery {

    private static final Logger log = LoggerFactory.getLogger(EventScheduleQuery.class);

    private final DSLContext dsl;

    public EventScheduleQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<EventScheduleDto> getPublicSchedule() {
        var events = dsl
                .select(EVENTS.ID, EVENTS.NAME, EVENTS.EVENT_DATE)
                .from(EVENTS)
                .where(EVENTS.STATUS.in("PUBLISHED", "OPEN", "ENTRIES_CLOSED", "IN_PROGRESS"))
                .orderBy(EVENTS.EVENT_DATE.asc(), EVENTS.ID.asc())
                .fetch();
        if (events.isEmpty()) {
            return List.of();
        }

        List<Long> eventIds = events.getValues(EVENTS.ID);
        Map<Long, List<Long>> finishedRacesByEvent = finishedRacesByEvent(eventIds);
        Map<Long, Long> championshipByEvent = championshipByEvent(eventIds);

        return events.stream().map(r -> new EventScheduleDto(
                r.get(EVENTS.ID),
                r.get(EVENTS.NAME),
                r.get(EVENTS.EVENT_DATE),
                finishedRacesByEvent.getOrDefault(r.get(EVENTS.ID), List.of()),
                championshipByEvent.get(r.get(EVENTS.ID))
        )).toList();
    }

    private Map<Long, List<Long>> finishedRacesByEvent(List<Long> eventIds) {
        return dsl.select(ROUNDS.EVENT_ID, RACES.ID)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .where(ROUNDS.EVENT_ID.in(eventIds))
                .and(RACES.STATUS.eq("FINISHED"))
                .fetchGroups(ROUNDS.EVENT_ID, RACES.ID);
    }

    /** One championship per event by convention; if an event has more, the schedule shows the lowest id. */
    private Map<Long, Long> championshipByEvent(List<Long> eventIds) {
        Map<Long, Long> byEvent = new HashMap<>();
        dsl.select(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID, CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID)
                .from(CHAMPIONSHIP_EVENT_LINKS)
                .where(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID.in(eventIds))
                .orderBy(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID.asc())
                .fetchGroups(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID, CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID)
                .forEach((eventId, championshipIds) -> {
                    if (championshipIds.size() > 1) {
                        log.warn("Event {} is linked to {} championships; only the first is shown on the schedule",
                                eventId, championshipIds.size());
                    }
                    byEvent.put(eventId, championshipIds.get(0));
                });
        return byEvent;
    }
}
