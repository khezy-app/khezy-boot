package io.github.khezyapp.api.audit;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.temporal.Temporal;
import java.util.Objects;
import java.util.UUID;

/**
 * Utility class for identifying terminal or "simple" types that should not be
 * further traversed during the audit flattening process.
 * <p>
 * This class defines "primitive" broadly to include standard Java value types
 * such as Strings, Numbers, Dates, and UUIDs, treating them as leaf nodes
 * in the object graph.
 * </p>
 */
public final class CheckTypes {

    private CheckTypes() {
    }

    /**
     * Checks if the provided object instance should be treated as a primitive leaf node.
     *
     * @param value the object instance to evaluate
     * @return {@code true} if the value is null or its class is a primitive type; {@code false} otherwise
     */
    public static boolean isPrimitive(final Object value) {
        if (Objects.isNull(value)) {
            return true;
        }
        return isPrimitive(value.getClass());
    }

    /**
     * Determines if a given class is considered a primitive or simple value type.
     * <p>
     * Supported types include:
     * <ul>
     * <li>Java primitives and their wrappers</li>
     * <li>{@link Number} and its subclasses</li>
     * <li>{@link String} and {@link Character}</li>
     * <li>{@link Boolean}</li>
     * <li>Temporal types ({@link Temporal})</li>
     * <li>{@link UUID}</li>
     * <li>{@link Enum}</li>
     * </ul>
     * </p>
     *
     * @param clz the class to inspect
     * @return {@code true} if the class matches a supported simple type; {@code false} otherwise
     */
    public static boolean isPrimitive(final Class<?> clz) {
        return clz.isPrimitive() ||
                Number.class.isAssignableFrom(clz) ||
                String.class.isAssignableFrom(clz) ||
                Character.class.isAssignableFrom(clz) ||
                Boolean.class.isAssignableFrom(clz) ||
                Temporal.class.isAssignableFrom(clz) ||
                UUID.class.isAssignableFrom(clz) ||
                Enum.class.isAssignableFrom(clz) ||
                Path.class.isAssignableFrom(clz) ||
                URI.class.isAssignableFrom(clz) ||
                URL.class.isAssignableFrom(clz);
    }
}
