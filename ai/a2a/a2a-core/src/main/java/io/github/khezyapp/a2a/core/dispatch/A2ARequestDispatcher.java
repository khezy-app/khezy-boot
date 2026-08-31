package io.github.khezyapp.a2a.core.dispatch;

import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import java.util.Optional;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;

/**
 * Behavioral re-implementation of the SDK's request handler, owned by us: adds identity
 * and cancellation hooks, and is testable without any transport.
 */
public interface A2ARequestDispatcher {

    EventKind onMessageSend(MessageSendParams params, CallContext ctx);

    void onMessageStream(MessageSendParams params, AgentEventSink sink, CallContext ctx);

    Optional<Task> onGetTask(TaskQueryParams query, CallContext ctx);

    Task onCancelTask(TaskIdParams params, CallContext ctx);

    AgentCard agentCard(CallContext ctx);
}
