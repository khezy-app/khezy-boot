package io.github.khezyapp.api.security.expression;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.parameters.DefaultSecurityParameterNameDiscoverer;

import java.lang.reflect.Method;

/**
 * Specialized SpEL evaluation context that supports method-based parameter resolution.
 * Allows security expressions to reference method arguments by name (e.g., {@code #id}).
 */
public class CustomMethodSecurityEvaluationContext extends MethodBasedEvaluationContext {

    public CustomMethodSecurityEvaluationContext(final Authentication user,
                                                 final MethodInvocation mi) {
        this(user, mi, new DefaultSecurityParameterNameDiscoverer());
    }

    public CustomMethodSecurityEvaluationContext(final Authentication user,
                                                 final MethodInvocation mi,
                                                 final ParameterNameDiscoverer parameterNameDiscoverer) {
        super(mi.getThis(), getSpecificMethod(mi), mi.getArguments(), parameterNameDiscoverer);
    }

    public CustomMethodSecurityEvaluationContext(final MethodSecurityExpressionOperations root,
                                                 final MethodInvocation mi,
                                                 final ParameterNameDiscoverer parameterNameDiscoverer) {
        super(root, getSpecificMethod(mi), mi.getArguments(), parameterNameDiscoverer);
    }

    private static Method getSpecificMethod(final MethodInvocation mi) {
        return AopUtils.getMostSpecificMethod(mi.getMethod(), AopProxyUtils.ultimateTargetClass(mi.getThis()));
    }
}
