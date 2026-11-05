package io.github.khezyapp.a2a.core.server;

import java.util.Objects;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.spec.A2AError;

/**
 * Task authorization based on the application-owned agent scope.
 *
 * <p>The scope recorded on a task (see {@link TaskScopeLocator}) must match the
 * {@link A2AContextKeys#AGENT_SCOPE} value in the caller's call-context state. Missing
 * or blank scopes fail closed. The SDK converts denials into {@code TaskNotFoundError},
 * so task existence is not leaked.</p>
 */
public final class TenantTaskAuthorizationProvider implements TaskAuthorizationProvider {

    private final TaskScopeLocator scopeLocator;

    /**
     * Creates the provider.
     *
     * @param scopeLocator the task scope locator
     */
    public TenantTaskAuthorizationProvider(final TaskScopeLocator scopeLocator) {
        this.scopeLocator = Objects.requireNonNull(scopeLocator, "scopeLocator must not be null");
    }

    @Override
    public boolean checkRead(
            final ServerCallContext context,
            final String taskId,
            final TaskOperation operation
    ) throws A2AError {
        if (operation == TaskOperation.LIST_TASKS) {
            return true;
        }
        return isSameScope(context, taskId);
    }

    @Override
    public boolean checkWrite(
            final ServerCallContext context,
            final String taskId,
            final TaskOperation operation
    ) throws A2AError {
        return isSameScope(context, taskId);
    }

    @Override
    public boolean checkCreate(final ServerCallContext context,
                               final TaskOperation operation) throws A2AError {
        return Objects.nonNull(context);
    }

    @Override
    public boolean isTaskRecorded(final String taskId) throws A2AError {
        return true;
    }

    @Override
    public void recordOwnership(
            final ServerCallContext context,
            final String taskId,
            final TaskOperation operation) throws A2AError {
        // Ownership is derived from the persisted task scope, not recorded separately.
    }

    private boolean isSameScope(final ServerCallContext context,
                                final String taskId) {
        if (Objects.isNull(context) || Objects.isNull(context.getState())) {
            return false;
        }
        final var callScope = context.getState().get(A2AContextKeys.AGENT_SCOPE);
        if (!(callScope instanceof String scope) || scope.isBlank()) {
            return false;
        }
        final var taskScope = scopeLocator.findScope(taskId);
        if (Objects.isNull(taskScope) || taskScope.isBlank()) {
            return false;
        }
        return scope.equals(taskScope);
    }
}
