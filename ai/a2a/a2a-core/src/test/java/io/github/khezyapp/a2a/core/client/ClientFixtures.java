package io.github.khezyapp.a2a.core.client;

import static org.mockito.Mockito.mock;

import java.util.List;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Shared SDK fixtures for client contract tests.
 */
public final class ClientFixtures {

    private ClientFixtures() {
    }

    public static RemoteAgent agent() {
        return agent("echo-agent", "http://localhost:8080", "tenant-1");
    }

    public static RemoteAgent agent(final String name, final String url, final String tenant) {
        final var skill = AgentSkill.builder()
                .id("echo")
                .name("Echo")
                .description("echoes text")
                .tags(List.of("text"))
                .examples(List.of("hello"))
                .inputModes(List.of("text"))
                .outputModes(List.of("text"))
                .build();
        final var card = AgentCard.builder()
                .name(name)
                .description("test agent")
                .version("1.0")
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(skill))
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", url, tenant, "1.0")))
                .preferredTransport("JSONRPC")
                .build();
        return new RemoteAgent(url, card, mock(Client.class));
    }

    public static Task task(final TaskState state, final String contextId, final String taskId) {
        return Task.builder()
                .id(taskId)
                .contextId(contextId)
                .status(new TaskStatus(state, message("status text"), null))
                .build();
    }

    public static Message message(final String text) {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(text)))
                .build();
    }
}
