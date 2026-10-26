package dev.monkeypatch.rctiming.resultsexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Results Export v1 wire format (#27): field names, order and nulls must match the golden example,
 * and the example must satisfy the published schema. A change here is a change to the contract with RaceHub.
 */
class ResultsExportV1GoldenTest {

    static final String GOLDEN = "resultsexport/results-export-v1-example.json";

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void serialisesExactlyAsTheGoldenExample() throws IOException {
        String expected = pretty(objectMapper.readTree(read(GOLDEN)));
        String actual = pretty(objectMapper.valueToTree(example()));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void goldenExampleSatisfiesTheSchema() throws IOException {
        assertThat(validate(objectMapper.readTree(read(GOLDEN)))).isEmpty();
    }

    @Test
    void schemaRejectsAnImportedRowWithoutItsRaceHubIds() throws IOException {
        JsonNode broken = objectMapper.readTree(read(GOLDEN));
        ((com.fasterxml.jackson.databind.node.ObjectNode) broken.at("/races/1/results/0")).putNull("entry_id");

        assertThat(validate(broken)).isNotEmpty();
    }

    @Test
    void schemaRejectsARaceHubStandingWithoutItsDriverId() throws IOException {
        JsonNode broken = objectMapper.readTree(read(GOLDEN));
        ((com.fasterxml.jackson.databind.node.ObjectNode) broken.at("/championships/0/classes/0/standings/0"))
                .putNull("driver_profile_id");

        assertThat(validate(broken)).isNotEmpty();
    }

    @Test
    void schemaRejectsAStandingFromAnUnknownSource() throws IOException {
        JsonNode broken = objectMapper.readTree(read(GOLDEN));
        ((com.fasterxml.jackson.databind.node.ObjectNode) broken.at("/championships/0/classes/0/standings/0"))
                .put("external_source", "SOMEWHERE");

        assertThat(validate(broken)).isNotEmpty();
    }

    static List<Error> validate(JsonNode document) throws IOException {
        try (InputStream schema = new ClassPathResource("resultsexport/results-export-v1.schema.json").getInputStream()) {
            Schema jsonSchema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(schema);
            return jsonSchema.validate(document.toString(), InputFormat.JSON);
        }
    }

    private String pretty(JsonNode node) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    /** The example in docs/results-export-v1.md: a final, an abandoned heat, and one championship. */
    static ResultsExportV1 example() {
        var finalA = new ResultsExportV1.Race(
                412, 37, List.of("rh-class-buggy"), "2WD Buggy", "FINAL", 1, 1, "A", "FINISHED",
                "2026-10-18T15:42:10Z",
                List.of(
                        new ResultsExportV1.Row(1, "RACEHUB", "6f1c2a9e-0d4b-4c55-9b1e-2f9d1c7a5e01", "drv-ada",
                                "rh-class-buggy", 1201L, 88L, "Ada Lovelace", "1", 22, 301_456L, 13_102L,
                                List.of()),
                        new ResultsExportV1.Row(2, "RACEHUB", "0b7e4d12-8a3f-4f0e-a1c2-9e5d6b4c3a02", "drv-grace",
                                "rh-class-buggy", 1202L, 89L, "Grace Hopper", "2", 21, 300_990L, 13_250L,
                                List.of(new ResultsExportV1.Penalty("LAP", new BigDecimal("1"), "Short cut at the chicane", true))),
                        new ResultsExportV1.Row(3, null, null, null, null, 1250L, 140L, "Walk-in Wendy", "3", 20,
                                302_004L, 14_011L, List.of())));
        var abandonedHeat = new ResultsExportV1.Race(
                398, 37, List.of("rh-class-buggy"), "2WD Buggy", "QUALIFIER", 2, 1, null, "ABANDONED",
                "2026-10-18T11:05:33Z", List.of());
        var standings = List.of(
                new ResultsExportV1.Standing(1, "RACEHUB", "drv-ada", 88, "Ada Lovelace", 96, 1, 25, false, false),
                new ResultsExportV1.Standing(2, "RACEHUB", "drv-grace", 89, "Grace Hopper", 90, 2, 22, false, false),
                new ResultsExportV1.Standing(2, null, null, 140, "Walk-in Wendy", 90, 3, 20, true, false));
        return new ResultsExportV1(
                ResultsExportV1.SCHEMA_VERSION,
                7,
                "2026-10-18T15:43:02.118Z",
                new ResultsExportV1.Source("RCTimingControl", "Wyvern RC Club"),
                new ResultsExportV1.EventRef("evt-2026-round-3", 21, "Club Round 3", "2026-10-18", "IN_PROGRESS"),
                List.of(abandonedHeat, finalA),
                List.of(new ResultsExportV1.Championship(3, "Winter Series 2026", 3,
                        List.of(new ResultsExportV1.ChampionshipClass(5, "2WD Buggy", standings)))));
    }
}
