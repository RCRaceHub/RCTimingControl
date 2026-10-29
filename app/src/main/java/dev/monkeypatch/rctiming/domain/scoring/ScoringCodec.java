package dev.monkeypatch.rctiming.domain.scoring;

import dev.monkeypatch.rctiming.config.JacksonConfig;
import dev.monkeypatch.rctiming.domain.format.RaceFormatCodec.FileType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Reads and writes a scoring system's config as a JSON or YAML file, for export and import (the same file types as race formats). */
@Component
public class ScoringCodec {

    private final ObjectMapper jsonMapper;
    /** A YAML mapper set up like Spring's JSON one (unknown properties ignored), so both file types read a config the same way. */
    private final ObjectMapper yamlMapper;

    public ScoringCodec(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        YAMLMapper.Builder yaml = YAMLMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        JacksonConfig.keepJackson2Defaults(yaml);
        this.yamlMapper = yaml.build();
    }

    /** The config as pretty-printed text in the given file type. */
    public String write(ScoringConfig config, FileType fileType) {
        try {
            return mapper(fileType).writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not write the scoring config", e);
        }
    }

    /**
     * Reads a config and checks its numbers.
     *
     * @throws IllegalArgumentException (HTTP 400) when the text is not a scoring config or a value is out of range
     */
    public ScoringConfig read(String text, FileType fileType) {
        try {
            ScoringConfig config = mapper(fileType).readValue(text, ScoringConfig.class);
            if (config == null) {
                throw new IllegalArgumentException("The scoring file is empty");
            }
            config.validate();
            return config;
        } catch (InvalidTypeIdException e) {
            // Jackson's own message names the Java class
            throw new IllegalArgumentException(
                    "Failed to parse scoring config: type must be one of CLASSIC, BRCA, PLACE_POINTS", e);
        } catch (JacksonException e) {
            String where = e.getLocation() == null ? ""
                    : " (line " + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr() + ")";
            throw new IllegalArgumentException("Failed to parse scoring config: " + e.getOriginalMessage() + where, e);
        }
    }

    private ObjectMapper mapper(FileType fileType) {
        return fileType == FileType.YAML ? yamlMapper : jsonMapper;
    }
}
