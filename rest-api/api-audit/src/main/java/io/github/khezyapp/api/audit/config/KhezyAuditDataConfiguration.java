package io.github.khezyapp.api.audit.config;

import io.github.khezyapp.api.audit.api.SensitiveMasker;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;
import io.github.khezyapp.api.audit.masker.BeanSensitiveMaskerStrategy;
import io.github.khezyapp.api.audit.masker.SensitiveMaskerBuilder;
import io.github.khezyapp.api.audit.mixin.AuditMetadataJsonMixin;
import io.github.khezyapp.api.audit.mixin.AuditThrowableSerializer;
import io.github.khezyapp.api.audit.model.AuditMetadata;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.util.ProxyUtils;

import java.util.Collections;

/**
 * Configuration class that initializes the core data and auditing infrastructure for the Khezy Audit library.
 * <p>
 * This configuration is conditionally active based on the property {@code khezy.audit.enabled=true}.
 * It sets up the necessary beans for sensitive data masking, request body extraction,
 * object diffing via Javers, and custom JSON serialization.
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@Conditional(OnAuditEnabledCondition.class)
@Slf4j
public class KhezyAuditDataConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BeanSensitiveMaskerStrategy beanSensitiveMaskerStrategy() {
        return new BeanSensitiveMaskerStrategy(Collections.emptySet(), ProxyUtils::getUserClass);
    }

    /**
     * Configures a {@link SensitiveMasker} to handle data sanitization.
     * <p>
     * It automatically collects all available {@link SensitiveMaskerStrategy} beans
     * from the application context to build a comprehensive masking toolkit.
     * </p>
     *
     * @param provider an object provider for all registered masking strategies
     * @return a configured {@link SensitiveMasker} instance
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveMasker sensitiveMasker(final ObjectProvider<SensitiveMaskerStrategy> provider) {
        final var builder = SensitiveMaskerBuilder.builder();
        provider.forEach(builder::registerStrategy);
        return builder.build();
    }

    /**
     * Customizes the Jackson {@link com.fasterxml.jackson.databind.ObjectMapper} to include the audit metadata mixin.
     * <p>
     * This ensures that {@link AuditMetadata} instances are serialized according to
     * the rules defined in {@link AuditMetadataJsonMixin}.
     * </p>
     *
     * @return a customizer for the Jackson builder
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer auditMixinCustomizer() {
        return builder ->
                builder.mixIn(AuditMetadata.class, AuditMetadataJsonMixin.class)
                        .serializerByType(Throwable.class, new AuditThrowableSerializer());
    }
}
