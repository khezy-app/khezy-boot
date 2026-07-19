package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Builder for {@link UsernamePasswordAuthenticationToken}.
 * Pre-populates from an existing token and allows reconfiguration before building a new instance.
 *
 * @param <B> the concrete builder type returned by mutators
 */
@SuppressWarnings("unchecked")
public class UsernamePasswordAuthenticationBuilder<B extends UsernamePasswordAuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {

    /**
     * Creates a builder from an existing {@link UsernamePasswordAuthenticationToken}.
     *
     * @param authentication the existing token to copy values from
     */
    public UsernamePasswordAuthenticationBuilder(final UsernamePasswordAuthenticationToken authentication) {
        super(authentication);
    }

    @Override
    public B addFactorAuthority() {
        this.authorities.add(RequiredFactorAuthority.fromAuthority(RequiredFactorAuthority.PASSWORD_AUTHORITY));
        return (B) this;
    }

    @Override
    public UsernamePasswordAuthenticationToken build() {
        final var username = new UsernamePasswordAuthenticationToken(this.principal, this.credentials, this.authorities);
        username.setDetails(this.details);
        return username;
    }
}
