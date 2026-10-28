package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.media.DriverMediaService;
import dev.monkeypatch.rctiming.domain.media.DriverMediaService.CompetitorMediaView;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * The optional pictures and sponsors of drivers (#520), under {@code /api/v1/admin}: a competitor's headshot and
 * sponsors at {@code /competitors/{id}/media}, and an entry's car photo at {@code /entries/{id}/car-photo}. Reads
 * are open to ADMIN, RACE_DIRECTOR and REFEREE; every change is ADMIN or RACE_DIRECTOR and audited. A bad file or
 * list answers 400 and a missing competitor or entry 404.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RaceControlStaff
public class AdminDriverMediaController {

    private final DriverMediaService service;

    public AdminDriverMediaController(DriverMediaService service) {
        this.service = service;
    }

    /** The body of a sponsors change: up to five names. */
    public record SponsorsRequest(@Size(max = 5) List<String> names) {}

    /** The answer for a car photo: its address, null when the entry has none. */
    public record CarPhotoResponse(String url) {}

    /** A competitor's headshot and sponsors. {@code GET /competitors/{id}/media}; read only. */
    @GetMapping("/competitors/{id}/media")
    public CompetitorMediaView get(@PathVariable long id) {
        return service.forCompetitor(id);
    }

    /** Replaces the sponsors. {@code PUT /competitors/{id}/media/sponsors}; ADMIN or RACE_DIRECTOR; 400. Audited. */
    @Audited("audit_log")
    @PutMapping("/competitors/{id}/media/sponsors")
    @DirectorOrAdmin
    public CompetitorMediaView setSponsors(@PathVariable long id, @RequestBody @Valid SponsorsRequest request) {
        return service.setSponsors(CurrentOfficial.actor(), id, request.names() == null ? List.of() : request.names());
    }

    /**
     * Replaces the headshot. {@code PUT /competitors/{id}/media/headshot} as multipart form data with a {@code file}
     * part (PNG or JPEG up to 2 MB); ADMIN or RACE_DIRECTOR; 400. Audited.
     */
    @Audited("audit_log")
    @PutMapping(value = "/competitors/{id}/media/headshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @DirectorOrAdmin
    public CompetitorMediaView uploadHeadshot(@PathVariable long id,
                                              @RequestPart("file") MultipartFile file) {
        return service.uploadHeadshot(CurrentOfficial.actor(), id, file);
    }

    /** Removes the headshot. {@code DELETE /competitors/{id}/media/headshot}; ADMIN or RACE_DIRECTOR. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/competitors/{id}/media/headshot")
    @DirectorOrAdmin
    public CompetitorMediaView deleteHeadshot(@PathVariable long id) {
        return service.deleteHeadshot(CurrentOfficial.actor(), id);
    }

    /** An entry's car photo. {@code GET /entries/{id}/car-photo}; read only. */
    @GetMapping("/entries/{id}/car-photo")
    public CarPhotoResponse getCarPhoto(@PathVariable long id) {
        return new CarPhotoResponse(service.carPhoto(id));
    }

    /**
     * Replaces the car photo. {@code PUT /entries/{id}/car-photo} as multipart form data with a {@code file} part (PNG or
     * JPEG up to 2 MB); ADMIN or RACE_DIRECTOR; 400. Audited.
     */
    @Audited("audit_log")
    @PutMapping(value = "/entries/{id}/car-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @DirectorOrAdmin
    public CarPhotoResponse uploadCarPhoto(@PathVariable long id,
                                           @RequestPart("file") MultipartFile file) {
        return new CarPhotoResponse(service.uploadCarPhoto(CurrentOfficial.actor(), id, file));
    }

    /** Removes the car photo. {@code DELETE /entries/{id}/car-photo}; ADMIN or RACE_DIRECTOR; 204. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/entries/{id}/car-photo")
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCarPhoto(@PathVariable long id) {
        service.deleteCarPhoto(CurrentOfficial.actor(), id);
    }
}
