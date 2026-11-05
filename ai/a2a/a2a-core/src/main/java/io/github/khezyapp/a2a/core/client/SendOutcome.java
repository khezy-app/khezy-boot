package io.github.khezyapp.a2a.core.client;

import java.util.List;
import java.util.Objects;

import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;

/**
 * Normalized result of one client send.
 *
 * <p>Either a task or a standalone message comes back from the SDK. Both are reduced to
 * the same shape so hosts and tools do not branch on SDK event types. Transport errors
 * are carried in {@link #error()} instead of a KHEZY exception hierarchy; the SDK
 * exception types remain available through lower-level APIs such as
 * {@link A2AClientOperations}.</p>
 *
 * @param agent the agent that handled the request
 * @param skill the skill that was requested, or {@code null}
 * @param state the task state, or {@code null} for a direct message response or an error
 * @param contextId the A2A context id, or {@code null}
 * @param taskId the A2A task id, or {@code null}
 * @param message the agent message text, or {@code null}
 * @param artifacts the agent artifacts
 * @param error the normalized error text, or {@code null} on success
 */
public record SendOutcome(RemoteAgent agent,
                          RemoteSkill skill,
                          TaskState state,
                          String contextId,
                          String taskId,
                          String message,
                          List<RemoteArtifact> artifacts,
                          String error) {

    /**
     * Creates an outcome with a defensive artifact copy.
     */
    public SendOutcome {
        Objects.requireNonNull(agent, "agent must not be null");
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    /**
     * Creates an outcome from a completed SDK task.
     *
     * @param agent the agent that handled the request
     * @param skill the requested skill, or {@code null}
     * @param task the SDK task
     * @return the normalized outcome
     */
    public static SendOutcome ofTask(final RemoteAgent agent,
                                     final RemoteSkill skill,
                                     final Task task) {
        Objects.requireNonNull(task, "task must not be null");
        final var status = task.status();
        final var state = Objects.isNull(status) ? null : status.state();
        final var statusMessage = Objects.isNull(status) ? null : status.message();
        final var text = Objects.isNull(status) ? "" : RemotePartExtractor.text(statusMessage.parts());
        return new SendOutcome(
                agent,
                skill,
                state,
                task.contextId(),
                task.id(),
                text,
                RemotePartExtractor.artifacts(task.artifacts()),
                null);
    }

    /**
     * Creates an outcome from a direct SDK message response.
     *
     * @param agent the agent that handled the request
     * @param skill the requested skill, or {@code null}
     * @param message the SDK message
     * @return the normalized outcome
     */
    public static SendOutcome ofMessage(final RemoteAgent agent,
                                        final RemoteSkill skill,
                                        final Message message) {
        Objects.requireNonNull(message, "message must not be null");
        return new SendOutcome(
                agent,
                skill,
                null,
                message.contextId(),
                message.taskId(),
                RemotePartExtractor.text(message.parts()),
                List.of(),
                null);
    }

    /**
     * Creates an error outcome.
     *
     * @param agent the agent that handled the request
     * @param skill the requested skill, or {@code null}
     * @param error the error text
     * @return the normalized outcome
     */
    public static SendOutcome error(final RemoteAgent agent,
                                    final RemoteSkill skill,
                                    final String error) {
        Objects.requireNonNull(error, "error must not be null");
        return new SendOutcome(agent, skill, null, null, null, null, List.of(), error);
    }

    /**
     * Returns whether this outcome carries an error.
     *
     * @return {@code true} when the error text is non-blank
     */
    public boolean isError() {
        return Objects.nonNull(error) && !error.isBlank();
    }

    /**
     * Returns whether the outcome is terminal.
     *
     * <p>A direct message response (no task state) is terminal: the turn ends without a
     * task to resume.</p>
     *
     * @return {@code true} when there is no error and the task is final or absent
     */
    public boolean isFinal() {
        return !isError() && (Objects.isNull(state) || state.isFinal());
    }

    /**
     * Returns whether the task is waiting on the caller.
     *
     * @return {@code true} for input-required or auth-required tasks
     */
    public boolean isInterrupted() {
        return !isError() && Objects.nonNull(state) && state.isInterrupted();
    }

    /**
     * Returns whether the task is still running.
     *
     * @return {@code true} for a non-final, non-interrupted task
     */
    public boolean isPending() {
        return !isError() && Objects.nonNull(state) && !state.isFinal() && !state.isInterrupted();
    }

    /**
     * Returns the A2A identifiers carried by this outcome.
     *
     * @return the task reference
     */
    public TaskRef taskRef() {
        return new TaskRef(contextId, taskId);
    }
}
