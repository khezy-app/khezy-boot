package io.github.khezyapp.api.security.token;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for extracting multi-factor authentication (MFA)
 * factor authorities from parsed token claims.
 */
public interface FactorExtractor {

    /**
     * Extracts MFA factor authority strings from the given claims.
     *
     * @param claims the token claims to extract factors from
     * @return list of factor authority strings, never {@code null}
     */
    List<String> extractFactors(Map<String, Object> claims);
}
