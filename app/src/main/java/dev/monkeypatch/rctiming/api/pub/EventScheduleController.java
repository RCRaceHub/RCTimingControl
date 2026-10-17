package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.query.event.EventScheduleDto;
import dev.monkeypatch.rctiming.query.event.EventScheduleQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The public event schedule under {@code /api/v1/events}. Anonymous: {@code GET /api/v1/events} is open in
 * {@code SecurityConfig}, so spectators and racers can see what is on without an account.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventScheduleController {

    private final EventScheduleQuery query;

    public EventScheduleController(EventScheduleQuery query) {
        this.query = query;
    }

    /**
     * Lists the events people can see, soonest first: those that are published, open for entries, closed to
     * entries or in progress. Each carries the ids of its finished races, so a page can link to their results, and
     * the id of its championship if it has one. Read only; no errors beyond an empty list.
     */
    @GetMapping
    public List<EventScheduleDto> list() {
        return query.getPublicSchedule();
    }
}
