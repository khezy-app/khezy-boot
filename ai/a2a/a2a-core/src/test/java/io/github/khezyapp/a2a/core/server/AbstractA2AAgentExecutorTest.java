package io.github.khezyapp.a2a.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AbstractA2AAgentExecutorTest {

    @Test
    void shouldSeedHistoryAndStartWorkForNewTask() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var message = userMessage();
        when(context.getTask()).thenReturn(null);
        when(context.getMessage()).thenReturn(message);
        when(emitter.taskBuilder()).thenAnswer(invocation -> Task.builder()
                .id("task-1")
                .contextId("ctx-1"));
        final var executor = new RecordingExecutor();

        executor.execute(context, emitter);

        final var captor = ArgumentCaptor.forClass(Task.class);
        verify(emitter).addTask(captor.capture());
        final var seeded = captor.getValue();
        assertEquals("task-1", seeded.id());
        assertEquals("ctx-1", seeded.contextId());
        assertEquals(TaskState.TASK_STATE_SUBMITTED, seeded.status().state());
        assertEquals(List.of(message), seeded.history());
        verify(emitter).startWork();
        assertNotNull(executor.received);
    }

    @Test
    void shouldNotReseedExistingTask() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var existing = Task.builder()
                .id("task-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .build();
        when(context.getTask()).thenReturn(existing);
        final var executor = new RecordingExecutor();

        executor.execute(context, emitter);

        verify(emitter, never()).addTask(any());
        verify(emitter).startWork();
        assertNotNull(executor.received);
        assertEquals(existing, executor.received.currentTask().orElseThrow());
    }

    @Test
    void defaultCancelShouldCancelTask() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var executor = new RecordingExecutor();

        executor.cancel(context, emitter);

        verify(emitter).cancel();
        assertNull(executor.cancelRequest);
    }

    @Test
    void onCancelHookShouldReceiveRequest() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var executor = new RecordingCancelExecutor();

        executor.cancel(context, emitter);

        assertNotNull(executor.cancelRequest);
        verify(emitter).cancel();
    }

    private static Message userMessage() {
        return Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId("message-1")
                .contextId("ctx-1")
                .taskId("task-1")
                .parts(List.of(new TextPart("hello")))
                .build();
    }

    private static final class RecordingExecutor extends AbstractA2AAgentExecutor {

        private A2ARequest received;
        private A2ARequest cancelRequest;

        @Override
        protected void onMessage(final A2ARequest request) {
            this.received = request;
        }
    }

    private static final class RecordingCancelExecutor extends AbstractA2AAgentExecutor {

        private A2ARequest cancelRequest;

        @Override
        protected void onMessage(final A2ARequest request) {
        }

        @Override
        protected void onCancel(final A2ARequest request) {
            this.cancelRequest = request;
            super.onCancel(request);
        }
    }
}
