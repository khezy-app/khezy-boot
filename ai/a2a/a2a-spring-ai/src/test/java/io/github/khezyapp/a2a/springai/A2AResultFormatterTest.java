package io.github.khezyapp.a2a.springai;

import java.util.List;

import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.khezyapp.a2a.core.client.RemoteArtifact;
import io.github.khezyapp.a2a.core.client.SendOutcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2AResultFormatterTest {

    private final A2AResultFormatter formatter = new A2AResultFormatter(200);

    @Test
    @DisplayName("Formats the outcome fields")
    void shouldFormatFields() {
        final var json = formatter.format(outcome("4", List.of()));

        assertTrue(json.contains("\"agent\":\"math\""));
        assertTrue(json.contains("\"skill\":\"add\""));
        assertTrue(json.contains("\"state\":\"TASK_STATE_COMPLETED\""));
        assertTrue(json.contains("\"message\":\"4\""));
        assertFalse(json.contains("\"error\""));
    }

    @Test
    @DisplayName("A2A ids never reach the model")
    void shouldOmitIds() {
        final var json = formatter.format(outcome("4", List.of()));

        assertFalse(json.contains("ctx-1"));
        assertFalse(json.contains("task-1"));
        assertFalse(json.contains("contextId"));
        assertFalse(json.contains("taskId"));
    }

    @Test
    @DisplayName("Formats artifacts with name, description and text")
    void shouldFormatArtifacts() {
        final var json = formatter.format(outcome("4", List.of(
                new RemoteArtifact("sum.txt", "the sum", "4"),
                new RemoteArtifact("steps.md", null, "2 + 2"))));

        assertTrue(json.contains("\"artifacts\":["));
        assertTrue(json.contains("\"name\":\"sum.txt\""));
        assertTrue(json.contains("\"description\":\"the sum\""));
        assertTrue(json.contains("\"text\":\"2 + 2\""));
        assertTrue(json.endsWith("]}"));
    }

    @Test
    @DisplayName("Escapes quotes, backslashes and control characters")
    void shouldEscapeJson() {
        final var json = formatter.format(outcome("he said \"hi\"\\\nnext\tline", List.of()));

        assertTrue(json.contains("he said \\\"hi\\\"\\\\\\nnext\\tline"));
        assertFalse(json.contains("\"message\":\"he said \"hi\""));
    }

    @Test
    @DisplayName("Truncates long text fields")
    void shouldTruncate() {
        final var json = new A2AResultFormatter(5)
                .format(outcome("abcdefghij", List.of(new RemoteArtifact("a", null, "0123456789"))));

        assertTrue(json.contains("abcde...[truncated 5 chars]"));
        assertTrue(json.contains("01234...[truncated 5 chars]"));
    }

    @Test
    @DisplayName("Non-positive limits fall back to the default")
    void shouldUseDefaultLimit() {
        assertEquals(A2AResultFormatter.DEFAULT_MAX_CHARS, new A2AResultFormatter().maxChars());
        assertEquals(A2AResultFormatter.DEFAULT_MAX_CHARS, new A2AResultFormatter(0).maxChars());
        assertEquals(A2AResultFormatter.DEFAULT_MAX_CHARS, new A2AResultFormatter(-1).maxChars());
    }

    @Test
    @DisplayName("Empty fields are omitted")
    void shouldOmitEmptyFields() {
        final var json = formatter.format(outcome("", List.of()));

        assertFalse(json.contains("\"message\""));
    }

    @Test
    @DisplayName("Error outcomes expose the error text")
    void shouldFormatErrors() {
        final var agent = TestAgents.agent("math", "http://localhost:8080/a2a");
        final var json = formatter.format(SendOutcome.error(agent, null, "connection refused"));

        assertTrue(json.contains("\"agent\":\"math\""));
        assertTrue(json.contains("\"error\":\"connection refused\""));
        assertFalse(json.contains("\"skill\""));
        assertFalse(json.contains("\"state\""));
    }

    private static SendOutcome outcome(final String message, final List<RemoteArtifact> artifacts) {
        final var agent = TestAgents.agent("math", "http://localhost:8080/a2a");
        final var skill = TestAgents.skill("math", "add", "Add", "Adds numbers");
        return new SendOutcome(agent, skill, TaskState.TASK_STATE_COMPLETED,
                "ctx-1", "task-1", message, artifacts, null);
    }
}
