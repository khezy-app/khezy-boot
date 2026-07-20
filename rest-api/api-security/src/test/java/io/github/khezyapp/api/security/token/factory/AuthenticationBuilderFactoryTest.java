package io.github.khezyapp.api.security.token.factory;

import io.github.khezyapp.api.security.token.builder.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthenticationBuilderFactoryTest {

    @Test
    void usernamePasswordFactoryShouldSupportCorrectType() {
        final var factory = new UsernamePasswordAuthenticationBuilderFactory();
        assertThat(factory.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(factory.supports(String.class)).isFalse();
    }

    @Test
    void usernamePasswordFactoryShouldCreateBuilder() {
        final var auth = UsernamePasswordAuthenticationToken.unauthenticated("u", "p");
        final var builder = new UsernamePasswordAuthenticationBuilderFactory().create(auth);
        assertThat(builder).isInstanceOf(UsernamePasswordAuthenticationBuilder.class);
    }

    @Test
    void jwtFactoryShouldSupportCorrectType() {
        final var factory = new JwtTokenAuthenticationBuilderFactory();
        assertThat(factory.supports(JwtAuthenticationToken.class)).isTrue();
        assertThat(factory.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }

    @Test
    void jwtFactoryShouldCreateBuilder() {
        final var jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256").claim("sub", "u")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
                .build();
        final var auth = new JwtAuthenticationToken(jwt, List.of(), "u");
        final var builder = new JwtTokenAuthenticationBuilderFactory().create(auth);
        assertThat(builder).isInstanceOf(JwtTokenAuthenticationBuilder.class);
    }

    @Test
    void bearerTokenFactoryShouldSupportCorrectType() {
        final var factory = new BearerTokenAuthenticationBuilderFactory();
        assertThat(factory.supports(BearerTokenAuthentication.class)).isTrue();
        assertThat(factory.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }

    @Test
    void oneTimeTokenFactoryShouldSupportCorrectType() {
        final var factory = new OneTimeTokenAuthenticationBuilderFactory();
        assertThat(factory.supports(OneTimeTokenAuthenticationToken.class)).isTrue();
        assertThat(factory.supports(String.class)).isFalse();
    }

    @Test
    void oneTimeTokenFactoryShouldCreateBuilder() {
        final var auth = new OneTimeTokenAuthenticationToken("token");
        final var builder = new OneTimeTokenAuthenticationBuilderFactory().create(auth);
        assertThat(builder).isInstanceOf(OneTimeTokenAuthenticationBuilder.class);
    }

    @Test
    void x509FactoryShouldSupportCorrectType() {
        final var factory = new X509AuthenticationBuilderFactory();
        assertThat(factory.supports(PreAuthenticatedAuthenticationToken.class)).isTrue();
        assertThat(factory.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }

    @Test
    void x509FactoryShouldCreateBuilder() {
        final var auth = new PreAuthenticatedAuthenticationToken("u", null);
        final var builder = new X509AuthenticationBuilderFactory().create(auth);
        assertThat(builder).isInstanceOf(X509AuthenticationBuilder.class);
    }

    @Test
    void webAuthnFactoryShouldSupportCorrectType() {
        final var factory = new WebAuthnAuthenticationBuilderFactory();
        assertThat(factory.supports(WebAuthnAuthentication.class)).isTrue();
        assertThat(factory.supports(String.class)).isFalse();
    }

    @Test
    void casFactoryShouldSupportCorrectType() {
        final var factory = new CASTokenAuthenticationBuilderFactory();
        assertThat(factory.supports(CasAuthenticationToken.class)).isTrue();
        assertThat(factory.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }

    @Test
    void saml2FactoryShouldSupportCorrectType() {
        final var factory = new Saml2AuthenticationBuilderFactory();
        assertThat(factory.supports(Saml2Authentication.class)).isTrue();
        assertThat(factory.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }

    @Test
    void oauth2LoginFactoryShouldSupportCorrectType() {
        final var factory = new OAuth2LonginAuthenticationBuilderFactory();
        assertThat(factory.supports(OAuth2LoginAuthenticationToken.class)).isTrue();
        assertThat(factory.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }
}
