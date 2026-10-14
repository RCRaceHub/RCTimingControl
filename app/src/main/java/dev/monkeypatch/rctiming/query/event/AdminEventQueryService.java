package dev.monkeypatch.rctiming.query.event;

import dev.monkeypatch.rctiming.domain.event.EventStatus;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Tracks.TRACKS;

/** The meeting list the admin pages show, each with its track. */
@Service
@ReadTransaction
public class AdminEventQueryService {

    private final DSLContext dsl;

    public AdminEventQueryService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<AdminEventListDto> listEvents() {
        return dsl.select(
                    EVENTS.ID, EVENTS.NAME, EVENTS.EVENT_DATE, EVENTS.STATUS,
                    TRACKS.NAME.as("trackName"))
                .from(EVENTS)
                .leftJoin(TRACKS).on(TRACKS.ID.eq(EVENTS.TRACK_ID))
                .orderBy(EVENTS.EVENT_DATE.desc())
                .fetch(r -> new AdminEventListDto(
                        r.get(EVENTS.ID),
                        r.get(EVENTS.NAME),
                        r.get(EVENTS.EVENT_DATE),
                        EventStatus.valueOf(r.get(EVENTS.STATUS)),
                        r.get("trackName", String.class)
                ));
    }
}
