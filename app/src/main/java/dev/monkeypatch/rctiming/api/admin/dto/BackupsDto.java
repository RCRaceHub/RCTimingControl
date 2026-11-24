package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.backup.BackupFile;
import java.util.List;

/** The backups answer: the absolute path of the backup directory and the backup files in it. */
public record BackupsDto(String directory, List<BackupFile> backups) {
}
