package io.github.khezyapp.a2a.starter;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.executor.A2AAgentExecutor;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import io.github.khezyapp.a2a.core.store.A2AEventQueue;
import io.github.khezyapp.a2a.core.store.A2ATaskStore;
import io.github.khezyapp.a2a.core.store.PushNotificationSender;
import io.github.khezyapp.a2a.coredef.dispatch.DefaultA2ARequestDispatcher;
import io.github.khezyapp.a2a.coredef.store.InMemoryEventQueue;
import io.github.khezyapp.a2a.coredef.store.InMemoryTaskStore;
import io.github.khezyapp.a2a.coredef.store.NoopPushNotificationSender;
import io.github.khezyapp.a2a.starter.security.SecurityCallerIdentityResolver;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.function.Supplier;

/**
 * Wires the A2A ports as overridable Spring beans: in-memory defaults for storage and
 * dispatch, a properties-driven {@link AgentCard} supplier, and a Spring Security based
 * caller identity resolver. Every port is {@code @ConditionalOnMissingBean} — provide
 * your own bean to override any of them. The {@link A2AAgentExecutor} is intentionally
 * NOT defined here; it is the application's responsibility.
 */
@AutoConfiguration
@EnableConfigurationProperties(A2AProperties.class)
@ConditionalOnClass({A2AAgentExecutor.class, A2ATaskStore.class})
public class A2AAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(A2ATaskStore.class)
    A2ATaskStore taskStore() {
        return new InMemoryTaskStore();
    }

    @Bean
    @ConditionalOnMissingBean(A2AEventQueue.class)
    A2AEventQueue eventQueue() {
        return new InMemoryEventQueue();
    }

    @Bean
    @ConditionalOnMissingBean(PushNotificationSender.class)
    PushNotificationSender pushNotificationSender() {
        return new NoopPushNotificationSender();
    }

    @Bean
    @ConditionalOnMissingBean(name = "agentCardSupplier")
    Supplier<AgentCard> agentCardSupplier(final A2AProperties properties) {
        final var capabilities = AgentCapabilities.builder()
                .streaming(properties.isStreamingEnabled())
                .build();
        final var url = properties.getAgentCardUrl();
        final var interfaces = url.isBlank()
                ? List.<AgentInterface>of()
                : List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), url));
        final var card = AgentCard.builder()
                .name(properties.getAgentCardName())
                .description(properties.getAgentCardDescription())
                .url(url)
                .version(properties.getAgentCardVersion())
                .capabilities(capabilities)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(interfaces)
                .build();
        return () -> card;
    }

    @Bean
    @ConditionalOnMissingBean(CallerIdentityResolver.class)
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    CallerIdentityResolver callerIdentityResolver() {
        return new SecurityCallerIdentityResolver();
    }

    @Bean
    @ConditionalOnMissingBean(A2ARequestDispatcher.class)
    A2ARequestDispatcher a2aRequestDispatcher(final A2AAgentExecutor executor,
                                              final A2ATaskStore taskStore,
                                              final A2AEventQueue eventQueue,
                                              final PushNotificationSender sender,
                                              final Supplier<AgentCard> cardSupplier) {
        return new DefaultA2ARequestDispatcher(executor, taskStore, eventQueue, sender, cardSupplier);
    }
}
