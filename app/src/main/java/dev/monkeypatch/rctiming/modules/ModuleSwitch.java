package dev.monkeypatch.rctiming.modules;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one place that says whether a module is on (#795). Every entry point of a module asks it: a controller through
 * {@link RequiresModule}, a listener or scheduled job by calling {@link #isEnabled(String)} first and doing nothing
 * when it is off.
 *
 * <p>A module is on unless an admin switched it off. An event may also turn a module off for itself (a fun day, say),
 * but can never turn on one the club has off, so the event check is {@code club on AND event not off}.
 *
 * <p>Reads are served from memory, because the timing thread asks for every lap passing and must not touch the
 * database. The cache is filled on first use under a lock, and each write updates it once its transaction has
 * committed, so a rolled-back change never shows and a committed one takes effect at once on every thread. Writes run
 * in a transaction on the write connection and are audited ({@code MODULE_SWITCHED}, {@code EVENT_MODULE_SWITCHED}).
 */
@Service
public class ModuleSwitch {

    private final ModuleRegistry registry;
    private final ModuleSwitchRepository repository;
    private final AuditService audit;
    private final Clock clock;

    private final Object lock = new Object();
    /** Club-wide switches that were saved; a module missing from here is on. Replaced, never changed in place. */
    private volatile Map<String, Boolean> clubSwitches;
    /** "eventId:moduleId" for each module an event turned off. Replaced, never changed in place. */
    private volatile Set<String> eventOffs;

    @Autowired
    public ModuleSwitch(ModuleRegistry registry, ModuleSwitchRepository repository, AuditService audit) {
        this(registry, repository, audit, Clock.systemUTC());
    }

    ModuleSwitch(ModuleRegistry registry, ModuleSwitchRepository repository, AuditService audit, Clock clock) {
        this.registry = registry;
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    /** Whether the club has the module on. An id that is not a module is off, so a typo never runs a feature. */
    public boolean isEnabled(String moduleId) {
        if (registry.find(moduleId).isEmpty()) {
            return false;
        }
        return loadClub().getOrDefault(moduleId, true);
    }

    /** Whether the module is on for this event: on for the club and not turned off by the event. */
    public boolean isEnabled(String moduleId, long eventId) {
        return isEnabled(moduleId) && !loadEventOffs().contains(key(eventId, moduleId));
    }

    /** One module with the club's switch and, when asked about an event, whether that event turned it off. */
    public record ModuleStatus(ModuleDescriptor module, boolean clubEnabled, boolean eventOff) {

        /** Whether the module is usable: on for the club and not turned off by the event. */
        public boolean enabled() {
            return clubEnabled && !eventOff;
        }
    }

    /** Every module with its club switch, for the Modules page and the menu. */
    public List<ModuleStatus> status() {
        return registry.all().stream()
                .map(m -> new ModuleStatus(m, loadClub().getOrDefault(m.id(), true), false)).toList();
    }

    /**
     * Every module as one event sees it.
     *
     * @throws EntityNotFoundException (404) if there is no such event
     */
    public List<ModuleStatus> status(long eventId) {
        if (!repository.eventExists(eventId)) {
            throw new EntityNotFoundException("No event with id " + eventId);
        }
        return registry.all().stream().map(m -> new ModuleStatus(m, loadClub().getOrDefault(m.id(), true),
                loadEventOffs().contains(key(eventId, m.id())))).toList();
    }

    /** Throws {@link ModuleDisabledException} (HTTP 404) while the module is off. */
    public void require(String moduleId) {
        if (!isEnabled(moduleId)) {
            throw new ModuleDisabledException(moduleId);
        }
    }

    /**
     * Switches a module on or off for the whole club and audits it.
     *
     * @throws EntityNotFoundException (404) if there is no such module
     */
    @Transactional
    public void setEnabled(String moduleId, boolean enabled, Actor actor) {
        ModuleDescriptor module = registry.find(moduleId)
                .orElseThrow(() -> new EntityNotFoundException("No module called " + moduleId));
        synchronized (lock) {
            boolean before = loadClub().getOrDefault(moduleId, true);
            repository.saveSwitch(moduleId, enabled, actor.userId(), clock.instant());
            if (before != enabled) {
                audit.entry(actor, "MODULE_SWITCHED").entity("module", moduleId)
                        .summary((enabled ? "Switched on " : "Switched off ") + module.name())
                        .before(before).after(enabled).record();
            }
        }
        afterCommit(() -> {
            synchronized (lock) {
                Map<String, Boolean> next = new HashMap<>(loadClub());
                next.put(moduleId, enabled);
                clubSwitches = Map.copyOf(next);
            }
        });
    }

    /**
     * Turns a module off for one event, or lets the event use it again. Whether the club has it on is a separate
     * question: this never turns a module on for an event whose club has it off.
     *
     * @throws EntityNotFoundException (404) if there is no such module or event
     */
    @Transactional
    public void setEventOff(long eventId, String moduleId, boolean off, Actor actor) {
        ModuleDescriptor module = registry.find(moduleId)
                .orElseThrow(() -> new EntityNotFoundException("No module called " + moduleId));
        if (!repository.eventExists(eventId)) {
            throw new EntityNotFoundException("No event with id " + eventId);
        }
        synchronized (lock) {
            boolean before = loadEventOffs().contains(key(eventId, moduleId));
            if (off) {
                repository.addEventOff(eventId, moduleId);
            } else {
                repository.removeEventOff(eventId, moduleId);
            }
            if (before != off) {
                audit.entry(actor, "EVENT_MODULE_SWITCHED").entity("module", moduleId).event(eventId)
                        .summary((off ? "Turned off " : "Turned back on ") + module.name() + " for this event")
                        .before(before).after(off).record();
            }
        }
        afterCommit(() -> {
            synchronized (lock) {
                Set<String> next = new HashSet<>(loadEventOffs());
                if (off) {
                    next.add(key(eventId, moduleId));
                } else {
                    next.remove(key(eventId, moduleId));
                }
                eventOffs = Set.copyOf(next);
            }
        });
    }

    /** Runs {@code action} once the current transaction commits, or at once when there is none (a direct call). */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private Map<String, Boolean> loadClub() {
        Map<String, Boolean> current = clubSwitches;
        if (current == null) {
            synchronized (lock) {
                current = clubSwitches;
                if (current == null) {
                    current = Map.copyOf(repository.findAllSwitches());
                    clubSwitches = current;
                }
            }
        }
        return current;
    }

    private Set<String> loadEventOffs() {
        Set<String> current = eventOffs;
        if (current == null) {
            synchronized (lock) {
                current = eventOffs;
                if (current == null) {
                    Set<String> loaded = new HashSet<>();
                    repository.findAllEventOffs().forEach(e -> loaded.add(key(e.eventId(), e.moduleId())));
                    current = Set.copyOf(loaded);
                    eventOffs = current;
                }
            }
        }
        return current;
    }

    private static String key(long eventId, String moduleId) {
        return eventId + ":" + moduleId;
    }
}
