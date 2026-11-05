package io.github.khezyapp.a2a.springai;

import java.util.Arrays;
import java.util.List;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;

import io.github.khezyapp.a2a.core.client.RemoteAgent;
import io.github.khezyapp.a2a.core.client.RemoteSkill;

import static org.mockito.Mockito.mock;

/**
 * Builds real card-backed agent/skill values for tests.
 */
final class TestAgents {

    private TestAgents() {
    }

    static RemoteSkill skill(final String agentName, final String id, final String name,
                             final String description) {
        return new RemoteSkill(agentName, "http://localhost:8080/a2a", id, name, description,
                List.of("math"), List.of("2 + 2"), List.of("text"), List.of("text"));
    }

    static RemoteAgent agent(final String name, final String baseUrl, final RemoteSkill... skills) {
        final var cardSkills = Arrays.stream(skills)
                .map(skill -> AgentSkill.builder()
                        .id(skill.id())
                        .name(skill.name())
                        .description(skill.description())
                        .tags(skill.tags())
                        .examples(skill.examples())
                        .build())
                .toList();
        final var card = AgentCard.builder()
                .name(name)
                .description(name + " agent")
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(cardSkills)
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", baseUrl, null, "1.0")))
                .preferredTransport("JSONRPC")
                .build();
        return new RemoteAgent(baseUrl, card, mock(Client.class));
    }
}
