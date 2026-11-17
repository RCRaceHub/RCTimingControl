package dev.monkeypatch.rctiming.timing.split;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adds, changes and removes the split decoders (#672). Called by the admin controller on a request thread; each write
 * is one transaction on the write connection and publishes a {@link SplitDecodersChangedEvent}, which
 * {@link SplitLoopListener} handles after commit.
 */
@Service
public class SplitDecoderService {

    private final SplitDecoderRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService audit;

    public SplitDecoderService(SplitDecoderRepository repository, ApplicationEventPublisher eventPublisher,
                               AuditService audit) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.audit = audit;
    }

    /** Every split decoder, in split order. */
    @Transactional(readOnly = true)
    public List<SplitDecoder> list() {
        return repository.findAll();
    }

    /**
     * Adds a split decoder.
     *
     * @throws StateConflictException if another decoder already has this split number (HTTP 409)
     */
    @Transactional
    public SplitDecoder add(Actor actor, String name, String host, int port, int splitNumber, long clockOffsetMs, boolean enabled) {
        requireFreeSplitNumber(splitNumber, null);
        SplitDecoder saved = repository.insert(
                new SplitDecoder(null, name.trim(), host.trim(), port, splitNumber, clockOffsetMs, enabled));
        audit.entry(actor, "SPLIT_DECODER_ADDED").entity("split_decoders", saved.id())
                .summary("Added split decoder " + splitNumber + " (" + saved.name() + ") at " + saved.host() + ":" + port)
                .record();
        eventPublisher.publishEvent(new SplitDecodersChangedEvent());
        return saved;
    }

    /**
     * Replaces a split decoder's settings.
     *
     * @throws EntityNotFoundException if there is no such decoder (HTTP 404)
     * @throws StateConflictException  if another decoder already has this split number (HTTP 409)
     */
    @Transactional
    public SplitDecoder update(Actor actor, long id, String name, String host, int port, int splitNumber, long clockOffsetMs,
                               boolean enabled) {
        repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Split decoder " + id + " not found"));
        requireFreeSplitNumber(splitNumber, id);
        repository.update(new SplitDecoder(id, name.trim(), host.trim(), port, splitNumber, clockOffsetMs, enabled));
        audit.entry(actor, "SPLIT_DECODER_CHANGED").entity("split_decoders", id)
                .summary("Changed split decoder " + splitNumber + " (" + name.trim() + "), offset " + clockOffsetMs + " ms")
                .record();
        eventPublisher.publishEvent(new SplitDecodersChangedEvent());
        return repository.findById(id).orElseThrow();
    }

    /** Removes a split decoder. @throws EntityNotFoundException if there is no such decoder (HTTP 404) */
    @Transactional
    public void remove(Actor actor, long id) {
        SplitDecoder gone = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Split decoder " + id + " not found"));
        repository.deleteById(id);
        audit.entry(actor, "SPLIT_DECODER_REMOVED").entity("split_decoders", id)
                .summary("Removed split decoder " + gone.splitNumber() + " (" + gone.name() + ")").record();
        eventPublisher.publishEvent(new SplitDecodersChangedEvent());
    }

    private void requireFreeSplitNumber(int splitNumber, Long exceptId) {
        if (repository.splitNumberTaken(splitNumber, exceptId)) {
            throw new StateConflictException("Split " + splitNumber + " is already used by another decoder");
        }
    }
}
