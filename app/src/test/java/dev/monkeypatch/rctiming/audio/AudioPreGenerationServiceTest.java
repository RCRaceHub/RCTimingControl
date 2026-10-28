package dev.monkeypatch.rctiming.audio;

import dev.monkeypatch.rctiming.domain.club.AudioSettingsChangedEvent;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.infrastructure.tts.AudioPreGenerationService;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsClipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioPreGenerationServiceTest {

    @Mock TtsClipService clipService;
    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock EntryRepository entryRepository;
    @Mock CompetitorRepository competitorRepository;
    @Mock ClubProfileService clubProfileService;

    @InjectMocks AudioPreGenerationService service;

    private Race race;
    private ClubProfile clubProfile;

    @BeforeEach
    void setUp() {
        race = new Race();
        race.setId(1L);
        race.setHeatNumber(3);

        clubProfile = new ClubProfile();
        clubProfile.setId(1L);
        clubProfile.setName("Test Club");
        clubProfile.setDefaultVoiceId("en_GB-alan-medium");
        clubProfile.setAudioSettings(ClubAudioSettings.defaults());
        lenient().when(clubProfileService.audioSettings()).thenReturn(ClubAudioSettings.defaults());
    }

    @Test
    void onRaceGridTransition_generatesCountdownClips() {
        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(Collections.emptyList());
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(clipService.generateCountdownClip(anyLong(), anyInt(), anyString(), anyString()))
                .thenReturn("http://localhost:8080/storage/clip.wav");

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));

        // 5 countdown intervals: 600, 300, 120, 60, 30
        verify(clipService, atLeast(5)).generateCountdownClip(eq(1L), anyInt(), anyString(), anyString());
        verify(clipService).generateCountdownClip(eq(1L), eq(600), anyString(), anyString());
        verify(clipService).generateCountdownClip(eq(1L), eq(30), anyString(), anyString());
    }

    @Test
    void onRaceGridTransition_generatesGridCallClipsKeyedByEntryId() {
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setId(10L);
        raceEntry.setRaceId(1L);
        raceEntry.setEntryId(100L);
        raceEntry.setGridPosition(1);

        Entry entry = new Entry();
        entry.setId(100L);
        entry.setCompetitorId(200L);

        Competitor competitor = new Competitor();
        competitor.setId(200L);
        competitor.setDisplayName("Alan Smith");

        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(List.of(raceEntry));
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(entryRepository.findById(100L)).thenReturn(Optional.of(entry));
        when(competitorRepository.findById(200L)).thenReturn(Optional.of(competitor));
        when(clipService.generateCountdownClip(anyLong(), anyInt(), anyString(), anyString())).thenReturn(null);
        when(clipService.generateRaceFinishedClip(anyLong(), anyString(), anyString())).thenReturn(null);
        when(clipService.generateGridCallClip(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn("http://localhost:8080/storage/grid.wav");

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));

        verify(clipService).generateGridCallClip(eq(1L), eq(100L), eq("Alan Smith."), anyString());
        assertThat(service.getClipMap(1L)).containsKey("grid-100");
    }

    @Test
    void onRaceGridTransition_gridCallSaysTheSpokenNameWhenOneIsSet() {
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setId(10L);
        raceEntry.setRaceId(1L);
        raceEntry.setEntryId(100L);
        raceEntry.setGridPosition(1);

        Entry entry = new Entry();
        entry.setId(100L);
        entry.setCompetitorId(200L);

        Competitor competitor = new Competitor();
        competitor.setId(200L);
        competitor.setDisplayName("Siobhan Keane");
        competitor.setSpokenName("Shiv-awn Keen");

        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(List.of(raceEntry));
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(entryRepository.findById(100L)).thenReturn(Optional.of(entry));
        when(competitorRepository.findById(200L)).thenReturn(Optional.of(competitor));
        when(clipService.generateCountdownClip(anyLong(), anyInt(), anyString(), anyString())).thenReturn(null);
        when(clipService.generateRaceFinishedClip(anyLong(), anyString(), anyString())).thenReturn(null);
        when(clipService.generateGridCallClip(anyLong(), anyLong(), anyString(), anyString())).thenReturn(null);

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));

        verify(clipService).generateGridCallClip(eq(1L), eq(100L), eq("Shiv-awn Keen."), anyString());
    }

    @Test
    void onRaceGridTransition_gridCallTidiesADisplayNameWithNoSpokenName() {
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setId(10L);
        raceEntry.setRaceId(1L);
        raceEntry.setEntryId(100L);
        raceEntry.setGridPosition(1);

        Entry entry = new Entry();
        entry.setId(100L);
        entry.setCompetitorId(200L);

        Competitor competitor = new Competitor();
        competitor.setId(200L);
        competitor.setDisplayName("ALEX ROWE (Wyvern)");

        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(List.of(raceEntry));
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(entryRepository.findById(100L)).thenReturn(Optional.of(entry));
        when(competitorRepository.findById(200L)).thenReturn(Optional.of(competitor));
        when(clipService.generateCountdownClip(anyLong(), anyInt(), anyString(), anyString())).thenReturn(null);
        when(clipService.generateRaceFinishedClip(anyLong(), anyString(), anyString())).thenReturn(null);
        when(clipService.generateGridCallClip(anyLong(), anyLong(), anyString(), anyString())).thenReturn(null);

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));

        verify(clipService).generateGridCallClip(eq(1L), eq(100L), eq("Alex Rowe."), anyString());
    }

    @Test
    void onRaceGridTransition_skipsGridCallWhenEntryHasNoCompetitor() {
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setId(10L);
        raceEntry.setRaceId(1L);
        raceEntry.setEntryId(100L);
        raceEntry.setGridPosition(1);

        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(List.of(raceEntry));
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(entryRepository.findById(100L)).thenReturn(Optional.empty());
        when(clipService.generateCountdownClip(anyLong(), anyInt(), anyString(), anyString())).thenReturn(null);
        when(clipService.generateRaceFinishedClip(anyLong(), anyString(), anyString())).thenReturn(null);

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));

        verify(clipService, never()).generateGridCallClip(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void onRaceGridTransition_generatesOneRaceFinishedClip() {
        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(Collections.emptyList());
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(clipService.generateCountdownClip(anyLong(), anyInt(), anyString(), anyString())).thenReturn(null);
        when(clipService.generateRaceFinishedClip(anyLong(), anyString(), anyString()))
                .thenReturn("http://localhost:8080/storage/finish.wav");

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));

        verify(clipService).generateRaceFinishedClip(eq(1L), eq("Race finished. Checkered flag."), anyString());
        assertThat(service.getClipMap(1L)).containsKey("finish");
    }

    @Test
    void getClipMap_returnsAllGeneratedUrls() {
        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(Collections.emptyList());
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(clipService.generateCountdownClip(anyLong(), anyInt(), anyString(), anyString()))
                .thenReturn("http://localhost:8080/storage/clip.wav");

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));

        Map<String, String> clips = service.getClipMap(1L);
        assertThat(clips).isNotEmpty();
        assertThat(clips).containsKey("countdown-600");
        assertThat(clips).containsKey("countdown-30");
    }

    @Test
    void getClipMap_returnsEmptyMapBeforeGeneration() {
        Map<String, String> clips = service.getClipMap(99L);
        assertThat(clips).isEmpty();
    }

    @Test
    void nonGridTransition_doesNotGenerateClips() {
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.RUNNING));

        verify(clipService, never()).generateCountdownClip(anyLong(), anyInt(), anyString(), anyString());
        verify(clipService, never()).generateGridCallClip(anyLong(), anyLong(), anyString(), anyString());
        verify(clipService, never()).generateRaceFinishedClip(anyLong(), anyString(), anyString());
    }

    @Test
    void raceNotFound_logsWarningAndDoesNotThrow() {
        when(raceRepository.findById(99L)).thenReturn(Optional.empty());

        // Should not throw
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 99L, RaceStatus.GRID));

        verify(clipService, never()).generateCountdownClip(anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void onRaceGridTransition_makesCountdownClipsForTheClubsOwnIntervals() {
        when(clubProfileService.audioSettings()).thenReturn(new ClubAudioSettings(
                true, true, true, true, true, 3, new int[]{900, 45}));
        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(Collections.emptyList());
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(clipService.generateCountdownClip(anyLong(), anyInt(), anyString(), anyString()))
                .thenReturn("http://localhost:8080/storage/clip.wav");

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));

        assertThat(service.getClipMap(1L)).containsKeys("countdown-900", "countdown-45")
                .doesNotContainKeys("countdown-600", "countdown-30");
    }

    @Test
    void getClipMap_makesTheClipsAgainForARaceOnTheGridThatLostThem() throws InterruptedException {
        race.setStatus(RaceStatus.GRID);
        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(Collections.emptyList());
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(clipService.generateCountdownClip(anyLong(), anyInt(), anyString(), anyString()))
                .thenReturn("http://localhost:8080/storage/clip.wav");

        // The first ask starts the work, which may finish before it returns; the clips are there a moment later
        service.getClipMap(1L);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(service.getClipMap(1L)).containsKey("countdown-600"));
    }

    @Test
    void getClipMap_leavesARaceThatIsNotOnTheGridAlone() {
        race.setStatus(RaceStatus.PENDING);
        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));

        assertThat(service.getClipMap(1L)).isEmpty();

        verifyNoInteractions(clipService);
    }

    @Test
    void onRaceGridTransition_saysTheClubsWordingForTheFinishAndTheCarFinishCalls() {
        ClubAudioSettings own = new ClubAudioSettings(true, true, true, true, true, 3, null, null, null, null, null,
                null, null, null, null, null, null,
                new ClubAudioSettings.Wording(null, null, null, null, null, null, "That is the race.", null, null,
                        null, "{name} home in car {car}."),
                null);
        when(clubProfileService.audioSettings()).thenReturn(own);
        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setEntryId(100L);
        raceEntry.setCarNumber(12);
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(List.of(raceEntry));
        Entry entry = new Entry();
        entry.setId(100L);
        entry.setCompetitorId(200L);
        when(entryRepository.findById(100L)).thenReturn(Optional.of(entry));
        Competitor competitor = new Competitor();
        competitor.setDisplayName("Alan Smith");
        when(competitorRepository.findById(200L)).thenReturn(Optional.of(competitor));
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(clipService.generateCarFinishClip(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn("http://localhost:8080/storage/car-finish.wav");
        when(clipService.generateRaceFinishedClip(anyLong(), anyString(), anyString())).thenReturn(null);

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));

        verify(clipService).generateRaceFinishedClip(eq(1L), eq("That is the race."), anyString());
        verify(clipService).generateCarFinishClip(eq(1L), eq(100L), eq("Alan Smith home in car 12."), anyString());
        assertThat(service.getClipMap(1L)).containsKey("car-finish-100");
    }

    @Test
    void whenTheClubChangesItsSettings_theClipsOfARaceOnTheGridAreMadeAgainInTheNewWording() {
        race.setStatus(RaceStatus.GRID);
        when(raceRepository.findById(1L)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(Collections.emptyList());
        when(clubProfileService.defaultVoiceId()).thenReturn(Optional.of(clubProfile.getDefaultVoiceId()));
        when(clipService.generateRaceFinishedClip(anyLong(), anyString(), anyString()))
                .thenReturn("http://localhost:8080/storage/finish.wav");
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.GRID));
        verify(clipService).generateRaceFinishedClip(eq(1L), eq("Race finished. Checkered flag."), anyString());

        when(clubProfileService.audioSettings()).thenReturn(new ClubAudioSettings(true, true, true, true, true, 3,
                null, null, null, null, null, null, null, null, null, null, null,
                new ClubAudioSettings.Wording(null, null, null, null, null, null, "All done.", null, null, null,
                        null),
                null));
        service.onAudioSettingsChanged(new AudioSettingsChangedEvent());

        verify(clipService).generateRaceFinishedClip(eq(1L), eq("All done."), anyString());
    }
}
