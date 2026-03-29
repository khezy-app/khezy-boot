package io.github.khezyapp.api.audit.javers;

import io.github.khezyapp.api.audit.javers.api.JaversMappingContext;
import io.github.khezyapp.api.audit.javers.api.ValueResolver;
import io.github.khezyapp.api.audit.model.ChangeType;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.NewObject;
import org.javers.core.diff.changetype.ObjectRemoved;
import org.javers.core.diff.changetype.container.ContainerChange;
import org.javers.core.metamodel.object.GlobalId;

import java.util.*;

/**
 * Default implementation of {@link JaversMappingContext} that manages the state
 * for flattening hierarchical changes into field-level audit entries.
 * <p>
 * This class handles object and path resolution, caches identifiers during
 * initialization, and performs recursive flattening while applying data masking
 * through a {@link ValueResolver}.
 * </p>
 */
public class DefaultJaversMappingContext implements JaversMappingContext {
    // store new / remove object change in collection
    private final Map<GlobalId, Object> objectCache = new HashMap<>();
    private final Map<GlobalId, String> pathCache = new HashMap<>();
    private final ValueResolver valueResolver;

    /**
     * Constructs a new context and initializes internal caches by scanning the
     * provided Javers diff.
     *
     * @param diff          the Javers diff containing structural changes
     * @param valueResolver the resolver used for masking and leaf identification
     */
    public DefaultJaversMappingContext(final Diff diff,
                                       final ValueResolver valueResolver) {
        this.valueResolver = valueResolver;
        this.initialize(diff);
    }

    private void initialize(final Diff diff) {
        for (final var change : diff.getChanges()) {
            if (change instanceof NewObject newObject) {
                newObject.getAffectedObject().ifPresent(obj -> objectCache.put(newObject.getAffectedGlobalId(), obj));
            } else if (change instanceof ObjectRemoved objRemoved) {
                objRemoved.getAffectedObject().ifPresent(obj -> objectCache.put(objRemoved.getAffectedGlobalId(), obj));
            } else if (change instanceof ContainerChange<?> cc) {
                final var oldList = (List<?>) cc.getLeft();
                for (int i = 0; i < oldList.size(); i++) {
                    final var item = oldList.get(i);
                    if (item instanceof GlobalId gid) {
                        pathCache.put(gid, cc.getPropertyName() + "[" + i + "]");
                    }
                }
            }
        }
    }

    /**
     * Resolves the domain object or value for a given ID, applying masking
     * via the value resolver.
     *
     * @param id the global identifier to resolve
     * @return the resolved and masked object
     */
    @Override
    public Object resolveObject(final GlobalId id) {
        final var object = objectCache.getOrDefault(id, id.value());
        return valueResolver.resolve(object);
    }

    /**
     * Retrieves the cached parent path for a specific identifier.
     *
     * @param id the global identifier
     * @return the associated path string, or {@code null} if not cached
     */
    @Override
    public String resolveParentPath(final GlobalId id) {
        return pathCache.get(id);
    }

    /**
     * Initiates the flattening process to convert complex objects into
     * specific field-level change records.
     *
     * @param path    the property path
     * @param type    the change classification
     * @param from    the original state
     * @param to      the target state
     * @param results the collection to populate with field changes
     */
    @Override
    public void flatten(final String path,
                        final ChangeType type,
                        final Object from,
                        final Object to,
                        final List<EntityFieldChange> results) {
        doFlatten(path, type, from, to, results, false);
    }

    private void doFlatten(final String path,
                           final ChangeType type,
                           final Object from,
                           final Object to,
                           final List<EntityFieldChange> results,
                           final boolean proceedMask) {
        final var sample = Optional.ofNullable(to).orElse(from);
        if (valueResolver.isLeaf(sample)) {
            // For bean object, handle mask from value resolver
            if (proceedMask) {
                results.add(create(path, type, from, to));
            } else {
                // Handle mask data for map change
                final var left = doMask(path, from);
                final var right = doMask(path, to);
                if (Objects.isNull(left) && Objects.isNull(right)) {
                    // After mask the value can be null if mask config as ignore
                    // which means skip those value from audit
                    return;
                } else {
                    results.add(create(path, type, left, right));
                }
            }
            return;
        }

        final var fromFields = extractFields(from);
        final var toFields = extractFields(to);
        final var allKeys = new HashSet<>(fromFields.keySet());
        allKeys.addAll(toFields.keySet());
        for (final var key : allKeys) {
            final var vFrom = fromFields.get(key);
            final var vTo = toFields.get(key);
            if (!Objects.equals(vFrom, vTo)) {
                doFlatten(path + "." + key, type, vFrom, vTo, results, true);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object doMask(final String path,
                          final Object from) {
        if (Objects.isNull(from)) {
            return null;
        }
        final var paths = path.split("\\.");
        final var currentKey = paths[paths.length - 1];
        final var tempMap = new HashMap<String, Object>();
        tempMap.put(currentKey, from);
        final var mask = (Map<String, Object>) valueResolver.resolve(tempMap);
        return mask.get(currentKey);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFields(final Object obj) {
        if (Objects.isNull(obj)) {
            return Collections.emptyMap();
        }
        return (Map<String, Object>) valueResolver.resolve(obj);
    }

    private EntityFieldChange create(final String prop,
                                     final ChangeType type,
                                     final Object from,
                                     final Object to) {
        return EntityFieldChange.builder()
                .property(prop)
                .changeType(type)
                .from(from)
                .to(to)
                .build();
    }
}
