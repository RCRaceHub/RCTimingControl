package dev.monkeypatch.rctiming.persistence.convert;

import tools.jackson.core.type.TypeReference;

import java.util.Map;

/** A free-form JSON object, such as a race format override patch. */
public class JsonMapConverter extends JsonTextConverter<Map<String, Object>> {

    public JsonMapConverter() {
        super(new TypeReference<>() {});
    }
}
