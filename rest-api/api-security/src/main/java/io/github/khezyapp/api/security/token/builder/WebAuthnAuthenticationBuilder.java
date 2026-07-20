package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

/**
 * Builder for {@link WebAuthnAuthentication}.
 * Preserves the WebAuthn {@link PublicKeyCredentialUserEntity} principal from the source token
 * while allowing authority and details changes.
 *
 * @param <B> the concrete builder type returned by mutators
 */
@SuppressWarnings("unchecked")
public class WebAuthnAuthenticationBuilder<B extends WebAuthnAuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {
    private final PublicKeyCredentialUserEntity principal;

    /**
     * Creates a builder from an existing {@link WebAuthnAuthentication}.
     *
     * @param authentication the existing token to copy values from
     */
    public WebAuthnAuthenticationBuilder(final WebAuthnAuthentication authentication) {
        super(authentication);
        this.principal = authentication.getPrincipal();
    }

    @Override
    public B addFactorAuthority() {
        this.authorities.add(RequiredFactorAuthority.fromAuthority(RequiredFactorAuthority.WEBAUTHN_AUTHORITY));
        return (B) this;
    }

    @Override
    public WebAuthnAuthentication build() {
        final var webAuthn = new WebAuthnAuthentication(this.principal, this.authorities);
        webAuthn.setDetails(this.details);
        return webAuthn;
    }
}
