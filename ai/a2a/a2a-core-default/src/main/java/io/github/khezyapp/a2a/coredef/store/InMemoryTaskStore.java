package io.github.khezyapp.a2a.coredef.store;

import io.github.khezyapp.a2a.core.error.TaskStoreException;
import io.github.khezyapp.a2a.core.store.A2ATaskStore;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskState;

/**
 * Simple in-memory {@link A2ATaskStore} backed by a {@link ConcurrentHashMap}.
 * Suitable for single-instance applications and tests only — tasks are lost on restart.
 */
public final class InMemoryTaskStore implements A2ATaskStore {

    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

    @Override
    public void save(final Task task) {
        Objects.requireNonNull(task, "task");
        tasks.put(task.id(), task);
    }

    @Override
    public Optional<Task> find(final String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public void updateState(final String taskId,
                            final TaskState state) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(state, "state");
        final var existing = tasks.get(taskId);
        if (Objects.isNull(existing)) {
            throw new TaskStoreException("Task not found in store: " + taskId, null);
        }
        tasks.put(taskId, Task.builder(existing).status(new TaskStatus(state)).build());
    }
}
