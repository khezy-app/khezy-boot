package io.github.khezyapp.a2a.starter.server;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.inject.Instance;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.server.multitenancy.AgentExecutorRouter;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.khezyapp.a2a.core.server.TenantTaskAuthorizationProvider;

import static org.assertj.core.api.Assertions.assertThat;

class SdkA2AConfigInjectorTest {

    @Test
    @DisplayName("Pinned SDK fields exist")
    void shouldFindSdkFields() {
        assertThat(SdkA2AConfigInjector.hasField(DefaultRequestHandler.class,
                SdkA2AConfigInjector.CONFIG_PROVIDER_FIELD)).isTrue();
        assertThat(SdkA2AConfigInjector.hasField(DefaultRequestHandler.class,
                SdkA2AConfigInjector.AUTHORIZATION_PROVIDER_INSTANCE_FIELD)).isTrue();
        assertThat(SdkA2AConfigInjector.hasField(DefaultRequestHandler.class,
                SdkA2AConfigInjector.AGENT_EXECUTOR_ROUTER_INSTANCE_FIELD)).isTrue();
        assertThat(SdkA2AConfigInjector.hasField(JSONRPCHandler.class,
                SdkA2AConfigInjector.AGENT_CARD_ROUTER_FIELD)).isTrue();
    }

    @Test
    @DisplayName("Configuration provider is injected into the request handler")
    void shouldInjectConfigProvider() {
        final var handler = newHandler();
        final var provider = new SpringA2AConfigProvider(new MockEnvironment(), emptyDefaults(), Map.of());

        SdkA2AConfigInjector.setConfigProvider(handler, provider);

        assertThat(ReflectionTestUtils.getField(handler, SdkA2AConfigInjector.CONFIG_PROVIDER_FIELD))
                .isSameAs(provider);
    }

    @Test
    @DisplayName("Authorization provider survives the SDK initialization step")
    void shouldInjectAuthorizationProvider() {
        final var handler = newHandler();
        final TaskAuthorizationProvider provider = new TenantTaskAuthorizationProvider(taskId -> "scope");

        SdkA2AConfigInjector.setAuthorizationProvider(handler, provider);

        assertThat(ReflectionTestUtils.getField(handler, SdkA2AConfigInjector.AUTHORIZATION_PROVIDER_FIELD))
                .isSameAs(provider);
        final Instance<?> instance = (Instance<?>) ReflectionTestUtils.getField(handler,
                SdkA2AConfigInjector.AUTHORIZATION_PROVIDER_INSTANCE_FIELD);
        assertThat(instance).isNotNull();
        assertThat(instance.get()).isSameAs(provider);
    }

    @Test
    @DisplayName("Tenant executor router survives the SDK initialization step")
    void shouldInjectAgentExecutorRouter() {
        final var handler = newHandler();
        final AgentExecutor executor = (context, emitter) -> {
        };
        final AgentExecutorRouter router = new MapAgentExecutorRouter(executor, Map.of());

        SdkA2AConfigInjector.setAgentExecutorRouter(handler, router);

        assertThat(ReflectionTestUtils.getField(handler, SdkA2AConfigInjector.AGENT_EXECUTOR_ROUTER_FIELD))
                .isSameAs(router);
        final Instance<?> instance = (Instance<?>) ReflectionTestUtils.getField(handler,
                SdkA2AConfigInjector.AGENT_EXECUTOR_ROUTER_INSTANCE_FIELD);
        assertThat(instance).isNotNull();
        assertThat(instance.get()).isSameAs(router);
    }

    @Test
    @DisplayName("Agent card router is injected into the JSON-RPC handler")
    void shouldInjectAgentCardRouter() {
        final var handler = new JSONRPCHandler(card(), newHandler(), Runnable::run);
        final AgentCardRouter router = new MapAgentCardRouter(card(), Map.of());

        SdkA2AConfigInjector.setAgentCardRouter(handler, router);

        assertThat(ReflectionTestUtils.getField(handler, SdkA2AConfigInjector.AGENT_CARD_ROUTER_FIELD))
                .isSameAs(router);
    }

    private DefaultRequestHandler newHandler() {
        final var store = new InMemoryTaskStore();
        final var bus = new MainEventBus();
        final var queueManager = new InMemoryQueueManager(store, bus);
        final var processor = Mockito.mock(MainEventBusProcessor.class);
        final AgentExecutor executor = (context, emitter) -> {
        };
        return DefaultRequestHandler.builder()
                .agentExecutor(executor)
                .taskStore(store)
                .queueManager(queueManager)
                .mainEventBusProcessor(processor)
                .executor(Runnable::run)
                .eventConsumerExecutor(Runnable::run)
                .authorizationRequired(false)
                .pushNotificationsEnabled(false)
                .build();
    }

    private static A2AConfigProvider emptyDefaults() {
        return new A2AConfigProvider() {
            @Override
            public String getValue(final String name) {
                throw new IllegalArgumentException("No default for " + name);
            }

            @Override
            public Optional<String> getOptionalValue(final String name) {
                return Optional.empty();
            }
        };
    }

    private static AgentCard card() {
        return AgentCard.builder()
                .name("test-agent")
                .description("test")
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost/a2a")))
                .url("http://localhost/a2a")
                .build();
    }
}
