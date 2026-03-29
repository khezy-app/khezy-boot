package io.github.khezyapp.api.audit;

import io.github.khezyapp.api.audit.javers.api.ChangeMapperRegistry;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import lombok.RequiredArgsConstructor;
import org.javers.core.Javers;

import java.util.List;

/**
 * Utility class for performing manual object difference calculations using Javers.
 * <p>
 * This utility provides a high-level wrapper around the {@link org.javers.core.Javers}
 * diff engine, specifically designed to compute differences between two versions
 * of an object and map them into a flattened list of {@link EntityFieldChange} records.
 * </p>
 * <p>
 * It is particularly useful when auditing logic needs to be triggered manually
 * outside of the standard Hibernate interceptor lifecycle.
 * </p>
 */
@RequiredArgsConstructor
public class ObjectDiffUtils {

    /**
     * The underlying Javers diff engine.
     */
    private final Javers javers;

    /**
     * The registry used to map raw Javers changes into structured entity field changes.
     */
    private final ChangeMapperRegistry changeMapperRegistry;

    /**
     * Compares two versions of an object and returns a list of detected property changes.
     * <p>
     * The comparison identifies additions, removals, and value modifications,
     * which are then transformed by the {@link ChangeMapperRegistry} into a
     * simplified, flattened format suitable for audit logging.
     * </p>
     *
     * @param oldVersion     the previous state of the object (may be {@code null} for new entities)
     * @param currentVersion the current state of the object (may be {@code null} for deleted entities)
     * @return a list of {@link EntityFieldChange} objects representing the differences
     */
    public List<EntityFieldChange> compares(final Object oldVersion,
                                            final Object currentVersion) {
        final var diff = javers.compare(oldVersion, currentVersion);
        return changeMapperRegistry.mapAll(diff);
    }
}
