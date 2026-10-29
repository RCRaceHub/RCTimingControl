package dev.monkeypatch.rctiming.livefeed;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.monkeypatch.rctiming.TestJson;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Live Feed v1 (#28): the app writes exactly the documented example, and it passes the schema. */
class LiveFeedV1GoldenTest {

    private static final String GOLDEN = "livefeed/live-feed-v1-example.json";
    private final ObjectMapper objectMapper = TestJson.mapper();

    @Test
    void serialisesExactlyAsTheGoldenExample() throws IOException {
        JsonNode written = objectMapper.readTree(objectMapper.writeValueAsString(example()));

        assertThat(pretty(written)).isEqualTo(pretty(objectMapper.readTree(read(GOLDEN))));
    }

    @Test
    void goldenExampleSatisfiesTheSchema() throws IOException {
        assertThat(validate(objectMapper.readTree(read(GOLDEN)))).isEmpty();
    }

    @Test
    void schemaRejectsAnythingButADisplayNameAboutAPerson() throws IOException {
        JsonNode broken = objectMapper.readTree(read(GOLDEN));
        ((ObjectNode) broken.at("/standings/0")).put("transponder_number", "1234567");

        assertThat(validate(broken)).isNotEmpty();
    }

    static List<Error> validate(JsonNode document) throws IOException {
        try (InputStream schema = new ClassPathResource("livefeed/live-feed-v1.schema.json").getInputStream()) {
            Schema jsonSchema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(schema);
            return jsonSchema.validate(document);
        }
    }

    static LiveFeedV1 example() {
        return new LiveFeedV1(
                LiveFeedV1.SCHEMA_VERSION,
                LiveFeedV1.TYPE_RACE,
                1842,
                "2026-10-18T14:21:07.512Z",
                new LiveFeedV1.Event(21, "Club Round 3", "2026-10-18"),
                new LiveFeedV1.Race(412, "2WD Buggy", "FINAL", 1, 1, "A", "RUNNING",
                        new LiveFeedV1.Clock(183_400, 300_000L, 116_600L, true)),
                List.of(
                        new LiveFeedV1.Standing(1, "Ada Lovelace", 1, 13, 13_870L, 13_102L, null, null, 0),
                        new LiveFeedV1.Standing(2, "Grace Hopper", 2, 13, 14_020L, 13_250L, 2_310L, 2_310L, 0),
                        new LiveFeedV1.Standing(3, "Walk-in Wendy", null, 12, 15_400L, 14_011L, null, null, 1)));
    }

    private String pretty(JsonNode node) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
