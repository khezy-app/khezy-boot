package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;

/**
 * Builder for {@link OneTimeTokenAuthenticationToken}.
 * Allows reconfiguration of a one-time token before building a new authenticated instance.
 *
 * @param <B> the concrete builder type returned by mutators
 */
@SuppressWarnings("unchecked")
public class OneTimeTokenAuthenticationBuilder<B extends OneTimeTokenAuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {

    /**
     * Creates a builder from an existing {@link OneTimeTokenAuthenticationToken}.
     *
     * @param authentication the existing token to copy values from
     */
    public OneTimeTokenAuthenticationBuilder(final OneTimeTokenAuthenticationToken authentication) {
        super(authentication);
    }

    @Override
    public B addFactorAuthority() {
        this.authorities.add(RequiredFactorAuthority.fromAuthority(RequiredFactorAuthority.OTT_AUTHORITY));
        return (B) this;
    }

    @Override
    public OneTimeTokenAuthenticationToken build() {
        final var oneTimeToken = OneTimeTokenAuthenticationToken.authenticated(this.principal, this.authorities);
        oneTimeToken.setDetails(this.details);
        return oneTimeToken;
    }
}
