package io.github.khezyapp.api.security.authority;

import lombok.Getter;
import lombok.ToString;
import org.springframework.util.Assert;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a missing required factor authority. Carries the authority
 * identifier and a {@link Reason} that explains why the factor is considered
 * unsatisfied.
 */
@ToString
public class RequiredFactorError implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final String authority;
    private final Reason reason;

    private RequiredFactorError(final String authority,
                                final Reason reason) {
        Assert.hasText(authority, "authority cannot be empty");
        Assert.notNull(reason, "reason cannot be null");
        this.authority = authority;
        this.reason = reason;
    }

    /**
     * Returns {@code true} if this error's reason is {@link Reason#MISSING},
     * indicating the factor authority was not found in the user's grants.
     */
    public boolean isMissing() {
        return this.reason == Reason.MISSING;
    }

    /**
     * Factory method for a {@code MISSING} error.
     *
     * @param authority the factor authority that is missing (e.g. {@code FACTOR_WEBAUTHN})
     * @return a new error with reason {@link Reason#MISSING}
     */
    public static RequiredFactorError createMissing(final String authority) {
        return new RequiredFactorError(authority, Reason.MISSING);
    }

    private enum Reason {
        MISSING
    }
}
