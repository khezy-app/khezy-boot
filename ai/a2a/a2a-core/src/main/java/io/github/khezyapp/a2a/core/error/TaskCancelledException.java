package io.github.khezyapp.a2a.core.error;

/** Thrown when cooperative cancellation has been requested for the current execution. */
public class TaskCancelledException extends A2ACoreException {

    public TaskCancelledException(final String taskId) {
        super("Task cancelled cooperatively: " + taskId);
    }
}
