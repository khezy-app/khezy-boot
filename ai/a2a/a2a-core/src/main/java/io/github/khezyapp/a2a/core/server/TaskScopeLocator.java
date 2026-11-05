package io.github.khezyapp.a2a.core.server;

import java.util.Objects;

import org.a2aproject.sdk.server.tasks.TaskStore;
import org.jspecify.annotations.Nullable;

/**
 * Locates the application-owned scope recorded for a task.
 *
 * <p>Implementations backed by a database should use a denormalized scope column; the
 * default {@link #fromTaskStore(TaskStore)} reads {@link A2AContextKeys#AGENT_SCOPE}
 * from task metadata.</p>
 */
@FunctionalInterface
public interface TaskScopeLocator {

    /**
     * Finds the scope recorded for a task.
     *
     * @param taskId the task identifier
     * @return the scope, or {@code null} when unknown
     */
    @Nullable String findScope(String taskId);

    /**
     * Creates a locator that knows no scopes.
     *
     * @return the empty locator
     */
    static TaskScopeLocator none() {
        return taskId -> null;
    }

    /**
     * Creates a locator backed by task metadata.
     *
     * @param store the task store
     * @return the metadata-backed locator
     */
    static TaskScopeLocator fromTaskStore(final TaskStore store) {
        Objects.requireNonNull(store, "store must not be null");
        return taskId -> {
            final var task = store.get(taskId);
            if (task == null || task.metadata() == null) {
                return null;
            }
            final var value = task.metadata().get(A2AContextKeys.AGENT_SCOPE);
            if (value instanceof String scope && !scope.isBlank()) {
                return scope;
            }
            return null;
        };
    }
}
