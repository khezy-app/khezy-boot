package io.github.khezyapp.api.security.aop;

import io.github.khezyapp.api.security.annotation.RowLevelSecurity;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * AOP pointcut that matches methods annotated with {@link RowLevelSecurity}.
 * Used to trigger row-level filter application before method execution.
 * Resolves the method from the target class to handle CGLIB proxies.
 */
public class RowLevelSecurityPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(final Method method,
                           final Class<?> targetClass) {
        final var targetMethod = resolveMethod(method, targetClass);
        return AnnotatedElementUtils.hasAnnotation(
                Objects.requireNonNullElse(targetMethod, method),
                RowLevelSecurity.class
        );
    }

    private static Method resolveMethod(final Method method,
                                        final Class<?> targetClass) {
        try {
            return targetClass.getMethod(method.getName(), method.getParameterTypes());
        } catch (final NoSuchMethodException e) {
            return null;
        }
    }
}
