package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fallback for any part whose {@code type} is not a statically known subtype — the AI
 * SDK provider-dynamic types such as {@code tool-<name>} and {@code data-<name>}.
 *
 * <p>Every field is preserved verbatim (including {@code type}) so the part round-trips
 * and the converters can interpret the {@code tool-*} ones.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public final class UnknownPart implements MessagePart {

    private final Map<String, Object> properties = new LinkedHashMap<>();

    @JsonAnySetter
    public void put(final String name,
                    final Object value) {
        properties.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> properties() {
        return properties;
    }

    @Override
    @JsonIgnore
    public String type() {
        final var value = properties.get("type");
        return value instanceof final String name ? name : null;
    }
}
