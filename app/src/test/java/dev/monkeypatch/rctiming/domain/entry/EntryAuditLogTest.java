package dev.monkeypatch.rctiming.domain.entry;

import dev.monkeypatch.rctiming.TestJson;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntryAuditLogTest {

    @Test
    void aMissingValue_isWrittenAsJsonNull_notTheWordNull() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("transponderNumberSnapshot", "1234");
        values.put("secondaryTransponderNumber", null);

        assertThat(EntryAuditLog.snapshot(TestJson.mapper(), values))
                .isEqualTo("{\"transponderNumberSnapshot\":\"1234\",\"secondaryTransponderNumber\":null}");
    }
}
