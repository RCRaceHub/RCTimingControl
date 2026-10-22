package dev.monkeypatch.rctiming.domain.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.monkeypatch.rctiming.domain.format.RaceFormatCodec.FileType;
import org.springframework.stereotype.Component;

/** Reads and writes a scoring system's config as a JSON or YAML file, for export and import (the same file types as race formats). */
@Component
public class ScoringCodec {

    private final ObjectMapper jsonMapper;
    /** Spring's mapper with a YAML parser, so both file types read and write a config the same way. */
    private final ObjectMapper yamlMapper;

    public ScoringCodec(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = jsonMapper.copyWith(new YAMLFactory());
    }

    /** The config as pretty-printed text in the given file type. */
    public String write(ScoringConfig config, FileType fileType) {
        try {
            return mapper(fileType).writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (JsonProcessingException e) {
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
        } catch (JsonProcessingException e) {
            String where = e.getLocation() == null ? ""
                    : " (line " + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr() + ")";
            throw new IllegalArgumentException("Failed to parse scoring config: " + e.getOriginalMessage() + where, e);
        }
    }

    private ObjectMapper mapper(FileType fileType) {
        return fileType == FileType.YAML ? yamlMapper : jsonMapper;
    }
}
