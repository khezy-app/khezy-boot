package io.github.khezyapp.api.audit.config;

import io.github.khezyapp.api.audit.ObjectDiffUtils;
import io.github.khezyapp.api.audit.api.SensitiveMasker;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;
import io.github.khezyapp.api.audit.javers.CompositeChangeMapper;
import io.github.khezyapp.api.audit.javers.DefaultValueResolver;
import io.github.khezyapp.api.audit.javers.JaversClassRegistry;
import io.github.khezyapp.api.audit.javers.NoopJaversRepository;
import io.github.khezyapp.api.audit.javers.api.ChangeMapperRegistry;
import io.github.khezyapp.api.audit.javers.api.ValueResolver;
import io.github.khezyapp.api.audit.masker.SensitiveMaskerBuilder;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.domain.EntityScanner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for initializing {@link ObjectDiffUtils} and its dependencies.
 * <p>
 * This configuration ensures that the audit engine is available for manual object
 * comparison regardless of whether the application is using JPA/Hibernate or
 * simple POJOs. It handles the conditional creation of Javers, Change Mappers,
 * and Sensitive Maskers.
 * </p>
 */
@Configuration
public class ObjectDiffConfiguration {

    /**
     * Configures {@link ObjectDiffUtils} for environments where JPA Entities are not present.
     * <p>
     * This bean provides a default Javers instance optimized for plain Java objects,
     * acting as a fallback when the {@code jakarta.persistence.Entity} class is missing
     * from the classpath.
     * </p>
     *
     * @return a non-JPA specialized {@link ObjectDiffUtils}
     */
    @Bean
    @ConditionalOnMissingClass(value = "jakarta.persistence.Entity")
    @ConditionalOnMissingBean
    public ObjectDiffUtils objectDiffUtilsWithDefaultJavers(
            final ObjectProvider<Javers> javersObjectProvider,
            final ObjectProvider<ChangeMapperRegistry> changeMapperRegistryObjectProvider,
            final ObjectProvider<ValueResolver> valueResolverObjectProvider,
            final ObjectProvider<SensitiveMaskerStrategy> maskerStrategyObjectProvider,
            final ObjectProvider<SensitiveMasker> sensitiveMaskerObjectProvider
    ) {
        final var javers = javersObjectProvider.getIfAvailable(() -> JaversBuilder.javers()
                .registerJaversRepository(new NoopJaversRepository())
                .build());
        final var changeMapperRegistry = getChangeMapperRegistry(
                changeMapperRegistryObjectProvider,
                valueResolverObjectProvider,
                maskerStrategyObjectProvider,
                sensitiveMaskerObjectProvider
        );
        return new ObjectDiffUtils(javers, changeMapperRegistry);
    }

    /**
     * Configures {@link ObjectDiffUtils} with full JPA support.
     * <p>
     * This bean initializes a Javers instance that automatically scans the application
     * context for classes annotated with {@code @Entity}. It applies specialized
     * mapping rules via {@link JaversClassRegistry} to handle database relationships
     * and identity fields correctly.
     * </p>
     *
     * @return a JPA-aware {@link ObjectDiffUtils}
     */
    @Bean
    @ConditionalOnClass(name = "jakarta.persistence.Entity")
    @ConditionalOnMissingBean
    public ObjectDiffUtils objectDiffUtils(
            final ObjectProvider<Javers> javersObjectProvider,
            final ObjectProvider<ChangeMapperRegistry> changeMapperRegistryObjectProvider,
            final ObjectProvider<ValueResolver> valueResolverObjectProvider,
            final ObjectProvider<SensitiveMaskerStrategy> maskerStrategyObjectProvider,
            final ObjectProvider<SensitiveMasker> sensitiveMaskerObjectProvider,
            final ObjectProvider<ApplicationContext> contextObjectProvider
    ) {
        final var javers = javersObjectProvider.getIfAvailable(() -> {
            final var builder = JaversBuilder.javers()
                    .registerJaversRepository(new NoopJaversRepository());

            try {
                final var scanner = new EntityScanner(contextObjectProvider.getObject());
                final var entityClasses = scanner.scan(jakarta.persistence.Entity.class);

                for (final var entityClass : entityClasses) {
                    JaversClassRegistry.register(builder, entityClass);
                }
            } catch (final ClassNotFoundException e) {
                throw new RuntimeException("Failed to scan for entities", e);
            }

            return builder.build();
        });
        final var changeMapperRegistry = getChangeMapperRegistry(
                changeMapperRegistryObjectProvider,
                valueResolverObjectProvider,
                maskerStrategyObjectProvider,
                sensitiveMaskerObjectProvider
        );
        return new ObjectDiffUtils(javers, changeMapperRegistry);
    }

    /**
     * Internal helper to resolve or construct the {@link ChangeMapperRegistry} hierarchy.
     * <p>
     * It ensures that the registry is backed by a functional {@link ValueResolver} and
     * {@link SensitiveMasker}, collecting any available {@link SensitiveMaskerStrategy}
     * beans from the context if a masker is not already provided.
     * </p>
     */
    private static ChangeMapperRegistry getChangeMapperRegistry(
            final ObjectProvider<ChangeMapperRegistry> changeMapperRegistryObjectProvider,
            final ObjectProvider<ValueResolver> valueResolverObjectProvider,
            final ObjectProvider<SensitiveMaskerStrategy> maskerStrategyObjectProvider,
            final ObjectProvider<SensitiveMasker> sensitiveMaskerObjectProvider
    ) {
        final var sensitiveMasker = sensitiveMaskerObjectProvider.getIfAvailable(() ->
                SensitiveMaskerBuilder.builder()
                        .registerStrategy(maskerStrategyObjectProvider.stream().toArray(SensitiveMaskerStrategy[]::new))
                        .build()
        );
        final var valueResolver = valueResolverObjectProvider.getIfAvailable(() ->
                new DefaultValueResolver(sensitiveMasker));
        return changeMapperRegistryObjectProvider.getIfAvailable(() ->
                new CompositeChangeMapper(valueResolver));
    }
}
