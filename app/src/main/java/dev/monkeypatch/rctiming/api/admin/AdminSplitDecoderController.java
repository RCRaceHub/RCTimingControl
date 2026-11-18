package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.SplitDecoderDto;
import dev.monkeypatch.rctiming.api.admin.dto.SplitDecoderRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.timing.split.SplitDecoder;
import dev.monkeypatch.rctiming.timing.split.SplitDecoderService;
import dev.monkeypatch.rctiming.timing.split.SplitLoopListener;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The split-loop decoders (#672), under {@code /api/v1/admin/split-decoders}. ADMIN only: they are decoder settings,
 * like the main decoder's. Every write is audited and reconnects the split decoders once it commits. A split number
 * already in use answers 409; an unknown id 404.
 */
@RestController
@RequestMapping("/api/v1/admin/split-decoders")
@AdminOnly
public class AdminSplitDecoderController {

    private final SplitDecoderService service;
    private final ObjectProvider<SplitLoopListener> listener;

    public AdminSplitDecoderController(SplitDecoderService service, ObjectProvider<SplitLoopListener> listener) {
        this.service = service;
        this.listener = listener;
    }

    /** Every split decoder with its connection state. {@code GET /}. */
    @GetMapping
    public List<SplitDecoderDto> list() {
        return service.list().stream().map(this::toDto).toList();
    }

    /** Adds a split decoder. {@code POST /}; 400; 409. Audited. */
    @Audited("audit_log")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SplitDecoderDto add(@RequestBody @Valid SplitDecoderRequest request) {
        return toDto(service.add(CurrentOfficial.actor(), request.name(), request.host(), request.port(), request.splitNumber(),
                request.clockOffsetMs(), request.enabled()));
    }

    /** Changes a split decoder. {@code PUT /{id}}; 400; 404; 409. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    public SplitDecoderDto update(@PathVariable long id, @RequestBody @Valid SplitDecoderRequest request) {
        return toDto(service.update(CurrentOfficial.actor(), id, request.name(), request.host(), request.port(), request.splitNumber(),
                request.clockOffsetMs(), request.enabled()));
    }

    /** Removes a split decoder. {@code DELETE /{id}}; 404. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id) {
        service.remove(CurrentOfficial.actor(), id);
    }

    private SplitDecoderDto toDto(SplitDecoder decoder) {
        SplitLoopListener active = listener.getIfAvailable();
        return SplitDecoderDto.from(decoder, active == null ? "DISCONNECTED" : active.stateOf(decoder.id()));
    }
}
