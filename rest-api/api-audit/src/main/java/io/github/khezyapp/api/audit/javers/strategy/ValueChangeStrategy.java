package io.github.khezyapp.api.audit.javers.strategy;

import io.github.khezyapp.api.audit.annotation.SensitiveData;
import io.github.khezyapp.api.audit.javers.api.ChangeMappingStrategy;
import io.github.khezyapp.api.audit.javers.api.JaversMappingContext;
import io.github.khezyapp.api.audit.model.ChangeType;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import org.javers.core.diff.Change;
import org.javers.core.diff.changetype.InitialValueChange;
import org.javers.core.diff.changetype.PropertyChangeType;
import org.javers.core.diff.changetype.TerminalValueChange;
import org.javers.core.diff.changetype.ValueChange;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link ChangeMappingStrategy} for processing Javers {@link ValueChange} types.
 * <p>
 * This strategy handles changes to simple property values. It includes specialized logic
 * to detect {@link SensitiveData} annotations directly on the entity fields, allowing
 * for property-level masking or exclusion (ignoring) within the generated audit trail.
 * </p>
 */
public class ValueChangeStrategy implements ChangeMappingStrategy {

    /**
     * Determines if the change is an instance of {@link ValueChange}.
     *
     * @param change the Javers change to evaluate
     * @return {@code true} if the change is a value-based change; {@code false} otherwise
     */
    @Override
    public boolean supports(final Change change) {
        return change instanceof ValueChange;
    }

    /**
     * Maps a simple value change into a list containing a single {@link EntityFieldChange}.
     * <p>
     * The method filters out initial or terminal changes and null states. It resolves
     * the full property path and checks for field-level sensitivity metadata to
     * determine if the value should be masked or if the change should be ignored entirely.
     * </p>
     *
     * @param change  the Javers value change
     * @param context the mapping context for path resolution
     * @return a list containing the mapped field change, or an empty list if ignored or invalid
     */
    @Override
    public List<EntityFieldChange> map(final Change change,
                                       final JaversMappingContext context) {
        final var vc = (ValueChange) change;
        if (Objects.isNull(vc.getLeft()) ||
                Objects.isNull(vc.getRight()) ||
                vc instanceof InitialValueChange ||
                vc instanceof TerminalValueChange) {
            return Collections.emptyList();
        }

        final var propertyName = formatPath(vc.getPropertyNameWithPath());
        final var parentPath = context.resolveParentPath(vc.getAffectedGlobalId());
        String fullPath;
        if (Objects.nonNull(parentPath)) {
            fullPath = parentPath + "." + propertyName;
        } else {
            fullPath = propertyName;
        }

        final var sensitiveData = extractSensitiveData(vc);
        if (Objects.isNull(sensitiveData)) {
            return Collections.singletonList(
                    EntityFieldChange.builder()
                            .property(fullPath)
                            .changeType(resolveChangeType(vc))
                            .from(vc.getLeft())
                            .to(vc.getRight())
                            .build()
            );
        }

        if (!sensitiveData.ignore()) {
            return Collections.singletonList(
                    EntityFieldChange.builder()
                            .property(fullPath)
                            .changeType(resolveChangeType(vc))
                            .from(Objects.nonNull(vc.getLeft()) ? sensitiveData.mask() : null)
                            .to(Objects.nonNull(vc.getRight()) ? sensitiveData.mask() : null)
                            .build()
            );
        }

        return Collections.emptyList();
    }

    private String formatPath(final String path) {
        //contacts/0.type -> list element
        //securityProfile/Q1.answer
        return path.replaceAll("/(\\d+)(\\.[^/.]+)", "[$1]$2")
                .replaceAll("/([^/.]+)(\\.[^/.]+)", ".$1$2");
    }

    /**
     * Reflectively extracts the {@link SensitiveData} annotation from the field
     * associated with the change.
     *
     * @param change the value change being inspected
     * @return the found annotation, or {@code null} if not present or field not found
     */
    private SensitiveData extractSensitiveData(final ValueChange change) {
        if (change.getAffectedObject().isEmpty()) {
            return null;
        }
        final var field = ReflectionUtils.findField(
                change.getAffectedObject().get().getClass(),
                change.getPropertyName()
        );
        if (Objects.isNull(field)) {
            return null;
        }
        return AnnotatedElementUtils.findMergedAnnotation(field, SensitiveData.class);
    }

    /**
     * Translates Javers {@link PropertyChangeType} into the internal {@link ChangeType}.
     *
     * @param change the value change
     * @return the mapped internal change type
     */
    private ChangeType resolveChangeType(final ValueChange change) {
        final var changeType = change.getChangeType();
        if (changeType == PropertyChangeType.PROPERTY_ADDED) {
            return ChangeType.ADDED;
        }
        if (changeType == PropertyChangeType.PROPERTY_REMOVED) {
            return ChangeType.REMOVED;
        }
        return ChangeType.VALUE_CHANGES;
    }
}
