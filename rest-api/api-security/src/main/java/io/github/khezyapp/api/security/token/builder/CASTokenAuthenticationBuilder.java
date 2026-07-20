package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.apereo.cas.client.validation.Assertion;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Builder for {@link CasAuthenticationToken}.
 * Preserves the key hash, CAS assertion, and user details from the source token while allowing
 * principal, credential, authority, and details changes.
 *
 * @param <B> the concrete builder type returned by mutators
 */
@SuppressWarnings("unchecked")
public class CASTokenAuthenticationBuilder<B extends CASTokenAuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {

    private final int keyHash;
    private final Assertion assertion;
    private final UserDetails userDetails;

    /**
     * Creates a builder from an existing {@link CasAuthenticationToken}.
     *
     * @param authentication the existing token to copy values from
     */
    public CASTokenAuthenticationBuilder(final CasAuthenticationToken authentication) {
        super(authentication);
        this.keyHash = authentication.getKeyHash();
        this.assertion = authentication.getAssertion();
        this.userDetails = authentication.getUserDetails();
    }

    @Override
    public B addFactorAuthority() {
        this.authorities.add(RequiredFactorAuthority.fromAuthority(RequiredFactorAuthority.CAS_AUTHORITY));
        return (B) this;
    }

    @Override
    public CasAuthenticationToken build() {
        final var casToken = new CasAuthenticationToken(
                this.keyHash + "",
                this.principal,
                this.credentials,
                this.authorities,
                this.userDetails,
                this.assertion
        );
        casToken.setDetails(this.details);
        return casToken;
    }
}
