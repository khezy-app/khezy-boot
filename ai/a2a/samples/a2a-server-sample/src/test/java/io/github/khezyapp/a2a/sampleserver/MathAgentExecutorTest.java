package io.github.khezyapp.a2a.sampleserver;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import io.github.khezyapp.a2a.core.executor.CancellationToken;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MathAgentExecutorTest {

    private final MathAgentExecutor executor = new MathAgentExecutor();

    @Test
    @DisplayName("computes 2 + 3 and answers '5' in a completed status update")
    void shouldAdd() {
        final var update = completedUpdateFor("2 + 3");
        assertEquals("5", firstText(update.status().message()));
        assertEquals(update.taskId(), update.status().message().taskId());
        assertEquals(update.contextId(), update.status().message().contextId());
    }

    @Test
    @DisplayName("supports subtraction, multiplication and division")
    void shouldSupportAllOperators() {
        assertEquals("10", firstText(completedUpdateFor("12 - 2").status().message()));
        assertEquals("42", firstText(completedUpdateFor("7 * 6").status().message()));
        assertEquals("4", firstText(completedUpdateFor("16 / 4").status().message()));
    }

    @Test
    @DisplayName("rejects malformed expressions with an invalid-params A2AError")
    void shouldRejectMalformedExpressions() {
        assertInvalidParams(() -> executor.execute(context("2 +")));
        assertInvalidParams(() -> executor.execute(context("two + three")));
        assertInvalidParams(() -> executor.execute(context("1 ^ 2")));
        assertInvalidParams(() -> executor.execute(context("5 / 0")));
    }

    @Test
    @DisplayName("rejects incoming messages without a non-blank text part")
    void shouldRejectMissingTextPart() {
        final var blank = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("")))
                .taskId("task-1")
                .contextId("ctx-1")
                .build();
        assertInvalidParams(() -> executor.execute(new TestContext(blank)));
    }

    @Test
    @DisplayName("generates task/context ids when the wire message omits them")
    void shouldGenerateIdsWhenAbsent() {
        final var anonymous = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("2 + 3")))
                .build();
        final var result = executor.execute(new TestContext(anonymous));
        final var update = assertInstanceOf(TaskStatusUpdateEvent.class, result);
        assertEquals(false, update.taskId().isBlank());
        assertEquals(update.taskId(), update.contextId());
        assertEquals("5", firstText(update.status().message()));
    }

    private TaskStatusUpdateEvent completedUpdateFor(final String expression) {
        final var result = executor.execute(context(expression));
        final var update = assertInstanceOf(TaskStatusUpdateEvent.class, result);
        assertEquals(TaskState.TASK_STATE_COMPLETED, update.status().state());
        return update;
    }

    private static void assertInvalidParams(final Runnable call) {
        final var error = assertThrows(A2AError.class, call::run);
        assertEquals(-32602, error.getCode());
    }

    private static AgentExecutionContext context(final String text) {
        return new TestContext(Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(text)))
                .taskId("task-1")
                .contextId("ctx-1")
                .build());
    }

    private static String firstText(final Message message) {
        return message.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .findFirst()
                .orElseThrow();
    }

    private record TestContext(Message incoming) implements AgentExecutionContext {

        @Override
        public Optional<Task> currentTask() {
            return Optional.empty();
        }

        @Override
        public CallerIdentity caller() {
            return CallerIdentity.anonymous();
        }

        @Override
        public Map<String, Object> attributes() {
            return Map.of();
        }

        @Override
        public CancellationToken cancellationToken() {
            return new SimpleCancellationTokenStub();
        }
    }

    private static final class SimpleCancellationTokenStub implements CancellationToken {

        @Override
        public boolean isCancellationRequested() {
            return false;
        }

        @Override
        public void requestCancellation() {
            throw new UnsupportedOperationException("not cancelled in tests");
        }
    }
}
