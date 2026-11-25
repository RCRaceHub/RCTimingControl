package dev.monkeypatch.rctiming.api.resultsexport.dto;

/**
 * Answer to a results re-upload: the queued export, which the sender takes to RaceHub on its next pass.
 *
 * @param exportId the outbox row, as listed under Results to RaceHub
 * @param revision the revision RaceHub will see
 */
public record ReuploadResponse(long exportId, long revision) {}
