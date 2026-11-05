package io.github.khezyapp.a2a.store.jpa;

import io.github.khezyapp.a2a.store.jpa.push.A2APushNotificationConfigRepository;
import io.github.khezyapp.a2a.store.jpa.push.SpringA2APushNotificationConfigStore;
import io.github.khezyapp.a2a.store.jpa.conversation.A2AConversationRepository;
import io.github.khezyapp.a2a.store.jpa.conversation.SpringA2AConversationStore;
import io.github.khezyapp.a2a.store.jpa.task.A2ATaskRepository;
import io.github.khezyapp.a2a.store.jpa.task.JpaTaskScopeLocator;
import io.github.khezyapp.a2a.store.jpa.task.SpringA2ATaskStore;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.LinkedHashSet;

/**
 * Auto-configuration for the Spring/JPA A2A stores.
 *
 * <p>Active only when JPA and a {@link StringEncryptor} bean are present. Entity and
 * repository scanning are <strong>additive</strong>: a plain {@code @EntityScan} would
 * replace the application's auto-configuration packages, so the store package is appended
 * through a {@link BeanFactoryPostProcessor} instead.</p>
 *
 * <p>The crypto starter ({@code io.github.khezyapp.crypto.spring.CryptoAutoConfiguration})
 * is ordered before this configuration by name, so its {@code StringEncryptor} bean is
 * visible to {@link ConditionalOnBean} without a compile dependency on the starter.</p>
 */
@AutoConfiguration(
        after = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class},
        afterName = "io.github.khezyapp.crypto.spring.CryptoAutoConfiguration")
@ConditionalOnClass({EntityManager.class, JpaRepository.class, StringEncryptor.class})
@ConditionalOnBean(StringEncryptor.class)
@EnableJpaRepositories(basePackages = A2AJpaStoreAutoConfiguration.STORE_PACKAGE)
@EnableConfigurationProperties(A2AStoreProperties.class)
public class A2AJpaStoreAutoConfiguration {

    /** Package that contains the store entities and repositories. */
    public static final String STORE_PACKAGE = "io.github.khezyapp.a2a.store.jpa";

    /**
     * Appends {@link #STORE_PACKAGE} to the application's entity-scan packages instead of
     * replacing them.
     *
     * @return the additive entity-scan post-processor
     */
    @Bean
    static BeanFactoryPostProcessor a2aStoreEntityScanPackages() {
        return beanFactory -> {
            if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
                return;
            }
            final var packages = new LinkedHashSet<String>();
            if (AutoConfigurationPackages.has(beanFactory)) {
                packages.addAll(AutoConfigurationPackages.get(beanFactory));
            }
            packages.add(STORE_PACKAGE);
            EntityScanPackages.register(registry, packages.toArray(new String[0]));
        };
    }

    /**
     * Registers the JPA task store.
     *
     * @param repository the task repository
     * @param encryptor the crypto library encryptor
     * @param properties the store properties
     * @param events the application event publisher
     * @return the task store
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringA2ATaskStore springA2ATaskStore(final A2ATaskRepository repository,
                                                 final StringEncryptor encryptor,
                                                 final A2AStoreProperties properties,
                                                 final ApplicationEventPublisher events) {
        return new SpringA2ATaskStore(repository, encryptor,
                properties.getTask().getGracePeriod(), events);
    }

    /**
     * Registers the denormalized scope locator.
     *
     * @param repository the task repository
     * @return the scope locator
     */
    @Bean
    @ConditionalOnMissingBean
    public JpaTaskScopeLocator jpaTaskScopeLocator(final A2ATaskRepository repository) {
        return new JpaTaskScopeLocator(repository);
    }

    /**
     * Registers the JPA push-notification config store.
     *
     * @param repository the push-config repository
     * @param encryptor the crypto library encryptor
     * @param properties the store properties
     * @return the push-config store
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringA2APushNotificationConfigStore springA2APushNotificationConfigStore(
            final A2APushNotificationConfigRepository repository,
            final StringEncryptor encryptor,
            final A2AStoreProperties properties) {
        return new SpringA2APushNotificationConfigStore(repository, encryptor,
                properties.getPush().getMaxPerTask());
    }

    /**
     * Registers the JPA conversation store used for multi-turn resumption.
     *
     * @param repository the conversation repository
     * @param properties the store properties
     * @return the conversation store
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringA2AConversationStore springA2AConversationStore(
            final A2AConversationRepository repository,
            final A2AStoreProperties properties) {
        return new SpringA2AConversationStore(repository,
                properties.getConversation().getTtl());
    }
}
