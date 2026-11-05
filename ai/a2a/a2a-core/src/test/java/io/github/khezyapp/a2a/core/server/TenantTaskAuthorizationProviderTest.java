package io.github.khezyapp.a2a.core.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.junit.jupiter.api.Test;

class TenantTaskAuthorizationProviderTest {

    @Test
    void shouldAllowSameScope() throws Exception {
        final var provider = providerForTaskScope("scope-a");
        final var context = contextWithScope("scope-a");

        assertTrue(provider.checkRead(context, "task-1", TaskOperation.GET_TASK));
        assertTrue(provider.checkWrite(context, "task-1", TaskOperation.CANCEL_TASK));
    }

    @Test
    void shouldDenyDifferentOrBlankScope() throws Exception {
        final var provider = providerForTaskScope("scope-a");

        assertFalse(provider.checkRead(contextWithScope("scope-b"), "task-1", TaskOperation.GET_TASK));
        assertFalse(provider.checkRead(contextWithScope("  "), "task-1", TaskOperation.GET_TASK));
        assertFalse(provider.checkRead(contextWithScope(null), "task-1", TaskOperation.GET_TASK));
        assertFalse(provider.checkRead(contextWithoutScope(), "task-1", TaskOperation.GET_TASK));
    }

    @Test
    void shouldDenyWhenTaskScopeIsUnknown() throws Exception {
        final var provider = providerForTaskScope(null);

        assertFalse(provider.checkRead(contextWithScope("scope-a"), "task-1", TaskOperation.GET_TASK));
        assertFalse(provider.checkWrite(contextWithScope("scope-a"), "task-1", TaskOperation.CANCEL_TASK));
    }

    @Test
    void shouldAllowListTasks() throws Exception {
        final var provider = providerForTaskScope("scope-a");

        assertTrue(provider.checkRead(contextWithScope("scope-b"), "", TaskOperation.LIST_TASKS));
        assertTrue(provider.checkRead(contextWithoutScope(), "", TaskOperation.LIST_TASKS));
    }

    @Test
    void shouldAllowCreateOnlyWithContext() throws Exception {
        final var provider = providerForTaskScope("scope-a");

        assertTrue(provider.checkCreate(contextWithScope("scope-a"), TaskOperation.MESSAGE_SEND));
        assertFalse(provider.checkCreate(null, TaskOperation.MESSAGE_SEND));
    }

    @Test
    void shouldTreatEveryTaskAsRecorded() throws Exception {
        final var provider = providerForTaskScope("scope-a");

        assertTrue(provider.isTaskRecorded("task-1"));
        assertDoesNotThrow(() -> provider.recordOwnership(
                contextWithScope("scope-a"), "task-1", TaskOperation.MESSAGE_SEND));
    }

    @Test
    void shouldDenyWhenScopeValueIsNotAString() throws Exception {
        final var provider = providerForTaskScope("scope-a");
        final var context = new ServerCallContext(
                UnauthenticatedUser.INSTANCE,
                Map.<String, Object>of(A2AContextKeys.AGENT_SCOPE, 42),
                Set.of());

        assertFalse(provider.checkRead(context, "task-1", TaskOperation.GET_TASK));
    }

    private static TenantTaskAuthorizationProvider providerForTaskScope(final String scope) {
        final var locator = mock(TaskScopeLocator.class);
        when(locator.findScope("task-1")).thenReturn(scope);
        return new TenantTaskAuthorizationProvider(locator);
    }

    private static ServerCallContext contextWithScope(final String scope) {
        return new ServerCallContext(
                UnauthenticatedUser.INSTANCE,
                scope == null ? Map.of() : Map.of(A2AContextKeys.AGENT_SCOPE, scope),
                Set.of());
    }

    private static ServerCallContext contextWithoutScope() {
        return new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());
    }
}
