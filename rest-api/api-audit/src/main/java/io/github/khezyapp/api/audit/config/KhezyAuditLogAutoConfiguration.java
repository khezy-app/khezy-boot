package io.github.khezyapp.api.audit.config;

import io.github.khezyapp.api.audit.AuditExpressionEvaluator;
import io.github.khezyapp.api.audit.aop.AuditLogAttributeSource;
import io.github.khezyapp.api.audit.aop.AuditLogMethodInterceptor;
import io.github.khezyapp.api.audit.aop.AuditLogPointcut;
import io.github.khezyapp.api.audit.api.AuditLogService;
import io.github.khezyapp.api.audit.api.SensitiveMasker;
import io.github.khezyapp.api.audit.extractor.AbstractBodyExtractor;
import io.github.khezyapp.api.audit.extractor.CompositeBodyExtractor;
import io.github.khezyapp.api.audit.extractor.CompositeBodyExtractorBuilder;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;

/**
 * Main auto-configuration class for the Khezy Audit library's AOP-based request auditing.
 * <p>
 * This configuration sets up the infrastructure required to intercept methods annotated
 * with {@code @AuditLog}. It defines the Pointcut, Advisor, and Interceptor necessary
 * to capture request metadata, evaluate SpEL expressions, and extract request bodies
 * before delegating to the {@link AuditLogService}.
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "khezy.audit", name = "enabled-audit-request", havingValue = "true")
public class KhezyAuditLogAutoConfiguration {

    /**
     * Defines the source for audit metadata, responsible for parsing the {@code @AuditLog}
     * annotation on methods and classes.
     *
     * @return the attribute source for the audit log
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static AuditLogAttributeSource auditLogAttributeSource() {
        return new AuditLogAttributeSource();
    }

    /**
     * Configures the AOP Advisor that joins the {@link AuditLogPointcut} with the
     * {@link AuditLogMethodInterceptor}.
     * <p>
     * The advisor is set to {@link Ordered#LOWEST_PRECEDENCE} to ensure that auditing
     * happens as the outermost layer, capturing the results of other aspects (like transactions).
     * </p>
     *
     * @param interceptor the method interceptor (lazily injected to prevent circular dependencies)
     * @return the configured advisor
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static Advisor auditLogAdvisor(@Lazy final AuditLogMethodInterceptor interceptor) {
        final var advisor = new DefaultPointcutAdvisor(new AuditLogPointcut(), interceptor);
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
        return advisor;
    }

    /**
     * Creates the primary {@link AuditLogMethodInterceptor} that manages the auditing lifecycle.
     * <p>
     * This interceptor coordinates the extraction of request bodies, evaluation of
     * resource IDs via SpEL, and the final dispatch of log records.
     * </p>
     *
     * @param attributeSource the source for audit annotation metadata
     * @param auditExpressionEvaluator the evaluator for dynamic SpEL expressions
     * @param compositeBodyExtractor the strategy-based request body extractor
     * @param auditLogService the destination service for audit records
     * @return the audit log method interceptor
     */
    @Bean
    public AuditLogMethodInterceptor auditLogMethodInterceptor(
            final AuditLogAttributeSource attributeSource,
            final AuditExpressionEvaluator auditExpressionEvaluator,
            final CompositeBodyExtractor compositeBodyExtractor,
            final AuditLogService auditLogService) {
        return new AuditLogMethodInterceptor(
                attributeSource,
                auditExpressionEvaluator,
                compositeBodyExtractor,
                auditLogService
        );
    }

    /**
     * Provides a default {@link AuditExpressionEvaluator} if none is defined.
     *
     * @return a standard SpEL expression evaluator
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditExpressionEvaluator auditExpressionEvaluator() {
        return new AuditExpressionEvaluator();
    }

    /**
     * Configures the {@link CompositeBodyExtractor} responsible for parsing HTTP request bodies.
     * <p>
     * This bean integrates the {@link SensitiveMasker} and any custom {@link AbstractBodyExtractor}
     * beans found in the context.
     * </p>
     *
     * @param provider an object provider for custom body extractors
     * @param sensitiveMasker the masker to be used during extraction
     * @return a configured {@link CompositeBodyExtractor}
     */
    @Bean
    @ConditionalOnMissingBean
    public CompositeBodyExtractor compositeBodyExtractor(
            final ObjectProvider<AbstractBodyExtractor> provider,
            final SensitiveMasker sensitiveMasker) {
        final var builder = CompositeBodyExtractorBuilder.builder()
                .sensitiveMasker(sensitiveMasker);
        provider.forEach(builder::registerExtractor);
        return builder.build();
    }
}
