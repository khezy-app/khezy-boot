package io.github.khezyapp.a2a.starter.server;

import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.events.TaskStreamLifecycleHook;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.khezyapp.a2a.core.server.A2AStoreProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a consumer-provided {@link QueueManager} replaces the in-memory default
 * and is the instance the SDK request handler receives.
 *
 * <p>The replicated queue manager extension point is a plain bean override: no broker
 * dependency and no Spring-specific replication strategy ship with this starter. The
 * consumer is responsible for giving the replacement the same {@code TaskStateProvider}
 * used by the task store; {@link A2AStoreProvider} exposes it.</p>
 */
class A2AQueueManagerOverrideTest {

    private static final AtomicReference<QueueManager> CUSTOM_QUEUE_MANAGER = new AtomicReference<>();

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(A2AServerAutoConfiguration.class))
            .withPropertyValues(
                    "io.github.khezyapp.a2a.server.agent-card.name=queue-test",
                    "io.github.khezyapp.a2a.server.agent-card.url=http://localhost:8080/a2a",
                    "io.github.khezyapp.a2a.server.authorization.required=false")
            .withUserConfiguration(QueueOverrideConfiguration.class);

    @Test
    void consumerQueueManagerWinsAndIsInjectedIntoTheHandler() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(QueueManager.class);
            assertThat(context.getBean(QueueManager.class)).isSameAs(CUSTOM_QUEUE_MANAGER.get());
            assertThat(context).hasSingleBean(DefaultRequestHandler.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class QueueOverrideConfiguration {

        @Bean
        AgentExecutor queueTestExecutor() {
            return new NoopAgentExecutor();
        }

        @Bean
        QueueManager consumerQueueManager(final A2AStoreProvider stores, final MainEventBus eventBus,
                                          final TaskStreamLifecycleHook lifecycleHook) {
            final QueueManager queueManager = new InMemoryQueueManager(stores.taskStateProvider(),
                    eventBus, lifecycleHook);
            CUSTOM_QUEUE_MANAGER.set(queueManager);
            return queueManager;
        }
    }

    static final class NoopAgentExecutor implements AgentExecutor {

        @Override
        public void execute(final RequestContext context, final AgentEmitter emitter) throws A2AError {
            emitter.complete();
        }
    }
}
