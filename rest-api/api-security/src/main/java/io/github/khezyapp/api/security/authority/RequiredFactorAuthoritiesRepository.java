package io.github.khezyapp.api.security.authority;

import java.util.List;

/**
 * Repository contract for looking up which multi-factor authorities are required
 * for a given user. Implementations can source these requirements from a database,
 * configuration file, or any other backing store.
 */
public interface RequiredFactorAuthoritiesRepository {

    /**
     * Returns the list of factor authorities (e.g. {@code FACTOR_WEBAUTHN})
     * that the specified user must satisfy.
     *
     * @param username the user to look up
     * @return list of required factor authority strings, never {@code null}
     */
    List<String> findRequiredFactorAuthorities(String username);
}
