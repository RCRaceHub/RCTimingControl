package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.ModuleDto;
import dev.monkeypatch.rctiming.api.admin.dto.SetEventModuleRequest;
import dev.monkeypatch.rctiming.api.admin.dto.SetModuleRequest;
import dev.monkeypatch.rctiming.modules.ModuleSwitch;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Feature switches (#797): which built-in modules the club has on, and which an event has turned off. Reads are open
 * to any official, because the menu hides the screens of a module that is off; the club-wide switch is ADMIN only
 * and an event's switch is RACE_DIRECTOR or ADMIN. Every change is audited by {@link ModuleSwitch}.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class ModulesController {

    private final ModuleSwitch moduleSwitch;

    public ModulesController(ModuleSwitch moduleSwitch) {
        this.moduleSwitch = moduleSwitch;
    }

    /** Every module with the club's switch. {@code GET /modules}; any official. */
    @GetMapping("/modules")
    public List<ModuleDto> list() {
        return moduleSwitch.status().stream().map(ModuleDto::from).toList();
    }

    /** Switches a module on or off for the club. {@code PUT /modules/{id}}; ADMIN; 204; 404 for an unknown module. */
    @PutMapping("/modules/{id}")
    @AdminOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@PathVariable String id, @Valid @RequestBody SetModuleRequest request) {
        moduleSwitch.setEnabled(id, request.enabled(), CurrentOfficial.actor());
    }

    /** Every module as one event sees it. {@code GET /events/{eventId}/modules}; any official; 404 for an unknown event. */
    @GetMapping("/events/{eventId}/modules")
    public List<ModuleDto> listForEvent(@PathVariable long eventId) {
        return moduleSwitch.status(eventId).stream().map(ModuleDto::from).toList();
    }

    /**
     * Turns a module off for one event, or back on for it. {@code PUT /events/{eventId}/modules/{id}};
     * RACE_DIRECTOR or ADMIN; 204; 404 for an unknown event or module. Never turns on a module the club has off.
     */
    @PutMapping("/events/{eventId}/modules/{id}")
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setForEvent(@PathVariable long eventId, @PathVariable String id,
                            @Valid @RequestBody SetEventModuleRequest request) {
        moduleSwitch.setEventOff(eventId, id, request.off(), CurrentOfficial.actor());
    }
}
