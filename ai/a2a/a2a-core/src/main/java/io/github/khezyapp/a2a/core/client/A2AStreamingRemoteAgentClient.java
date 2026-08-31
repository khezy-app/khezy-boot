package io.github.khezyapp.a2a.core.client;

import java.util.function.Consumer;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;

/** Streaming variant of the remote client port. */
public interface A2AStreamingRemoteAgentClient {

    void streamMessage(Message message, Consumer<EventKind> listener);

    /**
     * Same as {@link #streamMessage(Message, Consumer)} but attaches per-call metadata
     * (e.g., an Authorization header) to the outgoing request. The default ignores the
     * context so existing implementations stay valid; implementations that understand
     * call context should override.
     */
    default void streamMessage(final Message message, final Consumer<EventKind> listener, final CallContext context) {
        streamMessage(message, listener);
    }
}
