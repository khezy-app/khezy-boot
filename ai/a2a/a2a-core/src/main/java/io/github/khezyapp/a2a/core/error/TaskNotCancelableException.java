package io.github.khezyapp.a2a.core.error;

/** Thrown when an agent does not support cancelling a task (maps to the spec's TaskNotCancelableError). */
public class TaskNotCancelableException extends A2ACoreException {

    private final String taskId;

    public TaskNotCancelableException(final String taskId) {
        super("Task is not cancelable: " + taskId);
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }
}
