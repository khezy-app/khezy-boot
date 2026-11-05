package io.github.khezyapp.a2a.store.jpa.task;

import io.github.khezyapp.a2a.core.server.TaskScopeLocator;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Database-backed {@link TaskScopeLocator}.
 *
 * <p>Reads the denormalized {@code a2a_tasks.agent_scope} column through a single indexed
 * scalar query instead of loading and deserializing the task payload.</p>
 */
public class JpaTaskScopeLocator implements TaskScopeLocator {

    private final A2ATaskRepository repository;

    /**
     * Creates the locator.
     *
     * @param repository the task repository
     */
    public JpaTaskScopeLocator(final A2ATaskRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public @Nullable String findScope(final String taskId) {
        if (Objects.isNull(taskId) || taskId.isBlank()) {
            return null;
        }
        return repository.findAgentScopeByTaskId(taskId)
                .filter(scope -> !scope.isBlank())
                .orElse(null);
    }
}
