package io.github.khezyapp.api.audit.aop;

import io.github.khezyapp.api.audit.annotation.AuditLog;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Source for retrieving {@link AuditLog} metadata from method and class levels.
 * <p>
 * This class serves as a resolver to find audit configurations, prioritizing
 * method-level annotations before falling back to class-level declarations.
 * </p>
 */
public class AuditLogAttributeSource {

    /**
     * Resolves the {@link AuditLog} annotation for a specific method and target class.
     * <p>
     * The search logic follows these steps:
     * <ol>
     * <li>Searches for the annotation directly on the method (including merged/composed annotations).</li>
     * <li>If not found on the method, searches for the annotation on the target class.</li>
     * </ol>
     * </p>
     *
     * @param method the method being executed
     * @param targetClass the class on which the method is invoked
     * @return the resolved {@link AuditLog} instance, or {@code null} if no annotation is present
     */
    public AuditLog findAttribute(final Method method,
                                  final Class<?> targetClass) {
        var auditLog = AnnotatedElementUtils.findMergedAnnotation(method, AuditLog.class);
        if (Objects.isNull(auditLog)) {
            auditLog = AnnotatedElementUtils.findMergedAnnotation(targetClass, AuditLog.class);
        }
        return auditLog;
    }
}
