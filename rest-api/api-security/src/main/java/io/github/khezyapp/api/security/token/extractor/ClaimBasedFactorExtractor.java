package io.github.khezyapp.api.security.token.extractor;

import io.github.khezyapp.api.security.token.FactorExtractor;
import io.github.khezyapp.api.security.util.FactorAuthorities;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link FactorExtractor} that reads MFA factor authorities
 * from the {@code "factors"} claim in parsed token claims.
 * Returns factor authority strings with the {@code FACTOR_} prefix.
 */
public class ClaimBasedFactorExtractor implements FactorExtractor {

    static final String DEFAULT_FACTORS_CLAIM = "factors";

    private final String factorsClaim;

    public ClaimBasedFactorExtractor() {
        this(DEFAULT_FACTORS_CLAIM);
    }

    public ClaimBasedFactorExtractor(final String factorsClaim) {
        this.factorsClaim = Objects.requireNonNull(factorsClaim, "factorsClaim must not be null");
    }

    @Override
    public List<String> extractFactors(final Map<String, Object> claims) {
        if (Objects.isNull(claims)) {
            return Collections.emptyList();
        }
        final var raw = claims.get(factorsClaim);
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(FactorAuthorities::getFactorAuthorityFromMethod)
                    .toList();
        }
        return Collections.emptyList();
    }
}
