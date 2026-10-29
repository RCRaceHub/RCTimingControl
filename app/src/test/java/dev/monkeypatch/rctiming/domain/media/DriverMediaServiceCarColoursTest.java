package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.infrastructure.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The checks on a car colours change (#685); nothing is stored for a request that fails them. */
class DriverMediaServiceCarColoursTest {

    private final EntryCarColoursRepository carColours = mock(EntryCarColoursRepository.class);
    private final EntryRepository entries = mock(EntryRepository.class);
    private DriverMediaService service;

    @BeforeEach
    void setUp() {
        when(entries.getOrThrow(7L)).thenReturn(new Entry());
        service = new DriverMediaService(mock(DriverMediaRepository.class), mock(EntryPhotoRepository.class), carColours,
                mock(CompetitorRepository.class), entries, mock(RaceEntryRepository.class),
                mock(ObjectStorageService.class), mock(AuditService.class));
    }

    @Test
    void setCarColoursRejectsAValueThatIsNotRrggbb() {
        assertThatThrownBy(() -> service.setCarColours(null, 7L, List.of("red")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(carColours);
    }

    @Test
    void setCarColoursRejectsMoreThanFourColours() {
        List<String> five = List.of("#000001", "#000002", "#000003", "#000004", "#000005");
        assertThatThrownBy(() -> service.setCarColours(null, 7L, five)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(carColours);
    }

    @Test
    void carColoursReadsStoredValuesInOrder() {
        EntryCarColours row = new EntryCarColours();
        row.setColours("#ff0000,#ffff00,#ffffff");
        when(carColours.findByEntryId(7L)).thenReturn(Optional.of(row));
        assertThat(service.carColours(7L)).containsExactly("#ff0000", "#ffff00", "#ffffff");
        verify(carColours).findByEntryId(7L);
    }
}
