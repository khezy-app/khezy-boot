package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.api.AuthorizationRule;
import io.github.khezyapp.api.security.api.SecurityContextEnricher;
import io.github.khezyapp.api.security.autoconfigure.properties.KhezyCorsProperties;
import io.github.khezyapp.api.security.expression.KhezyMethodSecurityExpressionHandler;
import io.github.khezyapp.api.security.registry.AuthorizationRuleRegistry;
import io.github.khezyapp.api.security.token.AuthenticationBuilderManager;
import io.github.khezyapp.api.security.token.factory.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;

@Configuration(proxyBeanMethods = false)
public class KhezyApiSecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    AuthenticationBuilderManager authenticationBuilderManager(
            final ObjectProvider<AuthenticationBuilderFactory> provider
    ) {
        final var uniqueFactories = new LinkedHashMap<Class<?>, AuthenticationBuilderFactory>();
        provider.stream().forEach(factory -> {
            final var implementationClass = factory.getClass();
            uniqueFactories.putIfAbsent(implementationClass, factory);
        });

        return new AuthenticationBuilderManager(new ArrayList<>(uniqueFactories.values()));
    }

    @Bean
    @ConditionalOnMissingBean
    static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    @Bean
    @ConditionalOnMissingBean
    static KhezyMethodSecurityExpressionHandler methodSecurityExpressionHandler(
            final ObjectProvider<SecurityContextEnricher> contextEnricherObjectProvider,
            final ObjectProvider<AuthorizationRule> authorizationRuleObjectProvider
    ) {
        final var contextEnrichers = new ArrayList<SecurityContextEnricher>();
        final var authorizationRules = new ArrayList<AuthorizationRule>();

        contextEnricherObjectProvider.stream().forEach(contextEnrichers::add);
        authorizationRuleObjectProvider.stream().forEach(authorizationRules::add);

        return new KhezyMethodSecurityExpressionHandler(
                contextEnrichers,
                new AuthorizationRuleRegistry(authorizationRules)
        );
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty(prefix = "khezy.api.cors", name = "enabled")
    @ConditionalOnClass(name = "org.springframework.web.cors.UrlBasedCorsConfigurationSource")
    static class CorsConfigurationSourceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public UrlBasedCorsConfigurationSource apiCorsConfigurationSource(
                final KhezyCorsProperties properties
        ) {
            final var config = new CorsConfiguration();

            config.setAllowCredentials(properties.isAllowCredentials());
            config.setAllowedHeaders(properties.getAllowedHeaders());
            config.setAllowedOrigins(properties.getAllowedOrigins());
            config.setAllowedMethods(properties.getAllowedMethods());
            config.setExposedHeaders(properties.getExposedHeaders());
            config.setAllowPrivateNetwork(properties.getAllowPrivateNetwork());
            config.setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
            config.setMaxAge(properties.getMaxAge());

            final var source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration(properties.getPathPattern(), config);
            return source;
        }
    }
}
