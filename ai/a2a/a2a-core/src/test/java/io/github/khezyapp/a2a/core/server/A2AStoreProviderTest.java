package io.github.khezyapp.a2a.core.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.junit.jupiter.api.Test;

class A2AStoreProviderTest {

    @Test
    void inMemoryShouldShareTaskStoreWithStateProvider() {
        final var provider = A2AStoreProvider.inMemory();

        assertTrue(provider.taskStore() instanceof InMemoryTaskStore);
        assertSame(provider.taskStore(), provider.taskStateProvider());
        assertTrue(provider.pushNotificationConfigStore() instanceof InMemoryPushNotificationConfigStore);
    }

    @Test
    void ofShouldKeepGivenStores() {
        final var taskStore = mock(TaskStore.class);
        final var stateProvider = mock(TaskStateProvider.class);
        final var pushStore = mock(PushNotificationConfigStore.class);

        final var provider = A2AStoreProvider.of(taskStore, stateProvider, pushStore);

        assertSame(taskStore, provider.taskStore());
        assertSame(stateProvider, provider.taskStateProvider());
        assertSame(pushStore, provider.pushNotificationConfigStore());
        assertNotNull(provider);
    }

    @Test
    void ofShouldRejectNullStores() {
        final var taskStore = mock(TaskStore.class);
        final var stateProvider = mock(TaskStateProvider.class);
        final var pushStore = mock(PushNotificationConfigStore.class);

        assertThrows(NullPointerException.class, () -> A2AStoreProvider.of(null, stateProvider, pushStore));
        assertThrows(NullPointerException.class, () -> A2AStoreProvider.of(taskStore, null, pushStore));
        assertThrows(NullPointerException.class, () -> A2AStoreProvider.of(taskStore, stateProvider, null));
    }
}
