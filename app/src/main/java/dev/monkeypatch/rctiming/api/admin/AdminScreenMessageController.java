package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.ScreenActionResultDto;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenBannerRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenFinalsUpDto;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenFinalsUpRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenIdsRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenMessageRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenMessageSettingsDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.screens.MessageSettingsService;
import dev.monkeypatch.rctiming.screens.ScreenActionResult;
import dev.monkeypatch.rctiming.screens.ScreenMessageService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Messages and banners on the screens (#716), under {@code /api/v1/admin/screens}: put up or clear a full-screen
 * message or a bottom banner on the screens listed, "Finals are up", and the saved presets and event hooks. Reads are
 * open to ADMIN, RACE_DIRECTOR and REFEREE; every send, clear and save is ADMIN or RACE_DIRECTOR and audited. A send
 * to several screens answers 200 with one result per screen, failures included (a screen that is off, refuses, or is
 * older than agent 0.6.0), so the page can offer to send again to just those.
 */
@RestController
@RequestMapping("/api/v1/admin/screens")
@RaceControlStaff
public class AdminScreenMessageController {

    private final ScreenMessageService messages;
    private final MessageSettingsService settings;

    public AdminScreenMessageController(ScreenMessageService messages, MessageSettingsService settings) {
        this.messages = messages;
        this.settings = settings;
    }

    /** Puts a full-screen message on screens. {@code POST /message}; ADMIN or RACE_DIRECTOR; 400; 404. Audited. */
    @Audited("audit_log")
    @PostMapping("/message")
    @DirectorOrAdmin
    public List<ScreenActionResultDto> sendMessage(@RequestBody @Valid ScreenMessageRequest request) {
        return results(messages.sendMessage(CurrentOfficial.actor(), request.screenIds(), request.message()));
    }

    /** Takes the message down on screens. {@code POST /message/clear}; ADMIN or RACE_DIRECTOR; 404. Audited. */
    @Audited("audit_log")
    @PostMapping("/message/clear")
    @DirectorOrAdmin
    public List<ScreenActionResultDto> clearMessage(@RequestBody @Valid ScreenIdsRequest request) {
        return results(messages.clearMessage(CurrentOfficial.actor(), request.screenIds()));
    }

    /** Puts a banner on screens. {@code POST /banner}; ADMIN or RACE_DIRECTOR; 400; 404. Audited. */
    @Audited("audit_log")
    @PostMapping("/banner")
    @DirectorOrAdmin
    public List<ScreenActionResultDto> sendBanner(@RequestBody @Valid ScreenBannerRequest request) {
        return results(messages.sendBanner(CurrentOfficial.actor(), request.screenIds(), request.banner()));
    }

    /** Takes the banner down on screens. {@code POST /banner/clear}; ADMIN or RACE_DIRECTOR; 404. Audited. */
    @Audited("audit_log")
    @PostMapping("/banner/clear")
    @DirectorOrAdmin
    public List<ScreenActionResultDto> clearBanner(@RequestBody @Valid ScreenIdsRequest request) {
        return results(messages.clearBanner(CurrentOfficial.actor(), request.screenIds()));
    }

    /**
     * Pushes the finals playlist and then announces it. {@code POST /finals-up}; ADMIN or RACE_DIRECTOR; 400 (nothing
     * is sent); 404. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/finals-up")
    @DirectorOrAdmin
    public ScreenFinalsUpDto finalsUp(@RequestBody @Valid ScreenFinalsUpRequest request) {
        return ScreenFinalsUpDto.from(messages.finalsUp(CurrentOfficial.actor(), request.playlistId(),
                request.screenIds(), request.message()));
    }

    /** The saved presets and hooks, or the defaults. {@code GET /message-settings}; read only. */
    @GetMapping("/message-settings")
    public ScreenMessageSettingsDto getSettings() {
        return ScreenMessageSettingsDto.from(settings.get());
    }

    /** Saves the presets and hooks. {@code PUT /message-settings}; ADMIN or RACE_DIRECTOR; 400; 404 for an unknown playlist. Audited. */
    @Audited("audit_log")
    @PutMapping("/message-settings")
    @DirectorOrAdmin
    public ScreenMessageSettingsDto saveSettings(@RequestBody @Valid ScreenMessageSettingsDto request) {
        return ScreenMessageSettingsDto.from(settings.save(CurrentOfficial.actor(), request.toSettings()));
    }

    private static List<ScreenActionResultDto> results(List<ScreenActionResult> results) {
        return results.stream().map(ScreenActionResultDto::from).toList();
    }
}
