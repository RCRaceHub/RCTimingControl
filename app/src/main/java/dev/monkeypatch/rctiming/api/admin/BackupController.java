package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.backup.BackupFile;
import dev.monkeypatch.rctiming.backup.BackupService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Database backups for the admin page (#22): list them, and take one now. Under {@code /api/v1/admin/backups};
 * ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/admin/backups")
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final BackupService backupService;
    private final AuditService audit;

    public BackupController(BackupService backupService, AuditService audit) {
        this.backupService = backupService;
        this.audit = audit;
    }

    /** The backups answer: the absolute path of the backup directory and the backup files in it. */
    public record BackupsDto(String directory, List<BackupFile> backups) {
    }

    /** The backups on disk and where they are kept. {@code GET}; read only. */
    @GetMapping
    public BackupsDto list() {
        return new BackupsDto(backupService.directory().toAbsolutePath().toString(), backupService.list());
    }

    /**
     * A backup changes no data, so its row is written on its own after the file exists. Only the ones an official
     * asks for are recorded; the nightly and end-of-day backups name nobody. {@code POST}; answers 201 with the
     * file; 500 if the backup fails.
     */
    @Audited("audit_log")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BackupFile backupNow(Authentication auth) {
        BackupFile file = backupService.backup("manual");
        audit.entry(CurrentOfficial.actor(auth), "BACKUP_TAKEN").entity("backup", file.name())
                .summary("Took a backup now: " + file.name())
                .after(file).recordStandalone();
        return file;
    }
}
