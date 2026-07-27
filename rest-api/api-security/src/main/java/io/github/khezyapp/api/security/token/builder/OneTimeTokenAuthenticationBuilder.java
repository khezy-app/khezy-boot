package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.springframework.security.authentication.ott.OneTimeTokenAuthentication;

/**
 * Builder for {@link OneTimeTokenAuthentication}.
 * Allows reconfiguration of a one-time token before building a new authenticated instance.
 *
 * @param <B> the concrete builder type returned by mutators
 */
@SuppressWarnings("unchecked")
public class OneTimeTokenAuthenticationBuilder<B extends OneTimeTokenAuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {

    /**
     * Creates a builder from an existing {@link OneTimeTokenAuthentication}.
     *
     * @param authentication the existing token to copy values from
     */
    public OneTimeTokenAuthenticationBuilder(final OneTimeTokenAuthentication authentication) {
        super(authentication);
    }

    @Override
    public B addFactorAuthority() {
        this.authorities.add(RequiredFactorAuthority.fromAuthority(RequiredFactorAuthority.OTT_AUTHORITY));
        return (B) this;
    }

    @Override
    public OneTimeTokenAuthentication build() {
        final var oneTimeToken = new OneTimeTokenAuthentication(this.principal, this.authorities);
        oneTimeToken.setDetails(this.details);
        return oneTimeToken;
    }
}
