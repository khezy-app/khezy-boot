package io.github.khezyapp.a2a.coredef.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khezyapp.a2a.core.dispatch.CallContext;
import io.github.khezyapp.a2a.core.executor.AbstractA2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import io.github.khezyapp.a2a.core.executor.A2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import io.github.khezyapp.a2a.coredef.store.InMemoryEventQueue;
import io.github.khezyapp.a2a.coredef.store.InMemoryTaskStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.spec.*;
import org.junit.jupiter.api.Test;

class DefaultA2ARequestDispatcherStreamingTest {

    private final InMemoryTaskStore taskStore = new InMemoryTaskStore();

    @Test
    void blockingOnlyExecutorShouldFallBackToSingleCompletedEvent() {
        final var sink = new RecordingSink();
        final var dispatcher = dispatcher(new BlockingExecutor());

        dispatcher.onMessageStream(params("task-s", "ctx"), sink, callContext());

        assertEquals(1, sink.completed.size());
        assertTrue(sink.chunks.isEmpty());
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStore.find("task-s").orElseThrow().status().state());
    }

    @Test
    void nativeStreamingExecutorShouldEmitChunkAndCompleted() {
        final var sink = new RecordingSink();
        final var dispatcher = dispatcher(new NativeStreamingExecutor());

        dispatcher.onMessageStream(params("task-n", "ctx"), sink, callContext());

        assertEquals(List.of("chunk", "completed"), sink.calls);
        assertEquals(1, sink.completed.size());
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStore.find("task-n").orElseThrow().status().state());
    }

    private DefaultA2ARequestDispatcher dispatcher(final A2AAgentExecutor executor) {
        return new DefaultA2ARequestDispatcher(
                executor,
                taskStore,
                new InMemoryEventQueue(),
                task -> {
                },
                () -> AgentCard.builder().name("test-agent").description("").version("1.0.0").build());
    }

    private MessageSendParams params(final String taskId,
                                     final String contextId) {
        return new MessageSendParams(
                Message.builder()
                        .role(Message.Role.ROLE_USER)
                        .parts(new TextPart("hi"))
                        .messageId("m-" + taskId)
                        .taskId(taskId)
                        .contextId(contextId)
                        .build(),
                null,
                Map.of());
    }

    private CallContext callContext() {
        return new CallContext() {
            @Override
            public CallerIdentity caller() {
                return CallerIdentity.anonymous();
            }

            @Override
            public Map<String, Object> attributes() {
                return Map.of();
            }
        };
    }

    private static final class BlockingExecutor implements A2AAgentExecutor {
        @Override
        public EventKind execute(final AgentExecutionContext context) {
            return null;
        }
    }

    private static final class NativeStreamingExecutor extends AbstractA2AAgentExecutor {
        @Override
        public EventKind execute(final AgentExecutionContext context) {
            throw new UnsupportedOperationException("blocking path not used");
        }

        @Override
        public void stream(final AgentExecutionContext context, final AgentEventSink sink) {
            sink.chunk(new TextPart("delta"));
            sink.completed(null);
        }
    }

    private static final class RecordingSink implements AgentEventSink {
        final List<String> calls = new ArrayList<>();
        final List<EventKind> completed = new ArrayList<>();
        final List<Part> chunks = new ArrayList<>();

        @Override
        public void statusChanged(final TaskState state, final String message) {
            calls.add("status:" + state);
        }

        @Override
        public void artifactAdded(final Artifact artifact) {
            calls.add("artifact");
        }

        @Override
        public void chunk(final Part delta) {
            calls.add("chunk");
            chunks.add(delta);
        }

        @Override
        public void completed(final EventKind result) {
            calls.add("completed");
            completed.add(result);
        }

        @Override
        public void failed(final A2AError error) {
            calls.add("failed");
        }
    }
}
