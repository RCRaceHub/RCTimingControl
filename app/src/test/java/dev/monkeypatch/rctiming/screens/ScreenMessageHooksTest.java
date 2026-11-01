package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.query.racecontrol.FinalsReviewDto;
import dev.monkeypatch.rctiming.service.FinalsLineupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The opt-in hooks from the event flow to the screens (#716): off by default, and only when the moment is right. */
class ScreenMessageHooksTest {

    private static final ScreenMessage HOLD = new ScreenMessage("Finals being calculated", "Lists soon", "hold", "info", null);
    private static final ScreenMessage UP = new ScreenMessage("Finals lists are up", null, "announce", "info", 30);

    private final MessageSettingsService settings = mock(MessageSettingsService.class);
    private final ScreenMessageService messages = mock(ScreenMessageService.class);
    private final ScreenService screens = mock(ScreenService.class);
    private final RaceRepository races = mock(RaceRepository.class);
    private final EventClassRepository eventClasses = mock(EventClassRepository.class);
    private final FinalsLineupService lineups = mock(FinalsLineupService.class);
    private final ScreenMessageHooks hooks = new ScreenMessageHooks(settings, messages, screens, races, eventClasses, lineups);

    @BeforeEach
    void aRaceInAnEventWithTwoScreens() {
        Race race = new Race();
        race.setId(5L);
        race.setEventClassId(7L);
        EventClass eventClass = new EventClass();
        eventClass.setEventId(9L);
        Screen one = new Screen();
        one.setId(1L);
        Screen two = new Screen();
        two.setId(2L);
        when(races.findById(5L)).thenReturn(Optional.of(race));
        when(eventClasses.getOrThrow(7L)).thenReturn(eventClass);
        when(screens.screens()).thenReturn(List.of(one, two));
        when(lineups.review(9L)).thenReturn(new FinalsReviewDto(true, null));
    }

    private void hooksSet(boolean hold, boolean announce, Long playlistId) {
        when(settings.get()).thenReturn(new MessageSettings(List.of(),
                new MessageSettings.Hooks(hold, HOLD, announce, playlistId, UP)));
    }

    @Test
    void theHoldGoesUpOnEveryScreenWhenTheLastQualifyingHeatFinishesAndTheHookIsOn() {
        hooksSet(true, false, null);

        hooks.holdAfterQualifying(5L);

        verify(messages).sendMessage(any(), eq(List.of(1L, 2L)), eq(HOLD));
    }

    @Test
    void nothingIsSentWhileTheHooksAreOff() {
        hooksSet(false, false, null);

        hooks.holdAfterQualifying(5L);
        hooks.announceFinals(9L);

        verify(messages, never()).sendMessage(any(), any(), any());
        verify(messages, never()).finalsUp(any(), any(), any(), any());
    }

    @Test
    void theHoldWaitsUntilQualifyingIsOver() {
        hooksSet(true, false, null);
        when(lineups.review(9L)).thenReturn(new FinalsReviewDto(false, null));

        hooks.holdAfterQualifying(5L);

        verify(messages, never()).sendMessage(any(), any(), any());
    }

    @Test
    void whenTheFinalsArePublishedThePlaylistIsPushedThenTheAnnouncementIsMade() {
        hooksSet(false, true, 3L);
        when(messages.finalsUp(any(), any(), any(), any())).thenReturn(new FinalsUpResult(List.of(), List.of()));

        hooks.announceFinals(9L);

        verify(messages).finalsUp(any(), eq(3L), eq(List.of(1L, 2L)), eq(UP));
    }

    @Test
    void withNoPlaylistSetOnlyTheAnnouncementIsMade() {
        hooksSet(false, true, null);
        when(messages.finalsUp(any(), any(), any(), any())).thenReturn(new FinalsUpResult(List.of(), List.of()));

        hooks.announceFinals(9L);

        verify(messages).finalsUp(any(), eq(null), eq(List.of(1L, 2L)), eq(UP));
    }

    @Test
    void nothingIsSentWhenThereAreNoScreens() {
        hooksSet(true, true, 3L);
        when(screens.screens()).thenReturn(List.of());

        hooks.holdAfterQualifying(5L);
        hooks.announceFinals(9L);

        verify(messages, never()).sendMessage(any(), any(), any());
        verify(messages, never()).finalsUp(any(), any(), any(), any());
    }
}
