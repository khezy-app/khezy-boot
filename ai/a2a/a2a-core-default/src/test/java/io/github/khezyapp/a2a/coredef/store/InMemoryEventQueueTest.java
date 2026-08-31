package io.github.khezyapp.a2a.coredef.store;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.Test;

class InMemoryEventQueueTest {

    private final InMemoryEventQueue queue = new InMemoryEventQueue();

    @Test
    void shouldDeliverPublishedEventsToSubscriber() {
        final var received = new ArrayList<TaskStatusUpdateEvent>();
        final var subscription = queue.subscribe("ctx", event -> received.add((TaskStatusUpdateEvent) event));
        final var event = statusEvent("ctx");

        queue.publish("ctx", event);

        assertEquals(List.of(event), received);
        subscription.unsubscribe();
    }

    @Test
    void unsubscribeShouldStopDelivery() {
        final var received = new ArrayList<TaskStatusUpdateEvent>();
        final var subscription = queue.subscribe("ctx", event -> received.add((TaskStatusUpdateEvent) event));

        subscription.unsubscribe();
        queue.publish("ctx", statusEvent("ctx"));

        assertEquals(List.of(), received);
    }

    @Test
    void oneSubscriberExceptionShouldNotBreakOthers() {
        queue.subscribe("ctx", event -> {
            throw new IllegalStateException("boom");
        });
        final var received = new ArrayList<TaskStatusUpdateEvent>();
        queue.subscribe("ctx", event -> received.add((TaskStatusUpdateEvent) event));
        final var event = statusEvent("ctx");

        assertDoesNotThrow(() -> queue.publish("ctx", event));

        assertEquals(List.of(event), received);
    }

    @Test
    void publishWithoutSubscribersIsSafe() {
        assertDoesNotThrow(() -> queue.publish("no-subscribers", statusEvent("x")));
    }

    private TaskStatusUpdateEvent statusEvent(final String contextId) {
        return new TaskStatusUpdateEvent(
                "t1", new TaskStatus(TaskState.TASK_STATE_WORKING), contextId, java.util.Map.of());
    }
}
