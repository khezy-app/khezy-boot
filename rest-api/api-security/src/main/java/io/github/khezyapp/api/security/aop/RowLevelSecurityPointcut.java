package io.github.khezyapp.api.security.aop;

import io.github.khezyapp.api.security.annotation.RowLevelSecurity;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/**
 * AOP pointcut that matches methods annotated with {@link RowLevelSecurity}.
 * Used to trigger row-level filter application before method execution.
 */
public class RowLevelSecurityPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(final Method method,
                           final Class<?> targetClass) {
        return AnnotatedElementUtils.hasAnnotation(method, RowLevelSecurity.class);
    }
}
