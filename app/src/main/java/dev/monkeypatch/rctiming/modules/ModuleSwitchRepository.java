package dev.monkeypatch.rctiming.modules;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EventModulesOff.EVENT_MODULES_OFF;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Modules.MODULES;

/** SQL for the {@code modules} and {@code event_modules_off} tables (#795). No entity: the rows are plain flags. */
@Repository
public class ModuleSwitchRepository {

    private final DSLContext dsl;

    public ModuleSwitchRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** The saved switch of every module that has had one flipped, by module id. */
    public Map<String, Boolean> findAllSwitches() {
        return dsl.select(MODULES.ID, MODULES.ENABLED).from(MODULES).fetch().stream()
                .collect(Collectors.toMap(r -> r.value1(), r -> r.value2() != null && r.value2() == 1));
    }

    /** Saves a module's club-wide switch, adding its row the first time. */
    @Transactional
    public void saveSwitch(String moduleId, boolean enabled, Long changedBy, Instant changedAt) {
        int flag = enabled ? 1 : 0;
        dsl.insertInto(MODULES)
                .set(MODULES.ID, moduleId).set(MODULES.ENABLED, flag)
                .set(MODULES.CHANGED_BY, changedBy).set(MODULES.CHANGED_AT, changedAt)
                .onConflict(MODULES.ID).doUpdate()
                .set(MODULES.ENABLED, flag).set(MODULES.CHANGED_BY, changedBy).set(MODULES.CHANGED_AT, changedAt)
                .execute();
    }

    /** Every (event id, module id) pair an event has turned off. */
    public Set<EventModule> findAllEventOffs() {
        Set<EventModule> offs = new HashSet<>();
        for (var r : dsl.select(EVENT_MODULES_OFF.EVENT_ID, EVENT_MODULES_OFF.MODULE_ID).from(EVENT_MODULES_OFF).fetch()) {
            offs.add(new EventModule(r.value1(), r.value2()));
        }
        return offs;
    }

    /** Turns a module off for one event; already off is not an error. */
    @Transactional
    public void addEventOff(long eventId, String moduleId) {
        dsl.insertInto(EVENT_MODULES_OFF).set(EVENT_MODULES_OFF.EVENT_ID, eventId)
                .set(EVENT_MODULES_OFF.MODULE_ID, moduleId).onConflictDoNothing().execute();
    }

    /** Lets an event use a module again (when the club has it on); no row is not an error. */
    @Transactional
    public void removeEventOff(long eventId, String moduleId) {
        dsl.deleteFrom(EVENT_MODULES_OFF).where(EVENT_MODULES_OFF.EVENT_ID.eq(eventId))
                .and(EVENT_MODULES_OFF.MODULE_ID.eq(moduleId)).execute();
    }

    /** The modules one event has turned off. */
    public List<String> findEventOffs(long eventId) {
        return dsl.select(EVENT_MODULES_OFF.MODULE_ID).from(EVENT_MODULES_OFF)
                .where(EVENT_MODULES_OFF.EVENT_ID.eq(eventId)).fetch(EVENT_MODULES_OFF.MODULE_ID);
    }

    /** A module an event has turned off. */
    public record EventModule(long eventId, String moduleId) {}
}
