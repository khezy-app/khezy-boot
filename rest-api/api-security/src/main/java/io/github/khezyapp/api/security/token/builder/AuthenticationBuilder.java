package io.github.khezyapp.api.security.token.builder;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Fluent builder for constructing or modifying Spring Security {@link Authentication} tokens.
 * Provides a consistent chainable API for setting principal, credentials, details, and authorities
 * across multiple authentication types (e.g. username/password, JWT, SAML, OAuth2).
 *
 * @param <B> the concrete builder type returned by mutators (self-type pattern)
 */
@SuppressWarnings("unchecked")
public interface AuthenticationBuilder<B extends AuthenticationBuilder<B>> {

    /**
     * Replaces authorities with the given varargs array.
     *
     * @param authorities the granted authorities to set
     * @return this builder for chaining
     */
    B authorities(GrantedAuthority... authorities);

    /**
     * Replaces authorities with the given collection.
     *
     * @param authorities the granted authorities to set
     * @return this builder for chaining
     */
    B authorities(Collection<? extends GrantedAuthority> authorities);

    /**
     * Allows custom modification of the authority list via a {@link Consumer}.
     *
     * @param authorities a consumer that receives the mutable authority collection
     * @return this builder for chaining
     */
    B authorities(Consumer<Collection<GrantedAuthority>> authorities);

    /**
     * Sets the credentials (e.g. password, token value) on the token under construction.
     *
     * @param credentials the credential object
     * @return this builder for chaining
     */
    B credentials(Object credentials);

    /**
     * Sets the principal (e.g. username, user object) on the token under construction.
     *
     * @param principal the principal object
     * @return this builder for chaining
     */
    B principal(Object principal);

    /**
     * Sets the details (e.g. remote address, session info) on the token under construction.
     *
     * @param details the details object
     * @return this builder for chaining
     */
    B details(Object details);

    /**
     * Adds a multi-factor authority marker to the token.
     * Subclasses should override to supply the appropriate factor authority.
     *
     * @return this builder for chaining
     */
    default B addFactorAuthority() {
        return (B) this;
    }

    /**
     * Builds the final {@link Authentication} instance with all configured properties.
     *
     * @return a fully constructed authentication token
     */
    Authentication build();

}
