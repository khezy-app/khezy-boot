package io.github.khezyapp.a2a.core.server;

import java.util.List;
import java.util.Objects;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;

/**
 * Convenience base class for SDK agents.
 *
 * <p>The SDK {@link AgentExecutor} remains the only contract: a simple agent may be a
 * lambda {@code (context, emitter) -> ...}. This class only removes the per-turn
 * ceremony of seeding a new task and starting work.</p>
 *
 * <p>Business failures should be reported through {@link A2ARequest#fail(String)} or by
 * throwing {@link A2AError} for protocol-visible errors. Unexpected runtime exceptions
 * are not caught here; the SDK request handler maps them.</p>
 */
public abstract class AbstractA2AAgentExecutor implements AgentExecutor {

    @Override
    public final void execute(final RequestContext context,
                              final AgentEmitter emitter) throws A2AError {
        if (Objects.isNull(context.getTask())) {
            emitter.addTask(emitter.taskBuilder()
                    .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                    .history(Objects.nonNull(context.getMessage()) ? List.of(context.getMessage()) : null)
                    .build());
        }
        emitter.startWork();
        onMessage(new A2ARequest(context, emitter));
    }

    /**
     * Handles one request turn.
     *
     * @param request the request view over the SDK context and emitter
     */
    protected abstract void onMessage(A2ARequest request);

    @Override
    public void cancel(final RequestContext context,
                       final AgentEmitter emitter) throws A2AError {
        onCancel(new A2ARequest(context, emitter));
    }

    /**
     * Handles cancellation. The default cancels the task.
     *
     * @param request the request view over the SDK context and emitter
     */
    protected void onCancel(final A2ARequest request) {
        request.cancel();
    }
}
