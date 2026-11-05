package io.github.khezyapp.a2a.starter.server;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2AMultitenancyTest {

    private final AgentExecutor defaultExecutor = (context, emitter) -> {
    };

    private final AgentExecutor tenantExecutor = (context, emitter) -> {
    };

    @Test
    @DisplayName("Executor router selects tenant executors and falls back to the default")
    void shouldRouteExecutors() {
        final var router = new MapAgentExecutorRouter(defaultExecutor, Map.of("acme", tenantExecutor));

        assertThat(router.resolve("acme")).isSameAs(tenantExecutor);
        assertThat(router.resolve("unknown")).isSameAs(defaultExecutor);
        assertThat(router.resolve(null)).isSameAs(defaultExecutor);
        assertThat(router.resolve(" ")).isSameAs(defaultExecutor);
    }

    @Test
    @DisplayName("Card router serves tenant cards and rejects unknown tenants")
    void shouldRouteCards() {
        final var defaultCard = card("default-agent");
        final var tenantCard = card("tenant-agent");
        final var extendedCard = card("extended-agent");
        final var router = new MapAgentCardRouter(defaultCard, Map.of("acme", tenantCard),
                Map.of("acme", extendedCard));

        assertThat(router.resolvePublicCard(null)).isSameAs(defaultCard);
        assertThat(router.resolvePublicCard("acme")).isSameAs(tenantCard);
        assertThat(router.resolvePublicCard("unknown")).isNull();
        assertThat(router.resolveExtendedCard("acme")).isSameAs(extendedCard);
        assertThat(router.resolveExtendedCard("unknown")).isNull();
    }

    private static AgentCard card(final String name) {
        return AgentCard.builder()
                .name(name)
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
