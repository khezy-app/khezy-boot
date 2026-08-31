package io.github.khezyapp.a2a.core.executor;

/**
 * Streaming agent execution contract (analogous to {@code StreamingChatModel#stream}).
 * Emits incremental events (status changes, partial chunks, artifacts) onto the sink.
 */
public interface A2AStreamingAgentExecutor {

    void stream(AgentExecutionContext context, AgentEventSink sink);
}
