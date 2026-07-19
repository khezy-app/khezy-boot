package io.github.khezyapp.api.security.expression;

import io.github.khezyapp.api.security.api.SecurityContextEnricher;
import io.github.khezyapp.api.security.registry.AuthorizationRuleRegistry;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;
import java.util.function.Supplier;

/**
 * Orchestrator responsible for instantiating the {@link KhezySecurityExpressionRoot}.
 * Aggregates data from all {@link SecurityContextEnricher} beans to build the {@link SecurityAttributeContext}.
 */
public class KhezyMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {
    private final List<SecurityContextEnricher> contextEnrichers;
    private final AuthorizationRuleRegistry authorizationRuleRegistry;

    public KhezyMethodSecurityExpressionHandler(final List<SecurityContextEnricher> contextEnrichers,
                                                final AuthorizationRuleRegistry authorizationRuleRegistry) {
        this.contextEnrichers = Objects.requireNonNullElse(contextEnrichers, Collections.emptyList());
        this.authorizationRuleRegistry = authorizationRuleRegistry;
    }

    /**
     * Internal factory method that assembles the custom expression root and populates
     * it with HTTP request data and enriched attributes.
     */
    @Override
    protected MethodSecurityExpressionOperations createSecurityExpressionRoot(
            final Authentication authentication,
            final MethodInvocation invocation
    ) {
        return createSecurityExpressionRoot(() -> authentication, invocation);
    }

    /**
     * Overrides the standard context creation to inject custom root and evaluation context.
     * Ensures parameters like {@code #id} are discoverable in expressions.
     */
    @Override
    public EvaluationContext createEvaluationContext(final Supplier<Authentication> authentication,
                                                     final MethodInvocation mi) {
        final var root = createSecurityExpressionRoot(authentication, mi);
        final var context = new CustomMethodSecurityEvaluationContext(root, mi, getParameterNameDiscoverer());
        context.setBeanResolver(getBeanResolver());
        return context;
    }

    private MethodSecurityExpressionOperations createSecurityExpressionRoot(
            final Supplier<Authentication> authentication,
            final MethodInvocation invocation
    ) {
        final var root = new KhezySecurityExpressionRoot(authentication);
        root.setThis(invocation.getThis());
        root.setPermissionEvaluator(getPermissionEvaluator());
        root.setTrustResolver(getTrustResolver());
        root.setRoleHierarchy(getRoleHierarchy());
        root.setDefaultRolePrefix(getDefaultRolePrefix());
        root.setAuthorizationRuleRegistry(authorizationRuleRegistry);

        final var requestAttribute = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        final var request = Optional.ofNullable(requestAttribute)
                .map(ServletRequestAttributes::getRequest)
                .orElse(null);
        final var attribute = new HashMap<String, Object>();
        contextEnrichers.forEach(enricher -> {
            final var additionalContext = enricher.getAdditionalContext();
            if (Objects.nonNull(additionalContext)) {
                attribute.putAll(additionalContext);
            }
        });

        final var attributeContext = new SecurityAttributeContext(request, attribute);
        root.setSecurityAttributeContext(attributeContext);

        return root;
    }
}
