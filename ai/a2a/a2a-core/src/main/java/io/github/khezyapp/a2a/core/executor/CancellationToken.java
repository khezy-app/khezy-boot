package io.github.khezyapp.a2a.core.executor;

import io.github.khezyapp.a2a.core.error.TaskCancelledException;

/** Cooperative cancellation support threaded through execution. */
public interface CancellationToken {

    boolean isCancellationRequested();

    void requestCancellation();

    default void checkCancellation() {
        if (isCancellationRequested()) {
            throw new TaskCancelledException("execution");
        }
    }
}
