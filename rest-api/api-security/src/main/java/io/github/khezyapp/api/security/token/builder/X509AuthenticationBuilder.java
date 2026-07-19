package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Builder for X.509 certificate-based {@link PreAuthenticatedAuthenticationToken}.
 * Allows reconfiguration of a pre-authenticated token before building a new instance.
 *
 * @param <B> the concrete builder type returned by mutators
 */
@SuppressWarnings("unchecked")
public class X509AuthenticationBuilder<B extends X509AuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {

    /**
     * Creates a builder from an existing {@link PreAuthenticatedAuthenticationToken}.
     *
     * @param authentication the existing token to copy values from
     */
    public X509AuthenticationBuilder(final PreAuthenticatedAuthenticationToken authentication) {
        super(authentication);
    }


    @Override
    public B addFactorAuthority() {
        this.authorities.add(RequiredFactorAuthority.fromAuthority(RequiredFactorAuthority.X509_AUTHORITY));
        return (B) this;
    }

    @Override
    public PreAuthenticatedAuthenticationToken build() {
        final var result = new PreAuthenticatedAuthenticationToken(
                this.principal, this.credentials, this.authorities);
        result.setDetails(this.details);
        return result;
    }
}
