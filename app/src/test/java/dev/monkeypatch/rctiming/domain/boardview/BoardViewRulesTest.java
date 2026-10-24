package dev.monkeypatch.rctiming.domain.boardview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardViewRulesTest {

    @Test
    void anAddressNameIsLowerCasedAndMustBeShortLettersDigitsAndDashes() {
        assertThat(BoardViewRules.cleanSlug("  Pit-Monitor-2 ")).isEqualTo("pit-monitor-2");
        assertThatThrownBy(() -> BoardViewRules.cleanSlug("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardViewRules.cleanSlug("-leading")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardViewRules.cleanSlug("has space")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardViewRules.cleanSlug("a/b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardViewRules.cleanSlug("x".repeat(41))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLiveTopPanelDefaultsToTenPlacesAndOtherPanelsDropTheNumber() {
        List<BoardPanel> checked = BoardViewRules.validate(2, List.of(
                new BoardPanel(BoardPanelType.LIVE_TOP, null, 1),
                new BoardPanel(BoardPanelType.MARSHALS, 20, 2)));

        assertThat(checked).containsExactly(
                new BoardPanel(BoardPanelType.LIVE_TOP, 10, 1),
                new BoardPanel(BoardPanelType.MARSHALS, null, 2));
    }

    @Test
    void theLayoutIsRefusedWhenItHasNoPanelsTooManyOrAPanelWiderThanTheView() {
        BoardPanel timing = new BoardPanel(BoardPanelType.LIVE_TIMING, null, 1);
        assertThatThrownBy(() -> BoardViewRules.validate(2, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardViewRules.validate(2, java.util.Collections.nCopies(13, timing)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardViewRules.validate(1, List.of(new BoardPanel(BoardPanelType.LIVE_TIMING, null, 2))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardViewRules.validate(4, List.of(timing))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardViewRules.validate(2, List.of(new BoardPanel(BoardPanelType.LIVE_TOP, 31, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardViewRules.validate(2, List.of(new BoardPanel(BoardPanelType.LIVE_TOP, 0, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
