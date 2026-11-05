package io.github.khezyapp.a2a.springai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import io.github.khezyapp.a2a.core.client.A2AConnector;
import io.github.khezyapp.a2a.core.client.SendOutcome;
import io.github.khezyapp.a2a.coredef.client.RemoteAgentRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2AAgentToolsTest {

    private RemoteAgentRegistry registry;
    private A2AConnector connector;
    private A2AAgentTools tools;

    @BeforeEach
    void setUp() {
        registry = mock(RemoteAgentRegistry.class);
        connector = mock(A2AConnector.class);
        tools = new A2AAgentTools(registry, connector, new A2AResultFormatter());
    }

    @Test
    @DisplayName("sendToAgent delegates without a skill and formats the outcome")
    void shouldSendToAgent() {
        final var skill = TestAgents.skill("math", "add", "Add", "Adds numbers");
        final var agent = TestAgents.agent("math", "http://localhost:8080/a2a", skill);
        final var outcome = new SendOutcome(agent, null, TaskState.TASK_STATE_COMPLETED,
                "ctx-1", "task-1", "4", List.of(), null);
        when(registry.find("math")).thenReturn(Optional.of(agent));
        when(connector.invoke(eq(agent), anyList(), isNull(), eq("conv-1"))).thenReturn(outcome);

        final var result = tools.sendToAgent("math", "2 + 2", context("conv-1"));

        assertTrue(result.contains("\"agent\":\"math\""));
        assertTrue(result.contains("\"message\":\"4\""));
        verify(connector).invoke(eq(agent), anyList(), isNull(), eq("conv-1"));
    }

    @Test
    @DisplayName("sendToAgent reports an unknown agent")
    void shouldReportUnknownAgent() {
        final var agent = TestAgents.agent("weather", "http://localhost:8080/weather");
        when(registry.find("math")).thenReturn(Optional.empty());
        when(registry.agents()).thenReturn(List.of(agent));

        final var result = tools.sendToAgent("math", "2 + 2", context("conv-1"));

        assertTrue(result.contains("Unknown agent 'math'"));
        assertTrue(result.contains("weather"));
    }

    @Test
    @DisplayName("sendToAgent requires the conversation id")
    void shouldRequireConversationId() {
        when(registry.find("math")).thenReturn(Optional.of(TestAgents.agent("math", "http://localhost/a2a")));

        final var result = tools.sendToAgent("math", "2 + 2", new ToolContext(Map.of()));

        assertTrue(result.contains(A2AToolContextKeys.CONVERSATION_ID));
    }

    @Test
    @DisplayName("listRemoteAgents describes names, URLs and skills")
    void shouldListRemoteAgents() {
        final var agent = TestAgents.agent("math", "http://localhost:8080/a2a",
                TestAgents.skill("math", "add", "Add", "Adds numbers"));
        when(registry.agents()).thenReturn(List.of(agent));

        final var result = tools.listRemoteAgents();

        assertTrue(result.contains("math"));
        assertTrue(result.contains("http://localhost:8080/a2a"));
        assertTrue(result.contains("math agent"));
        assertTrue(result.contains("add - Add"));
    }

    @Test
    @DisplayName("listRemoteAgents handles an empty registry")
    void shouldHandleEmptyRegistry() {
        when(registry.agents()).thenReturn(List.of());

        assertEquals("No remote agents are configured.", tools.listRemoteAgents());
    }

    private static ToolContext context(final String conversationId) {
        return new ToolContext(Map.of(A2AToolContextKeys.CONVERSATION_ID, conversationId));
    }
}
