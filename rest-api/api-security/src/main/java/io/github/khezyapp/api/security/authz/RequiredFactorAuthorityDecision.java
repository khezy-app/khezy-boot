package io.github.khezyapp.api.security.authz;

import io.github.khezyapp.api.security.authority.RequiredFactorError;
import lombok.Getter;
import org.springframework.security.authorization.AuthorizationDecision;

import java.io.Serial;
import java.util.Collection;

/**
 * An {@link AuthorizationDecision} that carries the list of missing factor
 * authorities when multi-factor authorization fails. Each missing authority
 * is wrapped in a {@link RequiredFactorError}.
 */
@Getter
public class RequiredFactorAuthorityDecision extends AuthorizationDecision {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Collection<RequiredFactorError> factorErrors;

    /**
     * Creates a decision from the original grant verdict and the set of authorities
     * that were missing. Each authority is converted to a {@link RequiredFactorError}.
     */
    public RequiredFactorAuthorityDecision(final boolean granted,
                                           final Collection<String> missingAuthorities) {
        super(granted);
        this.factorErrors = createError(missingAuthorities);
    }

    private Collection<RequiredFactorError> createError(final Collection<String> missingAuthorities) {
        return missingAuthorities.stream()
                .map(RequiredFactorError::createMissing)
                .toList();
    }

    /**
     * Returns a string representation showing the grant status and the list
     * of factor authorities that caused denial.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "granted=" + isGranted() + ", authorities=" + this.factorErrors + ']';
    }
}
