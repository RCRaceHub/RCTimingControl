package dev.monkeypatch.rctiming.domain.race;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Reads and writes the JSON a {@link ResultSnapshot} keeps its rows in. A stored result that can't be read is
 * never skipped: championship points, finals seeding and the results export would quietly come out wrong
 * without it, so it fails whatever is reading it instead. The rows stay plain JSON text on the entity, so
 * this is not a {@code JsonTextConverter}.
 */
@Component
public class ResultSnapshotJson {

    private static final TypeReference<List<ResultSnapshotDto.ResultRow>> ROWS = new TypeReference<>() {};
    private static final TypeReference<List<ResultSnapshotDto.PositionAtLap>> LAP_HISTORY = new TypeReference<>() {};

    private static final Logger log = LoggerFactory.getLogger(ResultSnapshotJson.class);

    private final ObjectMapper objectMapper;

    public ResultSnapshotJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** The result rows of race {@code raceId}, best first; none when there is no stored result. */
    public List<ResultSnapshotDto.ResultRow> positions(long raceId, String json) {
        return read(raceId, json, ROWS);
    }

    /** Who was where after each lap of race {@code raceId}; none when there is no stored result. */
    public List<ResultSnapshotDto.PositionAtLap> lapHistory(long raceId, String json) {
        return read(raceId, json, LAP_HISTORY);
    }

    /** {@code value} as the JSON stored for race {@code raceId}. */
    public String write(long raceId, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("The result of race " + raceId + " could not be stored", e);
        }
    }

    private <T> List<T> read(long raceId, String json, TypeReference<List<T>> type) {
        if (json == null) {
            return List.of();
        }
        try {
            List<T> rows = objectMapper.readValue(json, type);
            return rows == null ? List.of() : rows;
        } catch (JacksonException e) {
            // Logged here as well: the race finish only logs the message of what it catches, and the cause
            // is what says which part of the stored result is wrong
            log.error("The stored result of race {} could not be read", raceId, e);
            throw new IllegalStateException("The stored result of race " + raceId + " could not be read", e);
        }
    }
}
