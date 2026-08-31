package io.github.khezyapp.a2a.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.khezyapp.a2a.core.executor.AbstractA2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import io.github.khezyapp.a2a.core.executor.CancellationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

class ChatClientExecutorsTest {

    private final ChatClient chatClient = mock(ChatClient.class);

    private static Message incomingMessage(final String text) {
        return Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(text)))
                .taskId("task-1")
                .contextId("ctx-1")
                .build();
    }

    private static ChatClient.ChatClientRequestSpec stubPrompt(final ChatClient client,
                                                               final Flux<String> flux) {
        final var requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class, Mockito.RETURNS_SELF);
        when(client.prompt()).thenReturn(requestSpec);
        final var streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(flux);
        return requestSpec;
    }

    @Test
    void fromBlockingExecuteDelegatesAndReturnsCompletedReply() {
        final var executor = ChatClientExecutors.from(chatClient, (client, ctx) -> "hello reply");
        assertInstanceOf(AbstractA2AAgentExecutor.class, executor);

        final EventKind event = executor.execute(new TestContext(incomingMessage("hi there")));

        final var statusUpdate = assertInstanceOf(TaskStatusUpdateEvent.class, event);
        assertEquals("task-1", statusUpdate.taskId());
        assertEquals("ctx-1", statusUpdate.contextId());
        assertEquals(TaskState.TASK_STATE_COMPLETED, statusUpdate.status().state());
        final var replyPart = assertInstanceOf(TextPart.class, statusUpdate.status().message().parts().get(0));
        assertEquals("hello reply", replyPart.text());
    }

    @Test
    void fromBlockingStreamFallbackEmitsSingleCompleted() {
        final var executor = ChatClientExecutors.from(chatClient, (client, ctx) -> "reply");
        final RecordingSink sink = new RecordingSink();

        executor.stream(new TestContext(incomingMessage("q")), sink);

        assertEquals(1, sink.completed.size());
        assertTrue(sink.failed.isEmpty());
        assertTrue(sink.chunks.isEmpty());
    }

    @Test
    void fromBothStreamDelegatesToStreamingHandler() {
        final var invoked = new boolean[]{false};
        final StreamingChatClientMessageHandler streamingHandler = (client, ctx, sink) -> {
            invoked[0] = true;
            sink.completed(null);
        };
        final var executor = ChatClientExecutors.from(chatClient,
                (client, ctx) -> "ignored", streamingHandler);
        final RecordingSink sink = new RecordingSink();

        executor.stream(new TestContext(incomingMessage("q")), sink);

        assertTrue(invoked[0]);
        assertEquals(1, sink.completed.size());
    }

    @Test
    void textExtractionPassesUserTextThroughChatClient() {
        final var requestSpec = stubPrompt(chatClient, Flux.just("chunk1 ", "chunk2"));
        final var executor = ChatClientExecutors.from(chatClient,
                (client, ctx) -> "x", ChatClientExecutors.reactiveStreamingHandler());
        final RecordingSink sink = new RecordingSink();

        executor.stream(new TestContext(incomingMessage("expected prompt")), sink);

        verify(requestSpec).user("expected prompt");
        assertEquals(2, sink.chunks.size());
        assertEquals(1, sink.completed.size());
        assertEquals(0, sink.failed.size());
        final var finalEvent = assertInstanceOf(TaskStatusUpdateEvent.class, sink.completed.get(0));
        final var replyPart = assertInstanceOf(TextPart.class, finalEvent.status().message().parts().get(0));
        assertEquals("chunk1 chunk2", replyPart.text());
    }

    @Test
    void reactiveHandlerFailsOnMissingTextPart() {
        final var executor = ChatClientExecutors.from(chatClient,
                (client, ctx) -> "x", ChatClientExecutors.reactiveStreamingHandler());
        final RecordingSink sink = new RecordingSink();
        final var incoming = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("   ")))
                .taskId("t")
                .contextId("c")
                .build();

        executor.stream(new TestContext(incoming), sink);

        assertEquals(0, sink.completed.size());
        assertEquals(1, sink.failed.size());
        assertEquals(-32602, sink.failed.get(0).getCode());
    }

    private record TestContext(Message incomingMessage) implements AgentExecutionContext {

        @Override
        public Message incoming() {
            return incomingMessage;
        }

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
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingSink implements AgentEventSink {

        private final List<EventKind> completed = new ArrayList<>();
        private final List<A2AError> failed = new ArrayList<>();
        private final List<Part> chunks = new ArrayList<>();

        @Override
        public void statusChanged(final TaskState state, final String message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void artifactAdded(final Artifact artifact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void chunk(final Part delta) {
            chunks.add(delta);
        }

        @Override
        public void completed(final EventKind result) {
            completed.add(result);
        }

        @Override
        public void failed(final A2AError error) {
            failed.add(error);
        }
    }
}
