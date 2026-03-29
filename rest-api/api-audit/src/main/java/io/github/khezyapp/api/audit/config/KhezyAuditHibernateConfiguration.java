package io.github.khezyapp.api.audit.config;

import io.github.khezyapp.api.audit.api.AuditLogService;
import io.github.khezyapp.api.audit.api.SensitiveMasker;
import io.github.khezyapp.api.audit.interceptor.KhezyAuditLogHibernateInterceptor;
import io.github.khezyapp.api.audit.javers.CompositeChangeMapper;
import io.github.khezyapp.api.audit.javers.DefaultValueResolver;
import io.github.khezyapp.api.audit.javers.JaversClassRegistry;
import io.github.khezyapp.api.audit.javers.NoopJaversRepository;
import io.github.khezyapp.api.audit.javers.api.ValueResolver;
import io.github.khezyapp.api.audit.javers.strategy.ContainerChangeStrategy;
import io.github.khezyapp.api.audit.javers.strategy.MapChangeStrategy;
import io.github.khezyapp.api.audit.javers.strategy.ReferenceChangeStrategy;
import io.github.khezyapp.api.audit.javers.strategy.ValueChangeStrategy;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScanner;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for integrating the audit logging system with Hibernate.
 * <p>
 * This configuration is activated only when Hibernate is present on the classpath
 * and audit logging is explicitly enabled via {@code khezy.audit.enabled=true}.
 * It registers a custom Hibernate Interceptor to the session factory to capture
 * low-level entity lifecycle events.
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.hibernate.Session")
@ConditionalOnProperty(prefix = "khezy.audit", name = "enabled-audit-entity-changes", havingValue = "true")
public class KhezyAuditHibernateConfiguration {

    /**
     * Creates the {@link KhezyAuditLogHibernateInterceptor} bean.
     * <p>
     * This interceptor uses Javers for object diffing and a {@link CompositeChangeMapper}
     * to translate those differences into audit records before sending them to
     * the {@link AuditLogService}.
     * </p>
     *
     * @param javers                the diff engine used to compare entity states
     * @param auditLogService       the service responsible for processing the audit data
     * @param compositeChangeMapper the mapper used to format entity field changes
     * @return a new instance of the Hibernate audit interceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public KhezyAuditLogHibernateInterceptor khezyAuditLogHibernateInterceptor(
            final Javers javers,
            final AuditLogService auditLogService,
            final CompositeChangeMapper compositeChangeMapper
    ) {
        return new KhezyAuditLogHibernateInterceptor(javers, auditLogService, compositeChangeMapper);
    }

    /**
     * Customizes Hibernate properties to register the audit interceptor.
     * <p>
     * This bean hooks into the Spring Boot Hibernate autoconfiguration process
     * by adding the {@code hibernate.session_factory.interceptor} property,
     * ensuring the interceptor is applied to every Hibernate Session created
     * by the SessionFactory.
     * </p>
     *
     * @param interceptor the audit interceptor to be registered
     * @return a customizer that modifies the Hibernate property map
     */
    @Bean
    public HibernatePropertiesCustomizer auditHibernatePropertiesCustomizer(
            final KhezyAuditLogHibernateInterceptor interceptor) {
        return hibernateProperties ->
                hibernateProperties.put("hibernate.session_factory.interceptor", interceptor);
    }

    /**
     * Initializes the {@link Javers} instance used for calculating entity differences.
     * <p>
     * This setup includes:
     * <ol>
     * <li>Configuring a {@link NoopJaversRepository} as the system primarily uses
     * Javers for diff calculation rather than state persistence.</li>
     * <li>Scanning the application context for classes annotated with {@code @Entity}
     * and registering them with Javers to ensure correct relationship and property mapping.</li>
     * </ol>
     * </p>
     *
     * @param context the application context used for entity scanning
     * @return a fully configured Javers instance
     * @throws RuntimeException if entity scanning fails due to missing classes
     */
    @Bean
    @ConditionalOnMissingBean
    public Javers javers(final ApplicationContext context) {
        final var builder = JaversBuilder.javers()
                .registerJaversRepository(new NoopJaversRepository());

        try {
            // 1. Scan the classpath for @Entity classes manually
            final var scanner = new EntityScanner(context);
            final var entityClasses = scanner.scan(jakarta.persistence.Entity.class);

            for (final var entityClass : entityClasses) {
                // 2. Scan for relationship fields
                JaversClassRegistry.register(builder, entityClass);
            }
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException("Failed to scan for entities", e);
        }

        return builder.build();
    }

    /**
     * Provides the default {@link ValueResolver} for resolving entity field values.
     *
     * @param sensitiveMasker the masker used to sanitize values during resolution
     * @return a {@link DefaultValueResolver} instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ValueResolver valueResolver(final SensitiveMasker sensitiveMasker) {
        return new DefaultValueResolver(sensitiveMasker);
    }

    /**
     * Configures the {@link CompositeChangeMapper} with a standard set of mapping strategies.
     * <p>
     * Strategies included:
     * <ul>
     * <li>{@link ValueChangeStrategy}: For simple property changes.</li>
     * <li>{@link MapChangeStrategy}: For changes within Map structures.</li>
     * <li>{@link ContainerChangeStrategy}: For List/Set/Array modifications.</li>
     * <li>{@link ReferenceChangeStrategy}: For changes in entity associations.</li>
     * </ul>
     * </p>
     *
     * @param valueResolver the resolver used to process field values
     * @return a fully registered {@link CompositeChangeMapper}
     */
    @Bean
    @ConditionalOnMissingBean
    public CompositeChangeMapper compositeChangeMapper(final ValueResolver valueResolver) {
        final var composite = new CompositeChangeMapper(valueResolver);
        composite.register(new ValueChangeStrategy());
        composite.register(new MapChangeStrategy());
        composite.register(new ContainerChangeStrategy());
        composite.register(new ReferenceChangeStrategy());
        return composite;
    }
}
