package io.github.khezyapp.a2a.core.executor;

import io.github.khezyapp.a2a.core.error.TaskNotCancelableException;
import org.a2aproject.sdk.spec.EventKind;

/**
 * Blocking agent execution contract (analogous to {@code ChatModel#call}).
 * Framework-neutral: implementations may be backed by ChatClient, a plain service, anything.
 */
public interface A2AAgentExecutor {

    /** Handle a message send synchronously and return the final event/result. */
    EventKind execute(AgentExecutionContext context);

    /**
     * Cooperatively cancel a task. Default: unsupported — the caller must handle
     * {@link TaskNotCancelableException}.
     */
    default void cancel(final String taskId) {
        throw new TaskNotCancelableException(taskId);
    }
}
