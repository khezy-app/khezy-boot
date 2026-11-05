package io.github.khezyapp.a2a.sampleserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MathAgentExecutorTest {

    private final MathAgentExecutor executor = new MathAgentExecutor();

    @Test
    @SuppressWarnings("unchecked")
    void shouldComputeAdditionAndEmitArtifact() {
        final var emitter = mockEmitter();

        executor.execute(context(null, "2 + 3"), emitter);

        final var artifact = ArgumentCaptor.forClass(List.class);
        verify(emitter).addArtifact(artifact.capture(), isNull(), any(), any());
        assertEquals("5", textOf((Part<?>) artifact.getValue().get(0)));
        assertEquals("5", completionText(emitter));
    }

    @Test
    void shouldSupportAllOperators() {
        assertEquals("10", resultFor("12 - 2"));
        assertEquals("42", resultFor("7 * 6"));
        assertEquals("4", resultFor("16 / 4"));
    }

    @Test
    void shouldAskForMissingOperand() {
        final var emitter = mockEmitter();

        executor.execute(context(null, "2 +"), emitter);

        final var question = ArgumentCaptor.forClass(Message.class);
        verify(emitter).requiresInput(question.capture());
        assertTrue(textOf(question.getValue().parts().get(0)).contains("missing operand"));
        verify(emitter, never()).complete(any(Message.class));
    }

    @Test
    void shouldResumeFromTaskHistory() {
        final var emitter = mockEmitter();
        final var task = Task.builder()
                .id("task-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED))
                .history(List.of(userMessage("2 +")))
                .build();

        executor.execute(context(task, "3"), emitter);

        assertEquals("5", completionText(emitter));
    }

    @Test
    void shouldNotRepeatCurrentMessageAlreadyInHistory() {
        final var emitter = mockEmitter();
        final var incoming = userMessage("3");
        final var task = Task.builder()
                .id("task-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED))
                .history(List.of(userMessage("2 +"), incoming))
                .build();

        executor.execute(context(task, incoming), emitter);

        assertEquals("5", completionText(emitter));
    }

    @Test
    void shouldFailMalformedExpressions() {
        assertEquals("Operand is not an integer: two", failureFor("two + three"));
        assertEquals("Unsupported operator: ^", failureFor("1 ^ 2"));
        assertEquals("Division by zero", failureFor("5 / 0"));
        assertEquals("Expected '<number> <op> <number>' but got: 1 + 2 + 3", failureFor("1 + 2 + 3"));
    }

    @Test
    void shouldAskForInputWhenMessageHasNoText() {
        final var emitter = mockEmitter();

        executor.execute(context(null, ""), emitter);

        final var question = ArgumentCaptor.forClass(Message.class);
        verify(emitter).requiresInput(question.capture());
        assertTrue(textOf(question.getValue().parts().get(0)).contains("What should I calculate?"));
    }

    private String resultFor(final String expression) {
        final var emitter = mockEmitter();
        executor.execute(context(null, expression), emitter);
        return completionText(emitter);
    }

    private String failureFor(final String expression) {
        final var emitter = mockEmitter();
        executor.execute(context(null, expression), emitter);
        final var failure = ArgumentCaptor.forClass(Message.class);
        verify(emitter).fail(failure.capture());
        return textOf(failure.getValue().parts().get(0));
    }

    private static RequestContext context(final Task task, final String text) {
        return context(task, userMessage(text));
    }

    private static RequestContext context(final Task task, final Message message) {
        final var context = mock(RequestContext.class);
        when(context.getTask()).thenReturn(task);
        when(context.getMessage()).thenReturn(message);
        return context;
    }

    private static AgentEmitter mockEmitter() {
        final var emitter = mock(AgentEmitter.class);
        when(emitter.taskBuilder()).thenAnswer(invocation -> Task.builder()
                .id("task-1")
                .contextId("ctx-1"));
        when(emitter.messageBuilder()).thenAnswer(invocation -> Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("reply-1")
                .taskId("task-1")
                .contextId("ctx-1"));
        return emitter;
    }

    private static String completionText(final AgentEmitter emitter) {
        final var completion = ArgumentCaptor.forClass(Message.class);
        verify(emitter).complete(completion.capture());
        return textOf(completion.getValue().parts().get(0));
    }

    private static Message userMessage(final String text) {
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(text)))
                .taskId("task-1")
                .contextId("ctx-1")
                .build();
    }

    private static String textOf(final Part<?> part) {
        return ((TextPart) part).text();
    }
}
