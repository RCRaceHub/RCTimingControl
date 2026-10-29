package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.TestJson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaceFormatCodecTest {

    private final RaceFormatCodec codec = new RaceFormatCodec(TestJson.mapper());

    @Test
    void brokenYamlIsRefusedWithTheLineAndColumn() {
        assertThatThrownBy(() -> codec.read("type: TIMED\nname: [unclosed", RaceFormatCodec.FileType.YAML))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Failed to parse format config:")
                .hasMessageContaining("line ");
    }

    @Test
    void brokenJsonIsRefusedWithTheLineAndColumn() {
        assertThatThrownBy(() -> codec.read("{\"type\": \"TIMED\",", RaceFormatCodec.FileType.JSON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Failed to parse format config:")
                .hasMessageContaining("line ");
    }

    @Test
    void anUnknownTypeNamesTheOnesItKnows() {
        assertThat(catchThrowable(() -> codec.read("{\"type\": \"NOPE\"}", RaceFormatCodec.FileType.JSON)))
                .hasMessageContaining("type must be one of");
    }

    private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }
}
