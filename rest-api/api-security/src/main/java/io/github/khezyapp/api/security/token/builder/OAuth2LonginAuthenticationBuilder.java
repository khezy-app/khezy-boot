package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Builder for {@link OAuth2LoginAuthenticationToken}.
 * Preserves the access token, refresh token, authorization exchange, and client registration
 * from the source token while allowing principal, authority, and details changes.
 *
 * @param <B> the concrete builder type returned by mutators
 */
@SuppressWarnings("unchecked")
public class OAuth2LonginAuthenticationBuilder<B extends OAuth2LonginAuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {
    private final OAuth2AccessToken accessToken;
    private final OAuth2AuthorizationExchange authorizationExchange;
    private final OAuth2RefreshToken refreshToken;
    private final ClientRegistration clientRegistration;

    /**
     * Creates a builder from an existing {@link OAuth2LoginAuthenticationToken}.
     *
     * @param authentication the existing token to copy values from
     */
    public OAuth2LonginAuthenticationBuilder(final OAuth2LoginAuthenticationToken authentication) {
        super(authentication);
        this.accessToken = authentication.getAccessToken();
        this.authorizationExchange = authentication.getAuthorizationExchange();
        this.refreshToken = authentication.getRefreshToken();
        this.clientRegistration = authentication.getClientRegistration();
    }

    @Override
    public B addFactorAuthority() {
        this.authorities.add(RequiredFactorAuthority.fromAuthority(RequiredFactorAuthority.AUTHORIZATION_CODE_AUTHORITY));
        return (B) this;
    }

    @Override
    public OAuth2LoginAuthenticationToken build() {
        final var oauth2Login = new OAuth2LoginAuthenticationToken(
                this.clientRegistration,
                this.authorizationExchange,
                (OAuth2User) this.principal,
                this.authorities,
                this.accessToken,
                this.refreshToken
        );
        oauth2Login.setDetails(this.details);
        return oauth2Login;
    }
}
