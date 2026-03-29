package io.github.khezyapp.api.audit.aop;

import io.github.khezyapp.api.audit.annotation.AuditLog;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/**
 * Pointcut implementation that identifies join points decorated with the {@link AuditLog} annotation.
 * <p>
 * This class performs a static check to determine if a method or its declaring class
 * is a candidate for audit logging based on the presence of the required annotation.
 * </p>
 */
public class AuditLogPointcut extends StaticMethodMatcherPointcut {

    /**
     * Determines whether the given method or its containing class is annotated with {@link AuditLog}.
     * <p>
     * The match is successful if:
     * <ul>
     * <li>The method itself has the {@code @AuditLog} annotation.</li>
     * <li>The target class has the {@code @AuditLog} annotation.</li>
     * </ul>
     * This implementation uses {@link AnnotatedElementUtils} to support bridged methods
     * and composed annotations.
     * </p>
     *
     * @param method the candidate method
     * @param targetClass the target class
     * @return {@code true} if the method or class is annotated with {@link AuditLog}, {@code false} otherwise
     */
    @Override
    public boolean matches(final Method method,
                           final Class<?> targetClass) {
        return AnnotatedElementUtils.hasAnnotation(method, AuditLog.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, AuditLog.class);
    }
}
