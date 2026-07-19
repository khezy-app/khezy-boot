package io.github.khezyapp.api.security.expression;

import io.github.khezyapp.api.security.registry.AuthorizationRuleRegistry;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Custom expression root that defines the DSL available within SpEL security expressions.
 * Extends standard Spring Security operations with domain-specific utilities and rule-based checks.
 */
public class KhezySecurityExpressionRoot extends SecurityExpressionRoot implements MethodSecurityExpressionOperations {

    private Object filterObject;
    private Object returnObject;
    private Object target;

    /**
     * Registry used to resolve and execute named {@link io.github.khezyapp.api.security.api.AuthorizationRule} logic.
     * */
    @Getter
    @Setter
    private SecurityAttributeContext securityAttributeContext;

    /** Enriched metadata used for attribute-based access control (ABAC). */
    @Getter
    @Setter
    private AuthorizationRuleRegistry authorizationRuleRegistry;

    public KhezySecurityExpressionRoot(final Authentication authentication) {
        super(authentication);
    }

    public KhezySecurityExpressionRoot(final Supplier<Authentication> authentication) {
        super(authentication);
    }

    @Override
    public void setFilterObject(final Object filterObject) {
        this.filterObject = filterObject;
    }

    @Override
    public Object getFilterObject() {
        return filterObject;
    }

    @Override
    public void setReturnObject(final Object returnObject) {
        this.returnObject = returnObject;
    }

    @Override
    public Object getReturnObject() {
        return returnObject;
    }

    public void setThis(final Object target) {
        this.target = target;
    }

    @Override
    public Object getThis() {
        return target;
    }

    // ---------------- Expose utilities method can be use with annotation ----------------

    /**
     * Checks if a specific HTTP header matches a value.
     *
     * @param headerName The name of the header to inspect.
     * @param headerValue The value to compare against.
     * @return true if the header exists and matches the value.
     */
    public boolean hasHeaderValue(final String headerName,
                                  final String headerValue) {
        return hasAnyHeaderValue(headerName, Collections.singletonList(headerValue));
    }


    public boolean hasAnyHeaderValue(final String headerName,
                                     final List<String> headersValue) {
        final var request = securityAttributeContext.getRequest();
        if (Objects.isNull(request)) {
            return false;
        }
        final var headers = request.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            final var value = headers.nextElement();
            if (headersValue.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Entry point for custom business logic rules.
     *
     * @param ruleName The unique identifier of the {@link io.github.khezyapp.api.security.api.AuthorizationRule}.
     * @param params Optional arguments passed from the annotation.
     * @return true if the rule evaluation passes.
     */
    public boolean check(final String ruleName,
                         final Object... params) {
        return authorizationRuleRegistry.check(ruleName, this, params);
    }
}
