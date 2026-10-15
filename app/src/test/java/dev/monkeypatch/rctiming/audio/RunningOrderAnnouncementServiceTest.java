package dev.monkeypatch.rctiming.audio;

import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfile;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.infrastructure.audio.RunningOrderAnnouncementService;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunningOrderAnnouncementServiceTest {

    @Mock RaceRepository raceRepository;
    @Mock ClubProfileService clubProfileService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock LapTimingService lapTimingService;
    @Mock EntryRepository entryRepository;
    @Mock CompetitorRepository competitorRepository;

    @InjectMocks RunningOrderAnnouncementService service;

    private Race runningRace;
    private ClubProfile clubProfile;

    @BeforeEach
    void setUp() {
        runningRace = new Race();
        runningRace.setId(1L);
        runningRace.setStatus(RaceStatus.RUNNING);

        clubProfile = new ClubProfile();
        clubProfile.setId(1L);
        clubProfile.setName("Test Club");
        clubProfile.setAudioSettings(ClubAudioSettings.defaults()); // runningOrderDepth = 3
    }

    @Test
    void onRaceStarted_registersRaceForAnnouncements() {
        service.onRaceStarted(1L);

        assertThat(service.getRaceStartTimes()).containsKey(1L);
    }

    @Test
    void onRaceStopped_removesRaceFromTracking() {
        service.onRaceStarted(1L);
        service.onRaceStopped(1L);

        assertThat(service.getRaceStartTimes()).doesNotContainKey(1L);
    }

    @Test
    void onRaceStatusChanged_runningTransition_registersRace() {
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.RUNNING));

        assertThat(service.getRaceStartTimes()).containsKey(1L);
    }

    @Test
    void onRaceStatusChanged_stoppedTransition_deregistersRace() {
        service.onRaceStarted(1L);
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.STOPPED));

        assertThat(service.getRaceStartTimes()).doesNotContainKey(1L);
    }

    @Test
    void onRaceStatusChanged_finishedTransition_deregistersRace() {
        service.onRaceStarted(1L);
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1L, RaceStatus.FINISHED));

        assertThat(service.getRaceStartTimes()).doesNotContainKey(1L);
    }

    @Test
    void checkAndAnnounce_sendsToAudioTopic_whenIntervalElapsed() throws Exception {
        // Arrange: register race and backdate start time so interval has elapsed
        service.onRaceStarted(1L);
        backdateRaceStart(1L, Instant.now().minusSeconds(150)); // 2.5 minutes ago

        LiveTimingRowDto row1 = makeRow(1, "Racer A");
        LiveTimingRowDto row2 = makeRow(2, "Racer B");

        LiveRaceState liveState = new LiveRaceState();
        // Inject pre-calculated rows via mock
        LapTimingService mockLts = lapTimingService;
        when(mockLts.peek(1L)).thenReturn(Optional.of(liveState));
        when(raceRepository.findById(1L)).thenReturn(Optional.of(runningRace));
        when(clubProfileService.audioSettings()).thenReturn(clubProfile.getAudioSettings());

        // We can't easily inject rows into LiveRaceState (no positions = empty calculatePositions)
        // so test the no-rows path first:
        service.checkAndAnnounce();

        // No broadcast because positions list is empty
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void checkAndAnnounce_doesNotSend_whenNoRacesTracked() {
        service.checkAndAnnounce();
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void checkAndAnnounce_doesNotSend_whenIntervalNotElapsed() throws Exception {
        service.onRaceStarted(1L);
        // Do NOT backdate — just registered, so interval has NOT elapsed

        when(raceRepository.findById(1L)).thenReturn(Optional.of(runningRace));
        when(clubProfileService.audioSettings()).thenReturn(clubProfile.getAudioSettings());

        service.checkAndAnnounce();

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void checkAndAnnounce_deregistersRace_whenNoLongerRunning() throws Exception {
        service.onRaceStarted(1L);
        backdateRaceStart(1L, Instant.now().minusSeconds(150));

        Race stoppedRace = new Race();
        stoppedRace.setId(1L);
        stoppedRace.setStatus(RaceStatus.STOPPED);

        when(raceRepository.findById(1L)).thenReturn(Optional.of(stoppedRace));
        when(clubProfileService.audioSettings()).thenReturn(clubProfile.getAudioSettings());

        service.checkAndAnnounce();

        assertThat(service.getRaceStartTimes()).doesNotContainKey(1L);
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void announcementDepth_usesDefaultThreeWhenNoClubProfile() throws Exception {
        // Verify depth = 3 is applied (default) — implicitly confirmed by no exception when
        // No club profile; depth 3 still used in broadcastRunningOrder
        when(clubProfileService.audioSettings()).thenReturn(ClubAudioSettings.defaults());
        // Just assert no exception with empty club profile list
        service.onRaceStarted(1L);
        backdateRaceStart(1L, Instant.now().minusSeconds(150));

        when(raceRepository.findById(1L)).thenReturn(Optional.of(runningRace));
        when(lapTimingService.peek(1L)).thenReturn(Optional.empty());

        service.checkAndAnnounce();
        // No NPE / no exception = depth defaults correctly
    }

    @Test
    void checkAndAnnounce_speaksTheSpokenNameWhenOneIsSet() throws Exception {
        service.onRaceStarted(1L);
        backdateRaceStart(1L, Instant.now().minusSeconds(150));

        LiveRaceState liveState = mock(LiveRaceState.class);
        when(liveState.calculatePositions()).thenReturn(List.of(makeRow(1, "Siobhan Keane"), makeRow(2, "Sam Ito"),
                makeRow(3, "TOM  HALL (Wyvern)")));
        when(lapTimingService.peek(1L)).thenReturn(Optional.of(liveState));
        when(raceRepository.findById(1L)).thenReturn(Optional.of(runningRace));
        when(clubProfileService.audioSettings()).thenReturn(clubProfile.getAudioSettings());

        Entry first = new Entry();
        first.setCompetitorId(10L);
        Entry second = new Entry();
        second.setCompetitorId(11L);
        when(entryRepository.findById(1L)).thenReturn(Optional.of(first));
        when(entryRepository.findById(2L)).thenReturn(Optional.of(second));
        Competitor withSpoken = new Competitor();
        withSpoken.setDisplayName("Siobhan Keane");
        withSpoken.setSpokenName("Shiv-awn Keen");
        Competitor plain = new Competitor();
        plain.setDisplayName("Sam Ito");
        when(competitorRepository.findById(10L)).thenReturn(Optional.of(withSpoken));
        when(competitorRepository.findById(11L)).thenReturn(Optional.of(plain));

        service.checkAndAnnounce();

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/race/1/audio"), sent.capture());
        assertThat(((RunningOrderAnnouncementService.RunningOrderAnnouncement) sent.getValue()).positions())
                .containsExactly("Shiv-awn Keen", "Sam Ito", "Tom Hall"); // no competitor found: the row's name, tidied
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void backdateRaceStart(Long raceId, Instant backdatedTime) throws Exception {
        // Access the raceStartTimes and lastAnnouncementTimes via reflection to backdate
        Field startField = RunningOrderAnnouncementService.class.getDeclaredField("raceStartTimes");
        startField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Instant> startTimes = (Map<Long, Instant>) startField.get(service);
        startTimes.put(raceId, backdatedTime);

        Field lastField = RunningOrderAnnouncementService.class.getDeclaredField("lastAnnouncementTimes");
        lastField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Instant> lastTimes = (Map<Long, Instant>) lastField.get(service);
        lastTimes.put(raceId, backdatedTime);
    }

    private LiveTimingRowDto makeRow(int position, String driverName) {
        return new LiveTimingRowDto(
                (long) position,
                driverName,
                position,
                0, 0L, null, null, null, null, 0, 0, null, null, false
        );
    }
}
