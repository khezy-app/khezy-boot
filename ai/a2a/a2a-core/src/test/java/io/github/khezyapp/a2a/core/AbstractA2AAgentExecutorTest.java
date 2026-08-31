package io.github.khezyapp.a2a.core;

import io.github.khezyapp.a2a.core.executor.AbstractA2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import java.util.ArrayList;
import java.util.List;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractA2AAgentExecutorTest {

    private static final class RecordingSink implements AgentEventSink {

        private final List<EventKind> completedEvents = new ArrayList<>();
        private final List<A2AError> failedErrors = new ArrayList<>();
        private final List<String> statusChanges = new ArrayList<>();
        private final List<Part> chunks = new ArrayList<>();
        private final List<Artifact> artifacts = new ArrayList<>();

        @Override
        public void statusChanged(final TaskState state, final String message) {
            statusChanges.add(message);
        }

        @Override
        public void chunk(final Part delta) {
            chunks.add(delta);
        }

        @Override
        public void artifactAdded(final Artifact artifact) {
            artifacts.add(artifact);
        }

        @Override
        public void completed(final EventKind result) {
            completedEvents.add(result);
        }

        @Override
        public void failed(final A2AError error) {
            failedErrors.add(error);
        }
    }

    private static final class SuccessfulExecutor extends AbstractA2AAgentExecutor {

        @Override
        public EventKind execute(final AgentExecutionContext context) {
            return null;
        }
    }

    private static final class FailingExecutor extends AbstractA2AAgentExecutor {

        @Override
        public EventKind execute(final AgentExecutionContext context) {
            throw new A2AError(Integer.valueOf(-32000), "execution failed", java.util.Map.of());
        }
    }

    @Test
    void streamingFallbackShouldEmitExactlyOneCompletedEventAndNoFailed() {
        final RecordingSink sink = new RecordingSink();

        new SuccessfulExecutor().stream(null, sink);

        assertEquals(1, sink.completedEvents.size());
        assertTrue(sink.failedErrors.isEmpty());
    }

    @Test
    void streamingFallbackShouldEmitFailedWhenExecuteThrowsA2AError() {
        final RecordingSink sink = new RecordingSink();

        new FailingExecutor().stream(null, sink);

        assertEquals(1, sink.failedErrors.size());
        assertTrue(sink.completedEvents.isEmpty());
    }

    @Test
    void cancelShouldDefaultToTaskNotCancelable() {
        final var executor = new SuccessfulExecutor();

        final var e = assertThrows(
            io.github.khezyapp.a2a.core.error.TaskNotCancelableException.class,
            () -> executor.cancel("task-x"));

        assertEquals("task-x", e.getTaskId());
    }
}
