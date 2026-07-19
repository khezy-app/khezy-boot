package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;

/**
 * Builder for {@link Saml2Authentication}.
 * Preserves the SAML response string and principal from the source token while allowing
 * authority and details changes.
 *
 * @param <B> the concrete builder type returned by mutators
 */
@SuppressWarnings("unchecked")
public class Saml2AuthenticationBuilder<B extends Saml2AuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {
    private final String saml2Response;
    private final AuthenticatedPrincipal principal;

    /**
     * Creates a builder from an existing {@link Saml2Authentication}.
     *
     * @param authentication the existing token to copy values from
     */
    public Saml2AuthenticationBuilder(final Saml2Authentication authentication) {
        super(authentication);
        this.principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        this.saml2Response = authentication.getSaml2Response();
    }

    @Override
    public B addFactorAuthority() {
        this.authorities.add(RequiredFactorAuthority.fromAuthority(RequiredFactorAuthority.SAML_RESPONSE_AUTHORITY));
        return (B) this;
    }

    @Override
    public Saml2Authentication build() {
        final var saml2 = new Saml2Authentication(this.principal, this.saml2Response ,this.authorities);
        saml2.setDetails(this.details);
        return saml2;
    }
}
