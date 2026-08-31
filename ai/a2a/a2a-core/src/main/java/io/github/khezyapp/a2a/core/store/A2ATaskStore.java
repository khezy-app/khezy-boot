package io.github.khezyapp.a2a.core.store;

import java.util.Optional;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;

/** Task persistence port. Backends: in-memory (A2A-03), later Redis/JDBC. */
public interface A2ATaskStore {

    void save(Task task);

    Optional<Task> find(String taskId);

    void updateState(String taskId, TaskState state);
}
