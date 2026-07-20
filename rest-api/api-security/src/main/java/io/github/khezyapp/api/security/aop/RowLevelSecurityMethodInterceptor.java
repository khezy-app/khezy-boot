package io.github.khezyapp.api.security.aop;

import io.github.khezyapp.api.security.Expressions;
import io.github.khezyapp.api.security.annotation.RowLevelSecurity;
import io.github.khezyapp.api.security.expression.KhezySecurityExpressionRoot;
import io.github.khezyapp.api.security.registry.RowLevelSecurityRuleRegistry;
import jakarta.persistence.EntityManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.Session;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;

/**
 * AOP Method Interceptor that manages the lifecycle of Hibernate filters.
 * Intercepts methods annotated with {@link RowLevelSecurity} to apply data isolation rules.
 */
@RequiredArgsConstructor
public class RowLevelSecurityMethodInterceptor implements MethodInterceptor {
    @Getter
    private final ObjectProvider<EntityManager> entityManager;
    private final MethodSecurityExpressionHandler expressionHandler;
    private final RowLevelSecurityRuleRegistry ruleRegistry;

    /**
     * Extracts {@link RowLevelSecurity} annotations, evaluates SpEL conditions,
     * and enables Hibernate filters before proceeding with method execution.
     * Disables filters in a {@code finally} block to prevent cross-request contamination.
     */
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        Session session = null;

        final var method = getSpecificMethod(invocation);
        final var annotations = AnnotatedElementUtils.findAllMergedAnnotations(method, RowLevelSecurity.class);
        final var enabledFilters = new ArrayList<String>();

        try {
            session = entityManager.getObject().unwrap(Session.class);
            final var evalContext = createEvaluationContext(invocation);

            for (final var rls : annotations) {
                final var ruleName = rls.ruleName();
                if (StringUtils.hasText(ruleName)) {
                    ruleRegistry.enableFilter(
                            ruleName,
                            (KhezySecurityExpressionRoot) evalContext,
                            rls,
                            invocation.getArguments()
                    );
                } else {
                    final var enabledCondition = evaluateCondition(rls.condition(), evalContext);

                    if (enabledCondition) {
                        final var paramValue = Expressions.evaluate(rls.expression(), evalContext);
                        if (Objects.nonNull(paramValue)) {
                            final var filterName = rls.filterName();
                            final var filter = session.enableFilter(filterName);
                            filter.setParameter(rls.parameterName(), paramValue);
                            enabledFilters.add(filterName);
                        }
                    }
                }
            }

            return invocation.proceed();
        } finally {
            if (Objects.nonNull(session)) {
                enabledFilters.forEach(session::disableFilter);
            }
        }
    }

    /**
     * Determines if a filter should be enabled based on a SpEL boolean expression.
     */
    private boolean evaluateCondition(final String condition,
                                      final EvaluationContext context) {
        if (StringUtils.hasText(condition)) {
            if ("true".equals(condition)) {
                return true;
            }
            return Expressions.evaluate(condition, context, Boolean.class);
        }
        return true;
    }

    private EvaluationContext createEvaluationContext(final MethodInvocation invocation) {
        return expressionHandler.createEvaluationContext(
                () -> SecurityContextHolder.getContext().getAuthentication(),
                invocation
        );
    }

    private static Method getSpecificMethod(final MethodInvocation invocation) {
        return AopUtils.getMostSpecificMethod(
                invocation.getMethod(),
                AopProxyUtils.ultimateTargetClass(invocation.getThis())
        );
    }
}
