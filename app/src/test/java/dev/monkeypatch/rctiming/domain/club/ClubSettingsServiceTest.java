package dev.monkeypatch.rctiming.domain.club;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClubSettingsServiceTest {

    private final ClubProfileService clubs = mock(ClubProfileService.class);
    private final AuditService audit = mock(AuditService.class, Answers.RETURNS_DEEP_STUBS);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private ClubSettingsService service;
    private ClubProfile profile;

    @BeforeEach
    void setUp() {
        service = new ClubSettingsService(clubs, mapper, audit,
                Validation.buildDefaultValidatorFactory().getValidator());
        profile = new ClubProfile();
        profile.setName("Wyvern RC Club");
        profile.setTimezone("Europe/London");
        when(clubs.getProfile()).thenReturn(Optional.of(profile));
        when(clubs.getDecoderSettings()).thenReturn(new DecoderSettings("192.168.1.9", 5100, "RC4"));
        when(clubs.listAffiliations()).thenReturn(List.of());
        when(clubs.privacyNotice()).thenReturn(
                new ClubProfileService.PrivacyNotice("Wyvern RC Club", null, null, "default text", false));
    }

    @Test
    void anExportReadsBackAsUnchanged() {
        ClubSettingsService.SettingsFile exported = service.export();

        assertThat(exported.format()).isEqualTo(ClubSettingsService.FORMAT);
        assertThat(service.preview(exported)).isNotEmpty().allMatch(section -> !section.changes());
    }

    @Test
    void anExportSurvivesBeingSavedAndReadBack() throws Exception {
        String json = mapper.writeValueAsString(service.export());

        ClubSettingsService.SettingsFile read = mapper.readValue(json, ClubSettingsService.SettingsFile.class);

        assertThat(service.preview(read)).isNotEmpty().allMatch(section -> !section.changes());
    }

    @Test
    void anInvalidSettingIsRefusedWithAMessageBeforeAnythingIsChanged() {
        ClubSettingsService.SettingsFile exported = service.export();
        ClubSettingsService.SettingsFile badDecoder = new ClubSettingsService.SettingsFile(
                exported.format(), exported.version(), null, null, null, null, null,
                new ClubSettingsService.Decoder("192.168.1.9", 0, "FOO"), null, null, null);
        ClubSettingsService.SettingsFile badTarget = new ClubSettingsService.SettingsFile(
                exported.format(), exported.version(), null, null, null, null, null, null, "SOMEWHERE", null, null);
        ClubSettingsService.SettingsFile badBody = new ClubSettingsService.SettingsFile(
                exported.format(), exported.version(), null, null, null, null, null, null, null, null,
                List.of(new ClubSettingsService.GoverningBody(null, "No code", false)));

        assertThatThrownBy(() -> service.apply(null, badDecoder)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> service.apply(null, badTarget)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SOMEWHERE");
        assertThatThrownBy(() -> service.apply(null, badBody)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
        verify(clubs, never()).updateDecoderConfig(any(), any(), any(), any());
    }

    @Test
    void aGoverningBodyListThatIsOnlyWhatIsAlreadyThereIsNotAChange() {
        GoverningBodyAffiliation brca = new GoverningBodyAffiliation();
        brca.setCode("BRCA");
        brca.setDisplayName("BRCA");
        brca.setMembershipRequired(true);
        when(clubs.listAffiliations()).thenReturn(List.of(brca));
        ClubSettingsService.SettingsFile file = new ClubSettingsService.SettingsFile(
                ClubSettingsService.FORMAT, ClubSettingsService.VERSION, null, null, null, null, null, null, null,
                null, List.of(new ClubSettingsService.GoverningBody("brca", "BRCA", true)));

        assertThat(service.preview(file)).singleElement().matches(section -> !section.changes());
    }

    @Test
    void aFileFromAnotherInstallShowsWhatWouldChange() {
        ClubSettingsService.SettingsFile exported = service.export();
        ClubSettingsService.SettingsFile other = new ClubSettingsService.SettingsFile(
                exported.format(), exported.version(), Instant.now(),
                new ClubProfileService.ClubDetails("Other Club", null, null, null, null, null, "UTC"),
                exported.audio(), exported.voiceId(), exported.liveTiming(), exported.decoder(),
                exported.resultsPublishTarget(), null, exported.governingBodies());

        List<ClubSettingsService.Section> sections = service.preview(other);

        assertThat(sections).filteredOn(ClubSettingsService.Section::changes)
                .extracting(ClubSettingsService.Section::name).containsExactly("Club details");
    }

    @Test
    void refusesAFileThatIsNotASettingsFileAndChangesNothing() {
        ClubSettingsService.SettingsFile wrong = new ClubSettingsService.SettingsFile(
                "something-else", 1, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.apply(null, wrong)).isInstanceOf(IllegalArgumentException.class);
        verify(clubs, never()).createOrUpdateProfile(any(), any());
    }

    @Test
    void refusesAFileFromANewerVersion() {
        ClubSettingsService.SettingsFile newer = new ClubSettingsService.SettingsFile(
                ClubSettingsService.FORMAT, 99, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.preview(newer))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version 99");
    }

    @Test
    void importAddsNewGoverningBodiesAndChangesExistingOnesByCode() {
        GoverningBodyAffiliation brca = new GoverningBodyAffiliation();
        brca.setId(7L);
        brca.setCode("BRCA");
        brca.setDisplayName("Old name");
        when(clubs.listAffiliations()).thenReturn(List.of(brca));
        ClubSettingsService.SettingsFile file = new ClubSettingsService.SettingsFile(
                ClubSettingsService.FORMAT, ClubSettingsService.VERSION, null, null, null, null, null, null, null,
                null, List.of(new ClubSettingsService.GoverningBody("brca", "BRCA", true),
                new ClubSettingsService.GoverningBody("NEW", "A new body", false)));

        service.apply(null, file);

        verify(clubs).updateAffiliation(any(), eq(7L), eq("brca"), eq("BRCA"), eq(true));
        verify(clubs).createAffiliation(any(), eq("NEW"), eq("A new body"), eq(false));
        verify(clubs, never()).createOrUpdateProfile(any(), any());
    }
}
