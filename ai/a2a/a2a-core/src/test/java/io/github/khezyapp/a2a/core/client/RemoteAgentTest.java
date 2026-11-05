package io.github.khezyapp.a2a.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.junit.jupiter.api.Test;

class RemoteAgentTest {

    @Test
    void shouldExposeNameAndSkills() {
        final var agent = ClientFixtures.agent();

        assertEquals("echo-agent", agent.name());
        assertEquals(1, agent.skills().size());
        assertEquals("echo", agent.skills().get(0).id());
        assertEquals("echo-agent", agent.skills().get(0).agentName());
        assertEquals("http://localhost:8080", agent.skills().get(0).agentUrl());
        assertThrows(UnsupportedOperationException.class, () -> agent.skills().clear());
    }

    @Test
    void selectedInterfaceShouldPreferTheCardTransport() {
        final var card = AgentCard.builder()
                .name("multi")
                .description("multi transport")
                .version("1.0")
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(AgentSkill.builder().id("echo").name("Echo")
                        .description("echoes").tags(List.of("text")).build()))
                .supportedInterfaces(List.of(
                        new AgentInterface("REST", "http://rest", "tenant-rest", "1.0"),
                        new AgentInterface("JSONRPC", "http://jsonrpc", "tenant-rpc", "1.0")))
                .preferredTransport("JSONRPC")
                .build();
        final var agent = new RemoteAgent("http://jsonrpc", card, ClientFixtures.agent().client());

        assertEquals("JSONRPC", agent.selectedInterface().orElseThrow().protocolBinding());
        assertEquals("tenant-rpc", agent.tenant().orElseThrow());
    }

    @Test
    void selectedInterfaceShouldFallBackToTheFirstInterface() {
        final var card = AgentCard.builder()
                .name("single")
                .description("single transport")
                .version("1.0")
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(AgentSkill.builder().id("echo").name("Echo")
                        .description("echoes").tags(List.of("text")).build()))
                .supportedInterfaces(List.of(new AgentInterface("REST", "http://rest", null, "1.0")))
                .preferredTransport("JSONRPC")
                .build();
        final var agent = new RemoteAgent("http://rest", card, ClientFixtures.agent().client());

        assertEquals("REST", agent.selectedInterface().orElseThrow().protocolBinding());
        assertFalse(agent.tenant().isPresent());
    }

    @Test
    void shouldRejectNullComponents() {
        final var agent = ClientFixtures.agent();

        assertThrows(NullPointerException.class, () -> new RemoteAgent(null, agent.card(), agent.client()));
        assertThrows(NullPointerException.class, () -> new RemoteAgent(agent.baseUrl(), null, agent.client()));
        assertThrows(NullPointerException.class, () -> new RemoteAgent(agent.baseUrl(), agent.card(), null));
    }

    @Test
    void taskRefShouldReportMissingIds() {
        final var empty = TaskRef.empty();

        assertFalse(empty.hasContext());
        assertFalse(empty.hasTask());
        assertTrue(new TaskRef("ctx", "task").hasContext());
        assertTrue(new TaskRef("ctx", "task").hasTask());
        assertFalse(new TaskRef(" ", " ").hasContext());
        assertFalse(new TaskRef(" ", " ").hasTask());
    }
}
