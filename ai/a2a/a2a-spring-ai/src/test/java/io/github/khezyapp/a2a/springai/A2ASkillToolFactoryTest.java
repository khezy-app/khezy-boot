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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2ASkillToolFactoryTest {

    private RemoteAgentRegistry registry;
    private A2AConnector connector;
    private A2ASkillToolFactory factory;

    @BeforeEach
    void setUp() {
        registry = mock(RemoteAgentRegistry.class);
        connector = mock(A2AConnector.class);
        factory = new A2ASkillToolFactory(registry, connector, new A2AResultFormatter());
    }

    @Test
    @DisplayName("One tool is built per discovered skill")
    void shouldBuildOneToolPerSkill() {
        final var add = TestAgents.skill("math", "add", "Add", "Adds numbers");
        final var multiply = TestAgents.skill("math", "multiply", "Multiply", "Multiplies numbers");
        when(registry.skills()).thenReturn(List.of(add, multiply));

        final var tools = factory.toolCallbacks();

        assertEquals(2, tools.size());
        assertEquals("math__add", tools.get(0).getToolDefinition().name());
        assertEquals("math__multiply", tools.get(1).getToolDefinition().name());
    }

    @Test
    @DisplayName("Tool names are sanitized and unique on collision")
    void shouldBuildUniqueNames() {
        final var first = TestAgents.skill("math agent", "add/one", "Add", "Adds numbers");
        final var duplicate = TestAgents.skill("math agent", "add/one", "Add again", "Adds more");
        when(registry.skills()).thenReturn(List.of(first, duplicate));

        final var tools = factory.toolCallbacks();

        assertEquals("math_agent__add_one", tools.get(0).getToolDefinition().name());
        assertEquals("math_agent__add_one_2", tools.get(1).getToolDefinition().name());
    }

    @Test
    @DisplayName("Tool names stay within 64 characters")
    void shouldCapToolNameLength() {
        final var skill = TestAgents.skill("a".repeat(80), "b".repeat(80), "Long", "Long names");
        when(registry.skills()).thenReturn(List.of(skill));

        assertEquals(64, factory.toolCallbacks().get(0).getToolDefinition().name().length());
    }

    @Test
    @DisplayName("Description carries the agent, skill, tags and examples")
    void shouldDescribeSkill() {
        final var skill = TestAgents.skill("math", "add", "Add", "Adds numbers");
        when(registry.skills()).thenReturn(List.of(skill));

        final var description = factory.toolCallbacks().get(0).getToolDefinition().description();

        assertTrue(description.contains("math"));
        assertTrue(description.contains("Add"));
        assertTrue(description.contains("Adds numbers"));
        assertTrue(description.contains("Tags: math"));
        assertTrue(description.contains("Examples: 2 + 2"));
        assertTrue(description.contains("restate the new information"));
    }

    @Test
    @DisplayName("The input schema accepts a required task string")
    void shouldDeclareInputSchema() {
        final var skill = TestAgents.skill("math", "add", "Add", "Adds numbers");
        when(registry.skills()).thenReturn(List.of(skill));

        final var schema = factory.toolCallbacks().get(0).getToolDefinition().inputSchema();

        assertTrue(schema.contains("\"task\""));
        assertTrue(schema.contains("\"required\": [\"task\"]"));
        assertTrue(schema.contains("\"additionalProperties\": false"));
    }

    @Test
    @DisplayName("The registry-backed tool list is cached")
    void shouldCacheToolCallbacks() {
        when(registry.skills()).thenReturn(List.of(TestAgents.skill("math", "add", "Add", "Adds")));

        assertSame(factory.toolCallbacks(), factory.toolCallbacks());
    }

    @Test
    @DisplayName("An explicit skill list is never cached")
    void shouldNotCacheExplicitSkills() {
        final var skills = List.of(TestAgents.skill("math", "add", "Add", "Adds"));

        assertNotSame(factory.toolCallbacks(skills), factory.toolCallbacks(skills));
    }

    @Test
    @DisplayName("Invocation delegates to the connector and formats the outcome")
    void shouldInvokeConnector() {
        final var skill = TestAgents.skill("math", "add", "Add", "Adds numbers");
        final var agent = TestAgents.agent("math", "http://localhost:8080/a2a", skill);
        final var outcome = new SendOutcome(agent, skill, TaskState.TASK_STATE_COMPLETED,
                "ctx-1", "task-1", "4", List.of(), null);
        when(registry.skills()).thenReturn(List.of(skill));
        when(registry.find("math")).thenReturn(Optional.of(agent));
        when(connector.invoke(eq(agent), anyList(), eq("add"), eq("conv-1"))).thenReturn(outcome);

        final var result = factory.toolCallbacks().get(0)
                .call("{\"task\":\"2 + 2\"}", context("conv-1"));

        assertTrue(result.contains("\"agent\":\"math\""));
        assertTrue(result.contains("\"message\":\"4\""));
        verify(connector).invoke(eq(agent), anyList(), eq("add"), eq("conv-1"));
    }

    @Test
    @DisplayName("An unknown agent yields a structured error")
    void shouldReportUnknownAgent() {
        final var skill = TestAgents.skill("math", "add", "Add", "Adds numbers");
        final var other = TestAgents.agent("weather", "http://localhost:8080/weather");
        when(registry.skills()).thenReturn(List.of(skill));
        when(registry.find("math")).thenReturn(Optional.empty());
        when(registry.agents()).thenReturn(List.of(other));

        final var result = factory.toolCallbacks().get(0).call("{\"task\":\"2 + 2\"}", context("conv-1"));

        assertTrue(result.contains("Unknown agent 'math'"));
        assertTrue(result.contains("weather"));
    }

    @Test
    @DisplayName("A missing conversation id yields a structured error")
    void shouldRequireConversationId() {
        final var skill = TestAgents.skill("math", "add", "Add", "Adds numbers");
        when(registry.skills()).thenReturn(List.of(skill));
        when(registry.find("math")).thenReturn(Optional.of(TestAgents.agent("math", "http://localhost/a2a")));

        final var result = factory.toolCallbacks().get(0).call("{\"task\":\"2 + 2\"}", new ToolContext(Map.of()));

        assertTrue(result.contains(A2AToolContextKeys.CONVERSATION_ID));
        assertFalse(result.contains("\"agent\""));
    }

    private static ToolContext context(final String conversationId) {
        return new ToolContext(Map.of(A2AToolContextKeys.CONVERSATION_ID, conversationId));
    }
}
