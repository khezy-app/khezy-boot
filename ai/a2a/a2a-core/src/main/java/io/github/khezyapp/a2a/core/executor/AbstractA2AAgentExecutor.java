package io.github.khezyapp.a2a.core.executor;

import org.a2aproject.sdk.spec.A2AError;

/**
 * Convenience bridge so blocking-only agents get streaming for free: {@link #stream}
 * runs {@link #execute} and emits its result as a single completed event.
 * Full streaming agents override {@link #stream} directly.
 */
public abstract class AbstractA2AAgentExecutor implements A2AAgentExecutor, A2AStreamingAgentExecutor {

    @Override
    public void stream(final AgentExecutionContext context,
                       final AgentEventSink sink) {
        try {
            sink.completed(execute(context));
        } catch (final A2AError e) {
            sink.failed(e);
        }
    }
}
