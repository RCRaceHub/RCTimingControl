package dev.monkeypatch.rctiming.screens;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static dev.monkeypatch.rctiming.jooq.generated.tables.DisplayMessageSettings.DISPLAY_MESSAGE_SETTINGS;

/**
 * The one row of {@code display_message_settings} (#716): the JSON text of {@link MessageSettings}. A plain repository,
 * because the row has no entity. Writes must run in the caller's write transaction.
 */
@Repository
public class MessageSettingsRepository {

    private static final long ROW = 1L;

    private final DSLContext dsl;

    public MessageSettingsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** The saved JSON, or empty when nothing has been saved yet. */
    public Optional<String> find() {
        return dsl.select(DISPLAY_MESSAGE_SETTINGS.SETTINGS).from(DISPLAY_MESSAGE_SETTINGS)
                .where(DISPLAY_MESSAGE_SETTINGS.ID.eq(ROW)).fetchOptional(DISPLAY_MESSAGE_SETTINGS.SETTINGS);
    }

    /** Saves the JSON and the time it was saved, creating the row the first time. */
    public void save(String settings, Instant now) {
        int updated = dsl.update(DISPLAY_MESSAGE_SETTINGS).set(DISPLAY_MESSAGE_SETTINGS.SETTINGS, settings)
                .set(DISPLAY_MESSAGE_SETTINGS.UPDATED_AT, now.truncatedTo(ChronoUnit.MICROS))
                .where(DISPLAY_MESSAGE_SETTINGS.ID.eq(ROW)).execute();
        if (updated == 0) {
            dsl.insertInto(DISPLAY_MESSAGE_SETTINGS).set(DISPLAY_MESSAGE_SETTINGS.ID, ROW)
                    .set(DISPLAY_MESSAGE_SETTINGS.SETTINGS, settings)
                    .set(DISPLAY_MESSAGE_SETTINGS.UPDATED_AT, now.truncatedTo(ChronoUnit.MICROS)).execute();
        }
    }
}
