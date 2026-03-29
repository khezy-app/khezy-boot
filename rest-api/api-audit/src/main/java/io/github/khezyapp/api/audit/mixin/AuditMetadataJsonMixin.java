package io.github.khezyapp.api.audit.mixin;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.Nullable;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

/**
 * Jackson Mixin interface used to configure the JSON serialization and deserialization
 * behavior for the {@link io.github.khezyapp.api.audit.model.AuditMetadata} class.
 * <p>
 * This mixin enables dynamic property handling, allowing any additional metadata fields
 * to be flattened into the top-level JSON object during serialization and captured into
 * a map during deserialization. It also ensures that empty or null collections/maps
 * are excluded from the final JSON output.
 * </p>
 */
@JsonInclude(NON_EMPTY)
public interface AuditMetadataJsonMixin {

    /**
     * Captures any unknown properties found in the JSON payload and stores them
     * in the underlying metadata map.
     * <p>
     * This is used during deserialization to support an extensible schema.
     * </p>
     *
     * @param name the name of the dynamic property
     * @param value the value associated with the property, which may be {@code null}
     */
    @JsonAnySetter
    void setProperty(String name, @Nullable Object value);

    /**
     * Flattens the internal properties map into the JSON object's root level.
     * <p>
     * This is used during serialization to provide a clean, key-value structure
     * without an intermediate "properties" wrapper.
     * </p>
     *
     * @return a map containing all dynamic metadata properties
     */
    @JsonAnyGetter
    Map<String, Object> getProperties();
}
