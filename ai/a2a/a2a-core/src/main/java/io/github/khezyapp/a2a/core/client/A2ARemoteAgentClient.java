package io.github.khezyapp.a2a.core.client;

import java.net.URI;
import java.time.Duration;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;

/** Client port for calling remote A2A agents. Shared by host agents and subagent bridges. */
public interface A2ARemoteAgentClient extends AutoCloseable {

    AgentCard discover(URI baseUrl);

    EventKind sendMessage(Message message, Duration timeout);

    /**
     * Same as {@link #sendMessage(Message, Duration)} but attaches per-call metadata
     * (e.g., an Authorization header) to the outgoing request. The default ignores the
     * context so existing implementations stay valid; implementations that understand
     * call context should override.
     */
    default EventKind sendMessage(final Message message, final Duration timeout, final CallContext context) {
        return sendMessage(message, timeout);
    }
}
