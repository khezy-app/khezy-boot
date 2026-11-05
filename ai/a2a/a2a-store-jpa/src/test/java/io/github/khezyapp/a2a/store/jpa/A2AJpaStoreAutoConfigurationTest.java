package io.github.khezyapp.a2a.store.jpa;

import io.github.khezyapp.a2a.store.jpa.push.SpringA2APushNotificationConfigStore;
import io.github.khezyapp.a2a.store.jpa.conversation.SpringA2AConversationStore;
import io.github.khezyapp.a2a.store.jpa.task.JpaTaskScopeLocator;
import io.github.khezyapp.a2a.store.jpa.task.SpringA2ATaskStore;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the auto-configuration contract without booting a context: registration,
 * ordering, fail-closed encryption requirement and conditional beans.
 */
class A2AJpaStoreAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void isAnAutoConfigurationAfterJpa() {
        final var annotation = A2AJpaStoreAutoConfiguration.class.getAnnotation(AutoConfiguration.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.after())
                .contains(HibernateJpaAutoConfiguration.class,
                        DataJpaRepositoriesAutoConfiguration.class);
        assertThat(annotation.afterName())
                .contains("io.github.khezyapp.crypto.spring.CryptoAutoConfiguration");
    }

    @Test
    void requiresJpaAndAStringEncryptor() {
        final var onClass = A2AJpaStoreAutoConfiguration.class
                .getAnnotation(ConditionalOnClass.class);
        assertThat(onClass.value()).contains(EntityManager.class, JpaRepository.class);

        final var onBean = A2AJpaStoreAutoConfiguration.class
                .getAnnotation(ConditionalOnBean.class);
        assertThat(onBean).isNotNull();
        assertThat(onBean.value()).contains(StringEncryptor.class);
    }

    @Test
    void storeBeansAreConditionalOnMissingBean() {
        final var storeMethods = Arrays.stream(A2AJpaStoreAutoConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getReturnType().equals(SpringA2ATaskStore.class)
                        || method.getReturnType().equals(JpaTaskScopeLocator.class)
                        || method.getReturnType().equals(SpringA2APushNotificationConfigStore.class)
                        || method.getReturnType().equals(SpringA2AConversationStore.class))
                .toList();

        assertThat(storeMethods).hasSize(4);
        assertThat(storeMethods).allSatisfy(method ->
                assertThat(method.getAnnotation(ConditionalOnMissingBean.class)).isNotNull());
    }

    @Test
    void isRegisteredInAutoConfigurationImports() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(IMPORTS_RESOURCE)) {
            assertThat(stream).isNotNull();
            final var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains(A2AJpaStoreAutoConfiguration.class.getName());
        }
    }

    @Test
    void propertiesDefaultToJpaWithSdkLimits() {
        final var properties = new A2AStoreProperties();
        assertThat(properties.getType()).isEqualTo(A2AStoreProperties.StoreType.JPA);
        assertThat(properties.getTask().getGracePeriod()).hasSeconds(15);
        assertThat(properties.getPush().getMaxPerTask()).isEqualTo(100);
        assertThat(properties.getConversation().getTtl()).hasHours(1);
    }
}
