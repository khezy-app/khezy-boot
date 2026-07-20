package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

/**
 * Builder for {@link BearerTokenAuthentication}.
 * Preserves the OAuth2 access token from the source while allowing principal, authority, and details changes.
 *
 * @param <B> the concrete builder type returned by mutators
 */
@SuppressWarnings("unchecked")
public class BearerTokenAuthenticationBuilder<B extends BearerTokenAuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {
    private final OAuth2AccessToken token;

    /**
     * Creates a builder from an existing {@link BearerTokenAuthentication}.
     *
     * @param authentication the existing token to copy values from
     */
    public BearerTokenAuthenticationBuilder(final BearerTokenAuthentication authentication) {
        super(authentication);
        this.token = authentication.getToken();
    }

    @Override
    public B addFactorAuthority() {
        this.authorities.add(RequiredFactorAuthority.fromAuthority(RequiredFactorAuthority.BEARER_AUTHORITY));
        return (B) this;
    }

    @Override
    public BearerTokenAuthentication build() {
        final var bearerToken = new BearerTokenAuthentication(
                (OAuth2AuthenticatedPrincipal) this.principal,
                this.token,
                this.authorities
        );
        bearerToken.setDetails(this.details);
        return bearerToken;
    }
}
