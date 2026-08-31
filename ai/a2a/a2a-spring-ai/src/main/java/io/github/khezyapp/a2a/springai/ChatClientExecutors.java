package io.github.khezyapp.a2a.springai;

import io.github.khezyapp.a2a.core.executor.AbstractA2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Static factories adapting a Spring AI {@link ChatClient} into A2A agent executors.
 */
public final class ChatClientExecutors {

    private ChatClientExecutors() {
    }

    public static AbstractA2AAgentExecutor from(final ChatClient chatClient,
                                                final ChatClientMessageHandler blocking) {
        Objects.requireNonNull(chatClient, "chatClient");
        Objects.requireNonNull(blocking, "blocking");
        return new BlockingChatClientExecutor(chatClient, blocking);
    }

    public static AbstractA2AAgentExecutor from(final ChatClient chatClient,
                                                final ChatClientMessageHandler blocking,
                                                final StreamingChatClientMessageHandler streaming) {
        Objects.requireNonNull(streaming, "streaming");
        return new StreamingChatClientExecutor(chatClient, blocking, streaming);
    }

    static String extractUserText(final Message incoming) {
        Objects.requireNonNull(incoming, "incoming");
        final var text = incoming.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(part -> ((TextPart) part).text())
                .filter(text1 -> !text1.isBlank())
                .findFirst();
        return text.orElse(null);
    }

    static TaskStatusUpdateEvent replyEvent(final String taskId,
                                            final String contextId,
                                            final String reply) {
        final var replyMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(reply)))
                .taskId(taskId)
                .contextId(contextId)
                .build();
        return new TaskStatusUpdateEvent(
                taskId,
                new TaskStatus(TaskState.TASK_STATE_COMPLETED, replyMessage, OffsetDateTime.now()),
                contextId,
                Map.of());
    }

    private static final class BlockingChatClientExecutor extends AbstractA2AAgentExecutor {

        private final ChatClient chatClient;
        private final ChatClientMessageHandler blocking;

        private BlockingChatClientExecutor(final ChatClient chatClient,
                                           final ChatClientMessageHandler blocking) {
            this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
            this.blocking = Objects.requireNonNull(blocking, "blocking");
        }

        @Override
        public EventKind execute(final AgentExecutionContext context) {
            final var incoming = context.incoming();
            final var userText = extractUserText(incoming);
            if (Objects.isNull(userText)) {
                throw new A2AError(-32602, "Invalid params: no text part", Map.of());
            }
            final var reply = blocking.handle(chatClient, context);
            if (Objects.isNull(reply)) {
                throw new A2AError(-32000, "Agent returned no reply", Map.of());
            }
            return replyEvent(incoming.taskId(), incoming.contextId(), reply);
        }
    }

    private static final class StreamingChatClientExecutor extends AbstractA2AAgentExecutor {

        private final ChatClient chatClient;
        private final ChatClientMessageHandler blocking;
        private final StreamingChatClientMessageHandler streaming;

        private StreamingChatClientExecutor(final ChatClient chatClient,
                                            final ChatClientMessageHandler blocking,
                                            final StreamingChatClientMessageHandler streaming) {
            this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
            this.blocking = Objects.requireNonNull(blocking, "blocking");
            this.streaming = Objects.requireNonNull(streaming, "streaming");
        }

        @Override
        public EventKind execute(final AgentExecutionContext context) {
            return new BlockingChatClientExecutor(chatClient, blocking).execute(context);
        }

        @Override
        public void stream(final AgentExecutionContext context, final AgentEventSink sink) {
            streaming.handle(chatClient, context, sink);
        }
    }

    /**
     * Built-in streaming handler that prompts the {@link ChatClient} reactively: every
     * content chunk is emitted via {@link AgentEventSink#chunk}, completion emits the final
     * completed event, errors emit a failed event.
     */
    public static StreamingChatClientMessageHandler reactiveStreamingHandler() {
        return (chatClient, context, sink) -> {
            final var incoming = context.incoming();
            final var userText = extractUserText(incoming);
            if (Objects.isNull(userText)) {
                sink.failed(new A2AError(-32602, "Invalid params: no text part", Map.of()));
                return;
            }
            try {
                final var collected = new StringBuilder();
                final var contentFlux = chatClient.prompt()
                        .user(userText)
                        .stream()
                        .content();

                contentFlux.subscribe(
                        chunk -> {
                            collected.append(chunk);
                            sink.chunk(new TextPart(chunk));
                        },
                        error -> sink.failed(new A2AError(-32000, errorMessage(error), Map.of())),
                        () -> sink.completed(
                                replyEvent(
                                        incoming.taskId(),
                                        incoming.contextId(),
                                        collected.toString()
                                )
                        )
                );
            } catch (final Exception e) {
                sink.failed(new A2AError(-32000, errorMessage(e), Map.of()));
            }
        };
    }

    private static String errorMessage(final Throwable t) {
        return Objects.nonNull(t.getMessage()) ? t.getMessage() : t.getClass().getSimpleName();
    }
}
