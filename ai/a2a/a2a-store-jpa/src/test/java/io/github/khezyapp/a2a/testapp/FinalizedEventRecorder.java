package io.github.khezyapp.a2a.testapp;

import io.github.khezyapp.a2a.store.jpa.task.A2ATaskFinalizedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captures {@link A2ATaskFinalizedEvent} instances so tests can assert exact publish counts.
 */
public class FinalizedEventRecorder {

    private final List<String> taskIds = new CopyOnWriteArrayList<>();

    /**
     * Records a finalized-task event.
     *
     * @param event the published event
     */
    @EventListener
    public void onFinalized(final A2ATaskFinalizedEvent event) {
        taskIds.add(event.taskId());
    }

    /**
     * Returns the finalized task ids observed so far.
     *
     * @return an immutable snapshot
     */
    public List<String> taskIds() {
        return List.copyOf(taskIds);
    }

    /**
     * Clears the recorded events.
     */
    public void clear() {
        taskIds.clear();
    }
}
