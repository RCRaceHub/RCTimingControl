package dev.monkeypatch.rctiming.persistence;

import dev.monkeypatch.rctiming.persistence.vendor.DatabaseVendor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Only the write pool may begin transactions immediately; readers must never take the write lock. */
class WriteConnectionPropertiesTest {

    @Test
    void writePoolBeginsImmediatelyAndReadPoolDoesNot() {
        assertThat(DatabaseVendor.SQLITE.writeConnectionProperties().getProperty("transaction_mode"))
                .isEqualToIgnoringCase("IMMEDIATE");
        assertThat(DatabaseVendor.SQLITE.connectionProperties().getProperty("transaction_mode"))
                .isNotEqualToIgnoringCase("IMMEDIATE");
        assertThat(DatabaseVendor.SQLITE.writeConnectionProperties().getProperty("busy_timeout")).isEqualTo("5000");
    }
}
