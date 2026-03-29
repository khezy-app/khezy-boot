package io.github.khezyapp.api.audit.interceptor;

import io.github.khezyapp.api.audit.HibernateUtils;
import io.github.khezyapp.api.audit.annotation.SensitiveData;
import io.github.khezyapp.api.audit.api.AuditLogService;
import io.github.khezyapp.api.audit.javers.CompositeChangeMapper;
import io.github.khezyapp.api.audit.model.AuditEntityChange;
import io.github.khezyapp.api.audit.model.ChangeType;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.CallbackException;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.hibernate.type.Type;
import org.javers.common.reflection.JaversField;
import org.javers.common.reflection.ReflectionUtil;
import org.javers.core.Javers;
import org.slf4j.MDC;
import org.springframework.beans.PropertyAccessorFactory;

import java.util.*;

/**
 * Hibernate {@link Interceptor} implementation that captures entity lifecycle events
 * to generate audit logs using Javers and a custom mapping context.
 * <p>
 * This interceptor tracks entity insertions, updates, and deletions within a {@link ThreadLocal}
 * buffer, ensuring audit records are collected during the session and dispatched only
 * upon successful transaction completion.
 * </p>
 */
@RequiredArgsConstructor
@Slf4j
public class KhezyAuditLogHibernateInterceptor implements Interceptor {

    /**
     * Buffer to store pending audit changes for the current thread, indexed by the entity instance.
     */
    private final ThreadLocal<Map<Object, AuditEntityChange>> auditBuffer =
            ThreadLocal.withInitial(HashMap::new);

    private final Javers javers;
    private final AuditLogService auditLogService;
    private final CompositeChangeMapper compositeChangeMapper;

    /**
     * Intercepts the entity persistence (insert) event to compute a "New Object" audit state.
     */
    @Override
    public boolean onPersist(final Object entity,
                             final Object id,
                             final Object[] state,
                             final String[] propertyNames,
                             final Type[] types) throws CallbackException {
        computeNewObject(entity, id, state, propertyNames);
        return false;
    }

    /**
     * Intercepts the entity removal event to compute a "Deleted Object" audit state.
     */
    @Override
    public void onRemove(final Object entity,
                         final Object id,
                         final Object[] state,
                         final String[] propertyNames,
                         final Type[] types) throws CallbackException {
        computeDeleteObject(entity, id, state, propertyNames);
    }

    /**
     * Intercepts entity updates (dirty flushes) to compare the previous state with
     * the current state using Javers.
     */
    @Override
    public boolean onFlushDirty(final Object entity,
                                final Object id,
                                final Object[] currentState,
                                final Object[] previousState,
                                final String[] propertyNames,
                                final Type[] types) throws CallbackException {
        computeChanges(entity, id, previousState, propertyNames);
        return false;
    }

    /**
     * Post-flush callback to ensure newly generated IDs are captured in the audit buffer
     * for entities that used identity-based primary key generation.
     */
    @Override
    public void postFlush(final Iterator<Object> entities) throws CallbackException {
        final var bufferKeys = auditBuffer.get().keySet();
        entities.forEachRemaining(entity -> {
            if (bufferKeys.contains(entity)) {
                final var entityChange = auditBuffer.get().get(entity);
                if (Objects.isNull(entityChange.getEntityId())) {
                    final var idProp = HibernateUtils.extractIdProperty(entity.getClass());
                    entityChange.setEntityId(
                            PropertyAccessorFactory.forDirectFieldAccess(entity).getPropertyValue(idProp)
                    );
                }
                entityChange.setEntityName(entity.getClass().getSimpleName());
            }
        });
    }

    /**
     * Finalizes and dispatches captured audit changes to the {@link AuditLogService}
     * before the transaction is committed.
     */
    @Override
    public void beforeTransactionCompletion(final Transaction tx) {
        final var changes = auditBuffer.get().values();
        if (!changes.isEmpty() && tx.getStatus() == TransactionStatus.ACTIVE) {
            try {
                for (final var change : changes) {
                    auditLogService.onAuditEntityChanges(change);
                }
            } finally {
                auditBuffer.remove();
            }
        }
    }

    /**
     * Cleanup callback to ensure the {@link ThreadLocal} buffer is cleared
     * after the transaction ends.
     */
    @Override
    public void afterTransactionCompletion(final Transaction tx) {
        auditBuffer.remove();
    }

    /**
     * Computes the initial state of a newly persisted object, flattening fields
     * and applying masking where necessary.
     */
    private void computeNewObject(final Object entity,
                                  final Object id,
                                  final Object[] state,
                                  final String[] propertyNames) {
        final var changes = new ArrayList<EntityFieldChange>();
        final var fieldMetadata = extractFieldInfo(entity);
        final var fieldShouldSKip = computeFieldShouldSkip(fieldMetadata);
        for (var idx = 0; idx < propertyNames.length; idx++) {
            final var fieldName = propertyNames[idx];
            if (fieldShouldSKip.get(fieldName)) {
                continue;
            }
            flatten(fieldMetadata, fieldName, ChangeType.ADDED, null, state[idx], changes);
        }
        if (!changes.isEmpty()) {
            final var entityChange = getAuditEntityChange(entity, id, changes);
            final var buffer = auditBuffer.get();
            buffer.put(entity, entityChange);
        }
    }

    /**
     * Generates a standardized {@link AuditEntityChange} DTO, capturing the
     * trace ID from the MDC and basic entity metadata.
     */
    private static AuditEntityChange getAuditEntityChange(final Object entity,
                                                          final Object id,
                                                          final List<EntityFieldChange> changes) {
        return AuditEntityChange.builder()
                .traceId(MDC.get("traceId"))
                .entityId(id)
                .entityClass(entity.getClass())
                .entityName(entity.getClass().getSimpleName())
                .changes(changes)
                .build();
    }

    /**
     * Uses Javers to perform a differential analysis between the previous state
     * and the current entity instance.
     */
    private void computeChanges(final Object entity,
                                final Object id,
                                final Object[] previousState,
                                final String[] propertyNames) {
        final var oldEntity = HibernateUtils.populate(entity.getClass(), id, previousState, propertyNames);
        final var diff = javers.compare(oldEntity, entity);
        final var buffer = auditBuffer.get();
        final var changes = compositeChangeMapper.mapAll(diff);
        if (!changes.isEmpty()) {
            final var entityChange = getAuditEntityChange(entity, id, changes);
            buffer.put(entity, entityChange);
        }
    }

    /**
     * Computes the state of an object being removed, marking all eligible
     * fields as {@link ChangeType#REMOVED}.
     */
    private void computeDeleteObject(final Object entity,
                                     final Object id,
                                     final Object[] state,
                                     final String[] propertyNames) {
        final var changes = new ArrayList<EntityFieldChange>();
        final var fieldMetadata = extractFieldInfo(entity);
        final var fieldShouldSKip = computeFieldShouldSkip(fieldMetadata);
        for (var idx = 0; idx < propertyNames.length; idx++) {
            final var fieldName = propertyNames[idx];
            if (fieldShouldSKip.get(fieldName)) {
                continue;
            }
            flatten(fieldMetadata, fieldName, ChangeType.REMOVED, state[idx], null, changes);
        }
        if (!changes.isEmpty()) {
            final var entityChange = getAuditEntityChange(entity, id, changes);
            final var buffer = auditBuffer.get();
            buffer.put(entity, entityChange);
        }
    }

    /**
     * Extracts field metadata from the entity class to assist in
     * relationship detection and annotation processing.
     */
    private Map<String, JaversField> extractFieldInfo(final Object entity) {
        final var fieldAnnotations = new LinkedHashMap<String, JaversField>();
        ReflectionUtil.getAllFields(entity.getClass())
                .forEach(f -> fieldAnnotations.put(f.name(), f));
        return fieldAnnotations;
    }

    /**
     * Determines which fields should be skipped during the flattening process,
     * specifically targeting JPA relationship annotations (OneToMany, ManyToOne, etc.).
     */
    private Map<String, Boolean> computeFieldShouldSkip(final Map<String, JaversField> fieldMetadata) {
        final var fieldAnnotations = new LinkedHashMap<String, Boolean>();
        final var relationAnnotations = Set.of(OneToOne.class, ManyToOne.class, OneToMany.class, ManyToMany.class);
        fieldMetadata.forEach((fieldName, field) ->
                fieldAnnotations.put(
                        fieldName,
                        field.getAnnotationTypes().stream().anyMatch(relationAnnotations::contains))
        );
        return fieldAnnotations;
    }

    /**
     * Performs property-level flattening and applies {@link SensitiveData}
     * masking rules based on field annotations.
     */
    public void flatten(final Map<String, JaversField> fieldMetadata,
                        final String rootProperty,
                        final ChangeType type,
                        final Object from,
                        final Object to,
                        final List<EntityFieldChange> results) {
        final var field = fieldMetadata.get(rootProperty);
        final var sensitiveData = (SensitiveData) field.getAnnotations()
                .stream()
                .filter(annotation -> annotation.annotationType().equals(SensitiveData.class))
                .findFirst()
                .orElse(null);
        if (Objects.nonNull(sensitiveData)) {
            if (!sensitiveData.ignore()) {
                results.add(create(
                        rootProperty,
                        type,
                        Objects.nonNull(from) ? sensitiveData.mask() : null,
                        Objects.nonNull(to) ? sensitiveData.mask() : null)
                );
            }
            return;
        }

        doFlatten(rootProperty, type, from, to, results, false);
    }

    /**
     * Recursively flattens complex types (Maps, Collections, Arrays) into
     * individual audit entries.
     */
    @SuppressWarnings("unchecked")
    private void doFlatten(final String path,
                           final ChangeType type,
                           final Object from,
                           final Object to,
                           final List<EntityFieldChange> results,
                           final boolean proceedMask) {
        if (Objects.isNull(from) && Objects.isNull(to)) {
            return;
        }
        final var sample = Optional.ofNullable(to).orElse(from);
        if (compositeChangeMapper.getValueResolver().isLeaf(sample)) {
            if (proceedMask) {
                results.add(create(path, type, from, to));
            } else {
                final var left = doMask(path, from);
                final var right = doMask(path, to);
                if (Objects.isNull(left) && Objects.isNull(right)) {
                    return;
                } else {
                    results.add(create(path, type, left, right));
                }
            }
            return;
        }

        // Masker process will return map of array or collection at this point
        final var fromFields = proceedMask ? from : resolveValueAndMask(from);
        final var toFields = proceedMask ? to : resolveValueAndMask(to);

        if (type.isAdded() && toFields instanceof Map<?, ?>) {
            doFlattenMapChange(path, type, results, null, (Map<String, Object>) toFields);
        } else if (type.isRemoved() && fromFields instanceof Map<?, ?>) {
            doFlattenMapChange(path, type, results, (Map<String, Object>) fromFields, null);
        } else if (type.isAdded() && toFields instanceof Collection<?> col) {
            doFlattenCollection(path, type, results, null, col);
        } else if (type.isRemoved() && fromFields instanceof Collection<?> col) {
            doFlattenCollection(path, type, results, col, null);
        } else if (type.isAdded() && Objects.nonNull(toFields) && toFields.getClass().isArray()) {
            doFlattenArray(path, type, results, null, (Object[]) toFields);
        } else if (type.isRemoved() && Objects.nonNull(fromFields) && fromFields.getClass().isArray()) {
            doFlattenArray(path, type, results, (Object[]) toFields, null);
        }
    }

    private void doFlattenCollection(final String path,
                                     final ChangeType type,
                                     final List<EntityFieldChange> results,
                                     final Collection<?> fromFields,
                                     final Collection<?> toFields) {
        if (type.isAdded()) {
            var idx = -1;
            for (final var added : toFields) {
                doFlatten(path + "[%d]".formatted(++idx), type, null, added, results, true);
            }
        } else if (type.isRemoved()) {
            var idx = -1;
            for (final var removed : fromFields) {
                doFlatten(path + "[%d]".formatted(++idx), type, removed, null, results, true);
            }
        }
    }

    private void doFlattenArray(final String path,
                                final ChangeType type,
                                final List<EntityFieldChange> results,
                                final Object[] fromFields,
                                final Object[] toFields) {
        if (type.isAdded()) {
            var idx = -1;
            for (final var added : toFields) {
                doFlatten(path + "[%d]".formatted(++idx), type, null, added, results, true);
            }
        } else if (type.isRemoved()) {
            var idx = -1;
            for (final var removed : fromFields) {
                doFlatten(path + "[%d]".formatted(++idx), type, removed, null, results, true);
            }
        }
    }

    private void doFlattenMapChange(final String path,
                                    final ChangeType type,
                                    final List<EntityFieldChange> results,
                                    final Map<String, Object> fromFields,
                                    final Map<String, Object> toFields) {
        final var allKeys = new HashSet<String>();

        final var ffield = Optional.ofNullable(fromFields).orElse(Collections.emptyMap());
        final var tfield = Optional.ofNullable(toFields).orElse(Collections.emptyMap());

        allKeys.addAll(ffield.keySet());
        allKeys.addAll(tfield.keySet());

        for (final var key : allKeys) {
            final var vFrom = ffield.get(key);
            final var vTo = tfield.get(key);
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
        final var mask = (Map<String, Object>) compositeChangeMapper.getValueResolver().resolve(tempMap);
        return mask.get(currentKey);
    }

    private Object resolveValueAndMask(final Object obj) {
        if (Objects.isNull(obj)) {
            return Collections.emptyMap();
        }
        return compositeChangeMapper.getValueResolver().resolve(obj);
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
