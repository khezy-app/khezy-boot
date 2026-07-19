package io.github.khezyapp.api.security.token.builder;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class AuthenticationBuilderTest {

    // --- UsernamePassword ---

    @Test
    void usernamePasswordShouldBuildCorrectToken() {
        final var source = UsernamePasswordAuthenticationToken.unauthenticated("user", "pass");
        final var builder = new UsernamePasswordAuthenticationBuilder<>(source);
        builder.authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")));

        final var result = builder.build();

        assertThat(result).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isEqualTo("user");
        assertThat(result.getCredentials()).isEqualTo("pass");
    }

    @Test
    void usernamePasswordShouldAppendFactorAuthority() {
        final var source = UsernamePasswordAuthenticationToken.unauthenticated("user", "pass");
        final var builder = new UsernamePasswordAuthenticationBuilder<>(source);
        builder.addFactorAuthority();

        final var result = builder.build();
        assertThat(result.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals(RequiredFactorAuthority.PASSWORD_AUTHORITY));
    }

    // --- JWT ---

    @Test
    void jwtShouldBuildCorrectToken() {
        final var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        final var source = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")), "user");
        final var builder = new JwtTokenAuthenticationBuilder<>(source);

        final var result = builder.build();

        assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(result.getName()).isEqualTo("user");
        assertThat(result.getToken()).isSameAs(jwt);
    }

    @Test
    void jwtShouldNotAddFactorAuthority() {
        final var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        final var source = new JwtAuthenticationToken(jwt, null, "user");
        final var builder = new JwtTokenAuthenticationBuilder<>(source);

        builder.addFactorAuthority();
        final var result = builder.build();

        assertThat(result.getAuthorities()).isEmpty();
    }

    // --- Bearer ---

    @Test
    void bearerTokenShouldBuildCorrectToken() {
        final var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "bearer-token",
                Instant.now(), Instant.now().plusSeconds(3600));
        final var principal = mock(OAuth2AuthenticatedPrincipal.class);
        when(principal.getAttributes()).thenReturn(Map.of());
        final var source = new BearerTokenAuthentication(
                principal, accessToken, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        final var builder = new BearerTokenAuthenticationBuilder<>(source);
        builder.authorities(new SimpleGrantedAuthority("ROLE_USER"));

        final var result = builder.build();

        assertThat(result).isInstanceOf(BearerTokenAuthentication.class);
        assertThat(result.getToken()).isSameAs(accessToken);
    }

    @Test
    void bearerTokenShouldAppendFactorAuthority() {
        final var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "bearer-token",
                Instant.now(), Instant.now().plusSeconds(3600));
        final var principal = mock(OAuth2AuthenticatedPrincipal.class);
        when(principal.getAttributes()).thenReturn(Map.of());
        final var source = new BearerTokenAuthentication(
                principal, accessToken, List.of());
        final var builder = new BearerTokenAuthenticationBuilder<>(source);
        builder.addFactorAuthority();

        final var result = builder.build();
        assertThat(result.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals(RequiredFactorAuthority.BEARER_AUTHORITY));
    }

    // --- OneTimeToken ---

    @Test
    void oneTimeTokenShouldBuildCorrectToken() {
        final var source = new OneTimeTokenAuthenticationToken("token-value");
        final var builder = new OneTimeTokenAuthenticationBuilder<>(source);
        builder.authorities(new SimpleGrantedAuthority("ROLE_USER"));

        final var result = builder.build();

        assertThat(result).isInstanceOf(OneTimeTokenAuthenticationToken.class);
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void oneTimeTokenShouldAppendFactorAuthority() {
        final var source = new OneTimeTokenAuthenticationToken("token-value");
        final var builder = new OneTimeTokenAuthenticationBuilder<>(source);
        builder.addFactorAuthority();

        final var result = builder.build();
        assertThat(result.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals(RequiredFactorAuthority.OTT_AUTHORITY));
    }

    // --- X509 ---

    @Test
    void x509ShouldBuildCorrectToken() {
        final var source = new PreAuthenticatedAuthenticationToken("user", null);
        final var builder = new X509AuthenticationBuilder<>(source);
        builder.authorities(new SimpleGrantedAuthority("ROLE_USER"));

        final var result = builder.build();

        assertThat(result).isInstanceOf(PreAuthenticatedAuthenticationToken.class);
        assertThat(result.getPrincipal()).isEqualTo("user");
    }

    @Test
    void x509ShouldAppendFactorAuthority() {
        final var source = new PreAuthenticatedAuthenticationToken("user", null,
                List.of());
        final var builder = new X509AuthenticationBuilder<>(source);
        builder.addFactorAuthority();

        final var result = builder.build();
        assertThat(result.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals(RequiredFactorAuthority.X509_AUTHORITY));
    }

    // --- AbstractBuilder tests ---

    @Test
    void abstractBuilderShouldMergeAuthoritiesFromSource() {
        final var source = UsernamePasswordAuthenticationToken.unauthenticated("user", "pass");
        source.setDetails("details123");

        final class TestBuilder extends AbstractAuthenticationBuilder<TestBuilder> {
            TestBuilder(final UsernamePasswordAuthenticationToken a) {
                super(a);
            }

            public UsernamePasswordAuthenticationToken build() {
                final var result = new UsernamePasswordAuthenticationToken(
                        this.principal, this.credentials, this.authorities);
                result.setDetails(this.details);
                return result;
            }
        }

        final var builder = new TestBuilder(source);
        final var tokenResult = builder.build();

        assertThat(tokenResult.getPrincipal()).isEqualTo("user");
        assertThat(tokenResult.getCredentials()).isEqualTo("pass");
        assertThat(tokenResult.getDetails()).isEqualTo("details123");
    }

    @Test
    void abstractBuilderShouldSkipDuplicateAuthorities() {
        final var source = UsernamePasswordAuthenticationToken.unauthenticated("user", "pass");
        final class TestBuilder extends AbstractAuthenticationBuilder<TestBuilder> {
            TestBuilder(final UsernamePasswordAuthenticationToken a) {
                super(a);
            }

            public UsernamePasswordAuthenticationToken build() {
                return new UsernamePasswordAuthenticationToken(
                        this.principal, this.credentials, this.authorities);
            }
        }

        final var builder = new TestBuilder(source);
        final var auth = new SimpleGrantedAuthority("ROLE_USER");
        builder.authorities(auth);
        builder.authorities(auth);

        final var result = builder.build();
        assertThat(result.getAuthorities()).hasSize(1);
    }

    @Test
    void abstractBuilderShouldAcceptConsumer() {
        final var source = UsernamePasswordAuthenticationToken.unauthenticated("user", "pass");
        final class TestBuilder extends AbstractAuthenticationBuilder<TestBuilder> {
            TestBuilder(final UsernamePasswordAuthenticationToken a) {
                super(a);
            }

            public UsernamePasswordAuthenticationToken build() {
                return new UsernamePasswordAuthenticationToken(
                        this.principal, this.credentials, this.authorities);
            }
        }

        final var builder = new TestBuilder(source);
        builder.authorities(auths -> auths.add(new SimpleGrantedAuthority("ROLE_ADMIN")));

        final var result = builder.build();
        assertThat(result.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // --- WebAuthn ---

    @Test
    void webAuthnShouldBuildCorrectToken() {
        final var userEntity = mock(PublicKeyCredentialUserEntity.class);
        final var source = new WebAuthnAuthentication(userEntity,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        final var builder = new WebAuthnAuthenticationBuilder<>(source);

        final var result = builder.build();

        assertThat(result).isInstanceOf(WebAuthnAuthentication.class);
        assertThat(result.getPrincipal()).isSameAs(userEntity);
    }

    @Test
    void webAuthnShouldAppendFactorAuthority() {
        final var userEntity = mock(PublicKeyCredentialUserEntity.class);
        final var source = new WebAuthnAuthentication(userEntity, List.of());
        final var builder = new WebAuthnAuthenticationBuilder<>(source);
        builder.addFactorAuthority();

        final var result = builder.build();
        assertThat(result.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals(RequiredFactorAuthority.WEBAUTHN_AUTHORITY));
    }

    // --- SAML2 ---

    @Test
    void saml2ShouldBuildCorrectToken() {
        final var principal = mock(AuthenticatedPrincipal.class);
        when(principal.getName()).thenReturn("saml-user");
        final var source = new Saml2Authentication(principal, "saml-response", List.of());
        final var builder = new Saml2AuthenticationBuilder<>(source);
        builder.authorities(new SimpleGrantedAuthority("ROLE_USER"));

        final var result = builder.build();

        assertThat(result).isInstanceOf(Saml2Authentication.class);
        assertThat(result.getSaml2Response()).isEqualTo("saml-response");
        assertThat(result.getPrincipal()).isSameAs(principal);
    }

    @Test
    void saml2ShouldAppendFactorAuthority() {
        final var principal = mock(AuthenticatedPrincipal.class);
        final var source = new Saml2Authentication(principal, "saml-response", List.of());
        final var builder = new Saml2AuthenticationBuilder<>(source);
        builder.addFactorAuthority();

        final var result = builder.build();
        assertThat(result.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals(RequiredFactorAuthority.SAML_RESPONSE_AUTHORITY));
    }

    // --- CAS ---

    @Test
    void casShouldBuildCorrectToken() {
        final var assertion = mock(org.apereo.cas.client.validation.Assertion.class);
        final var userDetails = mock(UserDetails.class);
        final var source = new CasAuthenticationToken("key", "user", "cred",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), userDetails, assertion);
        final var builder = new CASTokenAuthenticationBuilder<>(source);

        final var result = builder.build();

        assertThat(result).isInstanceOf(CasAuthenticationToken.class);
        assertThat(result.getPrincipal()).isEqualTo("user");
        assertThat(result.getCredentials()).isEqualTo("cred");
    }

    @Test
    void casShouldAppendFactorAuthority() {
        final var assertion = mock(org.apereo.cas.client.validation.Assertion.class);
        final var userDetails = mock(UserDetails.class);
        final var source = new CasAuthenticationToken("key", "user", "cred",
                List.of(), userDetails, assertion);
        final var builder = new CASTokenAuthenticationBuilder<>(source);
        builder.addFactorAuthority();

        final var result = builder.build();
        assertThat(result.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals(RequiredFactorAuthority.CAS_AUTHORITY));
    }

    // --- OAuth2Login ---

    @Test
    void oauth2LoginShouldBuildCorrectToken() {
        final var clientRegistration = ClientRegistration.withRegistrationId("test")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("client-id")
                .redirectUri("http://localhost")
                .authorizationUri("http://auth")
                .tokenUri("http://token")
                .build();
        final var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "token-value",
                Instant.now(), Instant.now().plusSeconds(3600));
        final var oauth2User = mock(OAuth2User.class);
        final var exchange = mock(OAuth2AuthorizationExchange.class);
        final var source = mock(OAuth2LoginAuthenticationToken.class);
        when(source.getClientRegistration()).thenReturn(clientRegistration);
        when(source.getAccessToken()).thenReturn(accessToken);
        when(source.getRefreshToken()).thenReturn(null);
        when(source.getAuthorizationExchange()).thenReturn(exchange);
        when(source.getPrincipal()).thenReturn(oauth2User);
        when(source.getAuthorities()).thenReturn(List.of());
        when(source.getCredentials()).thenReturn(null);
        when(source.getDetails()).thenReturn(null);

        final var builder = new OAuth2LonginAuthenticationBuilder<>(source);

        final var tokenResult = builder.build();

        assertThat(tokenResult).isInstanceOf(OAuth2LoginAuthenticationToken.class);
        assertThat(tokenResult.getClientRegistration()).isSameAs(clientRegistration);
    }

    @Test
    void oauth2LoginShouldAppendFactorAuthority() {
        final var clientRegistration = ClientRegistration.withRegistrationId("test")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("id").redirectUri("http://localhost")
                .authorizationUri("http://auth").tokenUri("http://token")
                .build();
        final var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "token-value",
                Instant.now(), Instant.now().plusSeconds(3600));
        final var exchange = mock(OAuth2AuthorizationExchange.class);
        final var source = mock(OAuth2LoginAuthenticationToken.class);
        when(source.getClientRegistration()).thenReturn(clientRegistration);
        when(source.getAccessToken()).thenReturn(accessToken);
        when(source.getRefreshToken()).thenReturn(null);
        when(source.getAuthorizationExchange()).thenReturn(exchange);
        when(source.getPrincipal()).thenReturn(mock(OAuth2User.class));
        when(source.getAuthorities()).thenReturn(List.of());
        when(source.getCredentials()).thenReturn(null);
        when(source.getDetails()).thenReturn(null);

        final var builder = new OAuth2LonginAuthenticationBuilder<>(source);
        builder.addFactorAuthority();

        final var tokenResult = builder.build();
        assertThat(tokenResult.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals(RequiredFactorAuthority.AUTHORIZATION_CODE_AUTHORITY));
    }
}
