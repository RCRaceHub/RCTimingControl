package dev.monkeypatch.rctiming.domain.club;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.resultsexport.ResultsPublishTarget;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Exports the club's own configuration as one small file and reads it back on another install (#510), so a club can
 * back its settings up on their own or set up a second laptop the same way. It works through
 * {@link ClubProfileService}, so every setting is checked and audited exactly as when an admin saves it on the Club
 * page.
 *
 * <p>The file holds the club's details, the announcer settings and default voice, the live timing thresholds, the
 * decoder's address, where results are published, the privacy notice and the governing bodies. It does not hold race
 * history, officials or their passwords, the sign-in key, the logo or uploaded horn sounds (files, not settings), or the
 * RaceHub address, which can carry a secret. Importing replaces what the file has and leaves the rest alone: governing
 * bodies are matched by code and added or changed, never removed.
 *
 * <p>Called from request threads. {@link #apply} runs in one transaction, so a file that fails halfway changes nothing.
 */
@Service
@Transactional
public class ClubSettingsService {

    /** The {@code format} every settings file carries. */
    public static final String FORMAT = "rctc-club-settings";
    /** The file layout this version reads and writes. */
    public static final int VERSION = 1;

    /** The decoder's address, as it is in the club profile. */
    public record Decoder(String host, Integer port, String protocol) {}

    /** A governing body the club is affiliated to. */
    public record GoverningBody(String code, String displayName, boolean membershipRequired) {}

    /**
     * The settings file. Any section may be missing from a file (null or empty) and is then left as it is on import.
     * {@code audio} never carries horn sound files.
     */
    public record SettingsFile(String format, int version, Instant exportedAt,
                               ClubProfileService.ClubDetails club, ClubAudioSettings audio, String voiceId,
                               LiveTimingSettings liveTiming, Decoder decoder, String resultsPublishTarget,
                               String privacyNotice, List<GoverningBody> governingBodies) {}

    /** One part of a file, for the preview: what it is, a short description and whether importing would change it. */
    public record Section(String name, String summary, boolean changes) {}

    private final ClubProfileService clubs;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Validator validator;

    public ClubSettingsService(ClubProfileService clubs, ObjectMapper mapper, AuditService audit,
                               Validator validator) {
        this.clubs = clubs;
        this.mapper = mapper;
        this.audit = audit;
        this.validator = validator;
    }

    /** This install's settings as a file. Read only. */
    @ReadTransaction
    public SettingsFile export() {
        ClubProfile profile = clubs.getProfile().orElseGet(ClubProfile::new);
        // Before the setup wizard has saved the club there are no details to export
        ClubProfileService.ClubDetails club = clubs.getProfile().isEmpty() || profile.getName() == null
                || profile.getName().isBlank() ? null
                : new ClubProfileService.ClubDetails(profile.getName(), profile.getEmail(), profile.getPhone(),
                profile.getWebsiteUrl(), profile.getLatitude(), profile.getLongitude(), profile.getTimezone());
        ClubAudioSettings audio = profile.getAudioSettings().withHornSounds(null);
        DecoderSettings decoder = clubs.getDecoderSettings();
        List<GoverningBody> bodies = clubs.listAffiliations().stream()
                .map(a -> new GoverningBody(a.getCode(), a.getDisplayName(), a.isMembershipRequired())).toList();
        return new SettingsFile(FORMAT, VERSION, Instant.now(), club, audio, profile.getDefaultVoiceId(),
                profile.getLiveTimingSettings(), new Decoder(decoder.host(), decoder.port(), decoder.protocol()),
                profile.getResultsPublishTarget(),
                clubs.privacyNotice().customised() ? clubs.privacyNotice().text() : null, bodies);
    }

    /**
     * What importing {@code file} would do, section by section, without changing anything.
     *
     * @throws IllegalArgumentException if the file is not a settings file this version can read (answered 400)
     */
    @ReadTransaction
    public List<Section> preview(SettingsFile file) {
        check(file);
        SettingsFile current = export();
        List<Section> sections = new ArrayList<>();
        if (file.club() != null) {
            sections.add(section("Club details", file.club().name(), file.club(), current.club()));
        }
        if (file.audio() != null) {
            // The sound files are this install's own uploads and are never imported, so they are not compared
            sections.add(section("Announcer settings", "Countdown, calls, beeps and wording",
                    file.audio().withHornSounds(null), current.audio()));
        }
        if (file.voiceId() != null) {
            sections.add(section("Default voice", file.voiceId(), file.voiceId(), current.voiceId()));
        }
        if (file.liveTiming() != null) {
            sections.add(section("Live timing thresholds", "Battle gaps and the close tint", file.liveTiming(),
                    current.liveTiming()));
        }
        if (file.decoder() != null && file.decoder().host() != null) {
            sections.add(section("Decoder address", file.decoder().host() + ":" + file.decoder().port(),
                    file.decoder(), current.decoder()));
        }
        if (file.resultsPublishTarget() != null && !file.resultsPublishTarget().isBlank()) {
            sections.add(section("Where results are published", file.resultsPublishTarget(),
                    file.resultsPublishTarget(), current.resultsPublishTarget()));
        }
        if (file.privacyNotice() != null) {
            sections.add(section("Privacy notice", "The club's own text", file.privacyNotice(),
                    current.privacyNotice()));
        }
        if (file.governingBodies() != null && !file.governingBodies().isEmpty()) {
            // Importing adds or changes bodies by code and never removes one, so only those count as a change
            List<GoverningBody> differing = file.governingBodies().stream()
                    .filter(body -> current.governingBodies().stream().noneMatch(
                            now -> now.code().equalsIgnoreCase(body.code())
                                    && Objects.equals(now.displayName(), body.displayName())
                                    && now.membershipRequired() == body.membershipRequired()))
                    .toList();
            sections.add(new Section("Governing bodies", file.governingBodies().size() + " in the file",
                    !differing.isEmpty()));
        }
        return sections;
    }

    /**
     * Applies the sections {@code file} has. Each goes through the same service call as the Club page, so each is
     * validated and audited, and one that is invalid (a time zone that does not exist, a threshold out of range) fails
     * the whole import with nothing changed.
     *
     * @return what was in the file, as {@link #preview} gave it
     * @throws IllegalArgumentException if the file is not readable, or a setting in it is invalid (answered 400)
     */
    public List<Section> apply(Actor actor, SettingsFile file) {
        List<Section> sections = preview(file);
        if (file.club() != null) {
            clubs.createOrUpdateProfile(actor, file.club());
        }
        if (file.audio() != null || file.voiceId() != null) {
            clubs.changeAudioSettings(actor, profile -> {
                if (file.audio() != null) {
                    // The sound files are this install's own uploads, so they stay as they are
                    profile.setAudioSettings(file.audio().withHornSounds(profile.getAudioSettings().hornSounds()));
                }
                if (file.voiceId() != null) {
                    profile.setDefaultVoiceId(file.voiceId());
                }
            });
        }
        if (file.liveTiming() != null) {
            clubs.changeLiveTimingSettings(actor, file.liveTiming());
        }
        if (file.decoder() != null && file.decoder().host() != null) {
            clubs.getSingletonProfileId();   // a file with a decoder but no club section on a fresh install
            clubs.updateDecoderConfig(actor, file.decoder().host(), file.decoder().port(), file.decoder().protocol());
        }
        if (file.resultsPublishTarget() != null && !file.resultsPublishTarget().isBlank()) {
            clubs.updateResultsPublishTarget(actor, file.resultsPublishTarget());
        }
        if (file.privacyNotice() != null) {
            clubs.changePrivacyNotice(actor, file.privacyNotice());
        }
        if (file.governingBodies() != null) {
            applyGoverningBodies(actor, file.governingBodies());
        }
        audit.entry(actor, "CLUB_SETTINGS_IMPORTED").entity("club_settings", "import")
                .summary("Imported a club settings file: " + sections.stream().filter(Section::changes)
                        .map(Section::name).toList())
                .record();
        return sections;
    }

    private void applyGoverningBodies(Actor actor, List<GoverningBody> bodies) {
        for (GoverningBody body : bodies) {
            Optional<GoverningBodyAffiliation> existing = clubs.listAffiliations().stream()
                    .filter(a -> a.getCode().equalsIgnoreCase(body.code())).findFirst();
            if (existing.isPresent()) {
                clubs.updateAffiliation(actor, existing.get().getId(), body.code(), body.displayName(),
                        body.membershipRequired());
            } else {
                clubs.createAffiliation(actor, body.code(), body.displayName(), body.membershipRequired());
            }
        }
    }

    private void check(SettingsFile file) {
        if (file == null || !FORMAT.equals(file.format())) {
            throw new IllegalArgumentException("This is not an RC Timing settings file");
        }
        if (file.version() != VERSION) {
            throw new IllegalArgumentException("This settings file is version " + file.version()
                    + "; this version of RC Timing reads version " + VERSION);
        }
        validate(file);
    }

    /**
     * The checks the Club page and the setup wizard make on the same values, which a file would otherwise skip because
     * it arrives as one body. Every message is shown to the admin.
     */
    private void validate(SettingsFile file) {
        if (file.club() != null) {
            ClubProfileService.ClubDetails club = file.club();
            require(club.name() != null && !club.name().isBlank(), "The club name is missing");
            require(club.timezone() != null && !club.timezone().isBlank(), "The club's time zone is missing");
        }
        if (file.audio() != null) {
            ClubAudioSettings audio = file.audio();
            require(audio.runningOrderPercent() >= 1 && audio.runningOrderPercent() <= 100,
                    "The running order share must be from 1 to 100");
            require(audio.startCountdownSeconds() >= 0 && audio.startCountdownSeconds() <= 60,
                    "The start countdown must be from 0 to 60 seconds");
            require(audio.countdownIntervals().length <= 10, "There can be at most 10 countdown calls");
            for (int seconds : audio.countdownIntervals()) {
                require(seconds >= 0, "A countdown call cannot be negative");
            }
            Set<ConstraintViolation<ClubAudioSettings.Wording>> wording = validator.validate(audio.wording());
            require(wording.isEmpty(), "The announcer wording is too long");
            Set<ConstraintViolation<ClubAudioSettings.Tuning>> tuning = validator.validate(audio.tuning());
            require(tuning.isEmpty(), "The announcer tuning is out of range");
        }
        if (file.liveTiming() != null) {
            file.liveTiming().validated();
        }
        if (file.decoder() != null && file.decoder().host() != null) {
            Decoder decoder = file.decoder();
            require(!decoder.host().isBlank() && decoder.host().length() <= 255, "The decoder address is not valid");
            require(decoder.port() != null && decoder.port() >= 1 && decoder.port() <= 65535,
                    "The decoder port must be from 1 to 65535");
            require("RC4".equals(decoder.protocol()) || "P3".equals(decoder.protocol()),
                    "The decoder protocol must be RC4 or P3");
        }
        if (file.resultsPublishTarget() != null && !file.resultsPublishTarget().isBlank()) {
            try {
                ResultsPublishTarget.valueOf(file.resultsPublishTarget());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Results cannot be published to " + file.resultsPublishTarget());
            }
        }
        if (file.privacyNotice() != null) {
            require(file.privacyNotice().length() <= 20_000, "The privacy notice is too long");
        }
        if (file.governingBodies() != null) {
            require(file.governingBodies().size() <= 50, "There are too many governing bodies");
            for (GoverningBody body : file.governingBodies()) {
                require(body.code() != null && !body.code().isBlank() && body.code().length() <= 50,
                        "A governing body needs a code of up to 50 characters");
                require(body.displayName() != null && !body.displayName().isBlank() && body.displayName().length() <= 255,
                        "A governing body needs a name of up to 255 characters");
            }
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Compares as JSON, because the announcer settings hold an array, which a record does not compare by content. */
    private Section section(String name, String summary, Object incoming, Object current) {
        JsonNode a = mapper.valueToTree(incoming);
        JsonNode b = mapper.valueToTree(current);
        return new Section(name, summary == null ? "" : summary, !Objects.equals(a, b));
    }
}
