package dev.monkeypatch.rctiming.query.entry;

import dev.monkeypatch.rctiming.TestJson;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class EntryHistoryQueryTest {

    private final EntryHistoryQuery query = new EntryHistoryQuery(Mockito.mock(DSLContext.class), TestJson.mapper());

    @Test
    void summaryOf_describesATransponderSwap() {
        String summary = query.summaryOf("TRANSPONDER_SWAP",
                "{\"slot\":\"PRIMARY\",\"transponderNumber\":\"1234\"}",
                "{\"slot\":\"PRIMARY\",\"transponderNumber\":\"5678\"}");

        assertThat(summary).isEqualTo("Changed the primary transponder from 1234 to 5678");
    }

    @Test
    void summaryOf_describesAMerge() {
        String summary = query.summaryOf("COMPETITOR_MERGED", "{\"displayName\":\"Al\"}", "{\"displayName\":\"Alan Smith\"}");

        assertThat(summary).isEqualTo("Moved to Alan Smith when two competitors were merged");
    }

    @Test
    void summaryOf_survivesMissingOrBrokenSnapshotsAndUnknownActions() {
        assertThat(query.summaryOf("TRANSPONDER_SWAP", null, "not json"))
                .isEqualTo("Changed the transponder from none to none");
        assertThat(query.summaryOf("SOMETHING_NEW", null, null)).isEqualTo("SOMETHING_NEW");
    }
}
