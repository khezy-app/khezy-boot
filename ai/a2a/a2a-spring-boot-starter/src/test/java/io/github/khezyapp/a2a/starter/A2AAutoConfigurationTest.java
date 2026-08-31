package io.github.khezyapp.a2a.starter;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.executor.A2AAgentExecutor;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import io.github.khezyapp.a2a.core.store.A2ATaskStore;
import io.github.khezyapp.a2a.coredef.store.InMemoryEventQueue;
import io.github.khezyapp.a2a.coredef.store.InMemoryTaskStore;
import io.github.khezyapp.a2a.coredef.store.NoopPushNotificationSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class A2AAutoConfigurationTest {

    @Test
    @DisplayName("Class is an auto-configuration")
    void shouldBeAutoConfiguration() {
        assertThat(A2AAutoConfiguration.class.getAnnotation(AutoConfiguration.class)).isNotNull();
    }

    @Test
    @DisplayName("Properties are enabled through @EnableConfigurationProperties")
    void shouldEnableA2AProperties() {
        final var annotation = A2AAutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(A2AProperties.class);
    }

    @Test
    @DisplayName("Configuration is guarded on the core port types")
    void shouldBeGuardedOnCorePorts() {
        final var annotation = A2AAutoConfiguration.class.getAnnotation(ConditionalOnClass.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(A2AAgentExecutor.class, A2ATaskStore.class);
    }

    @Test
    @DisplayName("Every @Bean factory method carries @ConditionalOnMissingBean")
    void everyBeanMethodShouldBeOverridable() {
        final var beanMethods = Arrays.stream(A2AAutoConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Bean.class) != null)
                .toList();
        assertThat(beanMethods).hasSize(6);
        assertThat(beanMethods).allSatisfy(method ->
                assertThat(method.getAnnotation(ConditionalOnMissingBean.class))
                        .as(method.getName())
                        .isNotNull());
    }

    @Test
    @DisplayName("CallerIdentityResolver bean only registers when Spring Security is present")
    void callerIdentityResolverShouldRequireSecurityOnClasspath() {
        final var method = Arrays.stream(A2AAutoConfiguration.class.getDeclaredMethods())
                .filter(candidate -> candidate.getReturnType().equals(CallerIdentityResolver.class))
                .findFirst()
                .orElseThrow();
        final var annotation = method.getAnnotation(ConditionalOnClass.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name())
                .contains("org.springframework.security.core.context.SecurityContextHolder");
    }

    @Test
    @DisplayName("taskStore() default is the in-memory store")
    void taskStoreShouldDefaultToInMemory() {
        assertThat(new A2AAutoConfiguration().taskStore()).isInstanceOf(InMemoryTaskStore.class);
    }

    @Test
    @DisplayName("eventQueue() default is the in-memory queue")
    void eventQueueShouldDefaultToInMemory() {
        assertThat(new A2AAutoConfiguration().eventQueue()).isInstanceOf(InMemoryEventQueue.class);
    }

    @Test
    @DisplayName("pushNotificationSender() default is the noop sender")
    void pushNotificationSenderShouldDefaultToNoop() {
        assertThat(new A2AAutoConfiguration().pushNotificationSender())
                .isInstanceOf(NoopPushNotificationSender.class);
    }

    @Test
    @DisplayName("agentCardSupplier() builds the card from property defaults")
    void agentCardSupplierShouldBuildCardFromDefaults() {
        final var card = new A2AAutoConfiguration().agentCardSupplier(new A2AProperties()).get();
        assertThat(card.name()).isEqualTo("Khezy A2A Agent");
        assertThat(card.description()).isEmpty();
        assertThat(card.url()).isEmpty();
        assertThat(card.version()).isEqualTo("1.0.0");
        assertThat(card.capabilities().streaming()).isTrue();
    }

    @Test
    @DisplayName("agentCardSupplier() reflects configured properties, including streaming off")
    void agentCardSupplierShouldReflectConfiguredProperties() {
        final var properties = new A2AProperties();
        properties.setAgentCardName("Math Agent");
        properties.setAgentCardDescription("arithmetic helper");
        properties.setAgentCardUrl("https://example.com/agent");
        properties.setAgentCardVersion("9.9.9");
        properties.setStreamingEnabled(false);
        final var card = new A2AAutoConfiguration().agentCardSupplier(properties).get();
        assertThat(card.name()).isEqualTo("Math Agent");
        assertThat(card.description()).isEqualTo("arithmetic helper");
        assertThat(card.url()).isEqualTo("https://example.com/agent");
        assertThat(card.version()).isEqualTo("9.9.9");
        assertThat(card.capabilities().streaming()).isFalse();
    }

    @Test
    @DisplayName("a2aRequestDispatcher() default is the core-default dispatcher")
    void a2aRequestDispatcherShouldWireDefaults() {
        final var config = new A2AAutoConfiguration();
        final A2AAgentExecutor executor = context -> null;
        final var dispatcher = config.a2aRequestDispatcher(executor,
                config.taskStore(),
                config.eventQueue(),
                config.pushNotificationSender(),
                config.agentCardSupplier(new A2AProperties()));
        assertThat(dispatcher).isInstanceOf(A2ARequestDispatcher.class);
    }
}
