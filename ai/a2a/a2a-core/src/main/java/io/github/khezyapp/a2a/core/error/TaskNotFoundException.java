package io.github.khezyapp.a2a.core.error;

/** Thrown when a requested task does not exist (maps to the spec's TaskNotFoundError). */
public class TaskNotFoundException extends A2ACoreException {

    private final String taskId;

    public TaskNotFoundException(final String taskId) {
        super("Task not found: " + taskId);
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }
}
