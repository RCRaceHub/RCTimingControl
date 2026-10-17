package dev.monkeypatch.rctiming.backup;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records a database restore in the audit log when the app next starts (#139). The {@code restore} command
 * runs with the app stopped and replaces the database, so it leaves a {@link RestoreNote} for this to pick up.
 */
@Component
public class RestoreAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(RestoreAuditRecorder.class);

    private final Path dataDirectory;
    private final AuditService audit;

    @Autowired
    public RestoreAuditRecorder(DatabaseProperties database, AuditService audit) {
        this(database.effectiveDataDirectory(), audit);
    }

    RestoreAuditRecorder(Path dataDirectory, AuditService audit) {
        this.dataDirectory = dataDirectory;
        this.audit = audit;
    }

    /**
     * Runs once, when the app is ready, on the main thread. Writes the audit row, as the OS user who ran the
     * command, then removes the note. A failure is logged and leaves the note for the next start.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recordPendingRestore() {
        try {
            RestoreNote.read(dataDirectory).ifPresent(note -> {
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("backup", note.backup());
                after.put("restoredAt", note.at().toString());
                audit.entry(Actor.cli(note.osUser()), "DATABASE_RESTORED").entity("database", "restore")
                        .summary("Restored the database from the backup " + note.backup())
                        .after(after).recordStandalone();
            });
            RestoreNote.remove(dataDirectory);
        } catch (Exception e) {
            // Never stop the app starting over its own paperwork; the note stays for the next start
            log.warn("Could not record the database restore in the audit log", e);
        }
    }
}
