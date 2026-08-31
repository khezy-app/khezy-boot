package io.github.khezyapp.a2a.core.error;

/** Thrown when the task store fails to persist or load a task. */
public class TaskStoreException extends A2ACoreException {

    public TaskStoreException(final String message,
                              final Throwable cause) {
        super(message, cause);
    }
}
