package dev.monkeypatch.rctiming.domain.setup;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplateRepository;
import dev.monkeypatch.rctiming.domain.track.TrackRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.domain.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetupServiceTest {

    @Mock
    private ClubProfileRepository clubProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private TrackRepository trackRepository;
    @Mock
    private RaceFormatTemplateRepository raceFormatTemplateRepository;
    private SetupService setupService;

    @BeforeEach
    void setUp() {
        setupService = new SetupService(
                clubProfileRepository, userRepository, userService, trackRepository, raceFormatTemplateRepository);
    }

    @Test
    void bootstrap_throws_whenAnyUserExists() {
        when(userRepository.count()).thenReturn(1L);
        assertThrows(StateConflictException.class,
                () -> setupService.bootstrap("admin@test.com", "password123", "First", "Last"));
    }

    @Test
    void bootstrap_assignsAdminRole_notRacerRole() {
        when(userRepository.count()).thenReturn(0L);
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@test.com");
        adminUser.setFirstName("First");
        adminUser.setLastName("Last");
        adminUser.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        adminUser.setCreatedAt(now);
        adminUser.setUpdatedAt(now);
        when(userService.createAdmin("admin@test.com", "password123", "First", "Last")).thenReturn(adminUser);

        User admin = setupService.bootstrap("admin@test.com", "password123", "First", "Last");

        assertThat(admin.getRoles()).containsExactly(Role.ADMIN);
    }

    @Test
    void getProgress_returnsAllFalse_onEmptyDb() {
        when(clubProfileRepository.count()).thenReturn(0L);
        when(trackRepository.count()).thenReturn(0L);
        when(raceFormatTemplateRepository.count()).thenReturn(0L);
        when(userRepository.countOfficials()).thenReturn(0L);
        when(clubProfileRepository.findCurrent()).thenReturn(Optional.empty());

        SetupService.Progress result = setupService.getProgress();

        assertThat(result.club()).isFalse();
        assertThat(result.track()).isFalse();
        assertThat(result.format()).isFalse();
        assertThat(result.staff()).isFalse();
        assertThat(result.decoder()).isFalse();
    }

    @Test
    void getProgress_returnsClubTrue_whenClubProfileSaved() {
        when(clubProfileRepository.count()).thenReturn(1L);
        when(trackRepository.count()).thenReturn(0L);
        when(raceFormatTemplateRepository.count()).thenReturn(0L);
        when(userRepository.countOfficials()).thenReturn(0L);
        // Club profile without decoder fields — decoder=false
        ClubProfile profile = new ClubProfile();
        when(clubProfileRepository.findCurrent()).thenReturn(Optional.of(profile));

        SetupService.Progress result = setupService.getProgress();

        assertThat(result.club()).isTrue();
        assertThat(result.decoder()).isFalse();
    }
}
