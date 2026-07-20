package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.token.factory.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuthenticationBuilderFactoryConfig {

    @Bean
    @ConditionalOnMissingBean(UsernamePasswordAuthenticationBuilderFactory.class)
    UsernamePasswordAuthenticationBuilderFactory usernamePasswordAuthenticationBuilderFactory() {
        return new UsernamePasswordAuthenticationBuilderFactory();
    }

    @Bean
    @ConditionalOnMissingBean(OneTimeTokenAuthenticationBuilderFactory.class)
    OneTimeTokenAuthenticationBuilderFactory oneTimeTokenAuthenticationBuilderFactory() {
        return new OneTimeTokenAuthenticationBuilderFactory();
    }

    @Bean
    @ConditionalOnMissingBean(X509AuthenticationBuilderFactory.class)
    X509AuthenticationBuilderFactory x509AuthenticationBuilderFactory() {
        return new X509AuthenticationBuilderFactory();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
            name = "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken")
    static class OAuth2ResourceServerConfiguration {

        @Bean
        @ConditionalOnMissingBean(JwtTokenAuthenticationBuilderFactory.class)
        JwtTokenAuthenticationBuilderFactory jwtTokenAuthenticationBuilderFactory() {
            return new JwtTokenAuthenticationBuilderFactory();
        }

        @Bean
        @ConditionalOnMissingBean(BearerTokenAuthenticationBuilderFactory.class)
        BearerTokenAuthenticationBuilderFactory bearerTokenAuthenticationBuilderFactory() {
            return new BearerTokenAuthenticationBuilderFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
            name = "org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken")
    static class OAuth2ClientConfiguration {

        @Bean
        @ConditionalOnMissingBean(OAuth2LonginAuthenticationBuilderFactory.class)
        OAuth2LonginAuthenticationBuilderFactory oAuth2LonginAuthenticationBuilderFactory() {
            return new OAuth2LonginAuthenticationBuilderFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.cas.authentication.CasAuthenticationToken")
    static class CASConfiguration {

        @Bean
        @ConditionalOnMissingBean()
        CASTokenAuthenticationBuilderFactory casTokenAuthenticationBuilderFactory() {
            return new CASTokenAuthenticationBuilderFactory();
        }
    }


    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
            name = "org.springframework.security.saml2.provider.service.authentication.Saml2Authentication")
    static class Saml2Configuration {

        @Bean
        @ConditionalOnMissingBean(Saml2AuthenticationBuilderFactory.class)
        Saml2AuthenticationBuilderFactory saml2AuthenticationBuilderFactory() {
            return new Saml2AuthenticationBuilderFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
            name = "org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication")
    static class WebAuthnConfiguration {

        @Bean
        @ConditionalOnMissingBean(WebAuthnAuthenticationBuilderFactory.class)
        WebAuthnAuthenticationBuilderFactory webAuthnAuthenticationBuilderFactory() {
            return new WebAuthnAuthenticationBuilderFactory();
        }
    }
}
