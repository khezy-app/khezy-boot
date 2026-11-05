package io.github.khezyapp.a2a.core.server;

import java.util.Objects;

import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;

/**
 * Bundles the three SDK stores the server runtime needs.
 *
 * <p>Implementations may share one object between {@link #taskStore()} and
 * {@link #taskStateProvider()} (as the in-memory implementation does) or use separate
 * adapters for persistence.</p>
 */
public interface A2AStoreProvider {

    /**
     * Returns the task persistence store.
     *
     * @return the task store
     */
    TaskStore taskStore();

    /**
     * Returns the task state provider used for queue lifecycle decisions.
     *
     * @return the task state provider
     */
    TaskStateProvider taskStateProvider();

    /**
     * Returns the push notification configuration store.
     *
     * @return the push notification config store
     */
    PushNotificationConfigStore pushNotificationConfigStore();

    /**
     * Creates an in-memory provider.
     *
     * <p>The same {@link InMemoryTaskStore} instance backs both the task store and the
     * task state provider.</p>
     *
     * @return the in-memory provider
     */
    static A2AStoreProvider inMemory() {
        final var taskStore = new InMemoryTaskStore();
        return of(taskStore, taskStore, new InMemoryPushNotificationConfigStore());
    }

    /**
     * Creates a provider from existing stores.
     *
     * @param taskStore the task store
     * @param taskStateProvider the task state provider
     * @param pushNotificationConfigStore the push notification config store
     * @return the provider
     */
    static A2AStoreProvider of(
            final TaskStore taskStore,
            final TaskStateProvider taskStateProvider,
            final PushNotificationConfigStore pushNotificationConfigStore
    ) {
        Objects.requireNonNull(taskStore, "taskStore must not be null");
        Objects.requireNonNull(taskStateProvider, "taskStateProvider must not be null");
        Objects.requireNonNull(pushNotificationConfigStore, "pushNotificationConfigStore must not be null");

        return new A2AStoreProvider() {
            @Override
            public TaskStore taskStore() {
                return taskStore;
            }

            @Override
            public TaskStateProvider taskStateProvider() {
                return taskStateProvider;
            }

            @Override
            public PushNotificationConfigStore pushNotificationConfigStore() {
                return pushNotificationConfigStore;
            }
        };
    }
}
