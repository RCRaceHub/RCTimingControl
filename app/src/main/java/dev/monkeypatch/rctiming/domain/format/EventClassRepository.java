package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.jooq.generated.tables.records.EventClassesRecord;
import dev.monkeypatch.rctiming.persistence.JooqRepository;
import dev.monkeypatch.rctiming.persistence.convert.JsonMapConverter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;

@Repository
public class EventClassRepository extends JooqRepository<EventClass, EventClassesRecord> {

    private static final RaceFormatConfigConverter CONFIG = new RaceFormatConfigConverter();
    private static final JsonMapConverter OVERRIDE = new JsonMapConverter();

    public EventClassRepository(DSLContext dsl) {
        super(dsl, EVENT_CLASSES, EVENT_CLASSES.ID);
    }

    public List<EventClass> findByEventId(Long eventId) {
        return findWhere(EVENT_CLASSES.EVENT_ID.eq(eventId));
    }

    /** Ids and racing classes of an event's classes, without loading their format config. */
    public List<EventClassRef> findRefsByEventId(Long eventId) {
        return dsl.select(EVENT_CLASSES.ID, EVENT_CLASSES.RACING_CLASS_ID)
                .from(EVENT_CLASSES)
                .where(EVENT_CLASSES.EVENT_ID.eq(eventId))
                .orderBy(EVENT_CLASSES.ID)
                .fetch(row -> new EventClassRef(row.value1(), row.value2()));
    }

    /** The event a class belongs to, without loading its format config. Empty when there is no such class. */
    public Optional<Long> findEventIdById(Long eventClassId) {
        return dsl.select(EVENT_CLASSES.EVENT_ID)
                .from(EVENT_CLASSES)
                .where(EVENT_CLASSES.ID.eq(eventClassId))
                .fetchOptional(EVENT_CLASSES.EVENT_ID);
    }

    /** The racing class and event names of each of the given classes, by id; a class that doesn't exist is left out. */
    public Map<Long, EventClassNames> namesByIds(Collection<Long> eventClassIds) {
        return dsl.select(EVENT_CLASSES.ID, RACING_CLASSES.NAME, EVENTS.NAME)
                .from(EVENT_CLASSES)
                .join(EVENTS).on(EVENTS.ID.eq(EVENT_CLASSES.EVENT_ID))
                .join(RACING_CLASSES).on(RACING_CLASSES.ID.eq(EVENT_CLASSES.RACING_CLASS_ID))
                .where(EVENT_CLASSES.ID.in(eventClassIds))
                .fetchMap(EVENT_CLASSES.ID, r -> new EventClassNames(r.value2(), r.value3()));
    }

    public record EventClassNames(String racingClassName, String eventName) {}

    public record EventClassRef(Long id, Long racingClassId) {
        public Long getId() {
            return id;
        }

        public Long getRacingClassId() {
            return racingClassId;
        }
    }

    @Override
    protected EventClass toEntity(EventClassesRecord r) {
        EventClass ec = new EventClass();
        ec.setId(r.getId());
        ec.setConfigSnapshot(CONFIG.convertToEntityAttribute(r.getConfigSnapshot()));
        ec.setConfigOverride(OVERRIDE.convertToEntityAttribute(r.getConfigOverride()));
        ec.setTemplateId(r.getTemplateId());
        ec.setCreatedAt(r.getCreatedAt());
        ec.setUpdatedAt(r.getUpdatedAt());
        ec.setEventId(r.getEventId());
        ec.setRacingClassId(r.getRacingClassId());
        ec.setCombinedRaceGroup(r.getCombinedRaceGroup());
        ec.setFinalsCount(r.getFinalsCount());
        return ec;
    }

    @Override
    protected void toRecord(EventClass ec, EventClassesRecord r) {
        r.setConfigSnapshot(CONFIG.convertToDatabaseColumn(ec.getConfigSnapshot()));
        r.setConfigOverride(OVERRIDE.convertToDatabaseColumn(ec.getConfigOverride()));
        r.setTemplateId(ec.getTemplateId());
        r.setCreatedAt(ec.getCreatedAt());
        r.setUpdatedAt(ec.getUpdatedAt());
        r.setEventId(ec.getEventId());
        r.setRacingClassId(ec.getRacingClassId());
        r.setCombinedRaceGroup(ec.getCombinedRaceGroup());
        r.setFinalsCount(ec.getFinalsCount());
    }

    /** The creation time is set once, on insert. */
    @Override
    protected List<Field<?>> insertOnly() {
        return List.of(EVENT_CLASSES.CREATED_AT);
    }

    @Override
    protected Long idOf(EventClass ec) {
        return ec.getId();
    }

    @Override
    protected void setId(EventClass ec, Long id) {
        ec.setId(id);
    }
}
