package io.github.khezyapp.a2a.core.client;

/**
 * Identifiers needed to continue an A2A conversation.
 *
 * <p>Either identifier may be absent. A conversation that has never produced a task has
 * no context; a settled task keeps its context but drops the task id so the next message
 * starts a fresh task in the same context.</p>
 *
 * @param contextId the A2A context id, or {@code null}
 * @param taskId the A2A task id, or {@code null}
 */
public record TaskRef(String contextId, String taskId) {

    /**
     * An empty reference.
     */
    public static final TaskRef EMPTY = new TaskRef(null, null);

    /**
     * Returns an empty reference.
     *
     * @return the empty reference
     */
    public static TaskRef empty() {
        return EMPTY;
    }

    /**
     * Returns whether a task id is present.
     *
     * @return {@code true} when the task id is non-blank
     */
    public boolean hasTask() {
        return taskId != null && !taskId.isBlank();
    }

    /**
     * Returns whether a context id is present.
     *
     * @return {@code true} when the context id is non-blank
     */
    public boolean hasContext() {
        return contextId != null && !contextId.isBlank();
    }
}
