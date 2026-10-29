package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.config.JacksonConfig;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;

/** Reads and writes a format's config as a JSON or YAML file, for export and import (FORMAT-14). */
@Component
public class RaceFormatCodec {

    public enum FileType {
        JSON("application/json"),
        YAML("application/yaml");

        private final String mediaType;

        FileType(String mediaType) {
            this.mediaType = mediaType;
        }

        public String mediaType() {
            return mediaType;
        }

        /** YAML when the media type names it, JSON otherwise. */
        public static FileType of(String mediaType) {
            return mediaType != null && mediaType.contains("yaml") ? YAML : JSON;
        }
    }

    private final ObjectMapper jsonMapper;
    /** A YAML mapper set up like the JSON one, so both file types read and write a config the same way. */
    private final ObjectMapper yamlMapper;

    public RaceFormatCodec(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = JacksonConfig.yamlMapper();
    }

    /** The config as pretty-printed text in the given file type. */
    public String write(RaceFormatConfig config, FileType fileType) {
        try {
            return mapper(fileType).writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not write the format config", e);
        }
    }

    /** @throws IllegalArgumentException when the text is not a format config */
    public RaceFormatConfig read(String text, FileType fileType) {
        try {
            RaceFormatConfig config = mapper(fileType).readValue(text, RaceFormatConfig.class);
            if (config == null) {
                throw new IllegalArgumentException("The format file is empty");
            }
            return config;
        } catch (InvalidTypeIdException e) {
            // Jackson's own message names the Java class
            throw new IllegalArgumentException(
                    "Failed to parse format config: type must be one of TIMED, BUMP_UP, POINTS_FINALS, SHIKABANE", e);
        } catch (JacksonException e) {
            String where = e.getLocation() == null ? ""
                    : " (line " + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr() + ")";
            throw new IllegalArgumentException("Failed to parse format config: " + e.getOriginalMessage() + where, e);
        }
    }

    private ObjectMapper mapper(FileType fileType) {
        return fileType == FileType.YAML ? yamlMapper : jsonMapper;
    }
}
