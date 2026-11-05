package io.github.khezyapp.a2a.store.jpa.task;

import org.a2aproject.sdk.spec.Task;

/**
 * Published after a locally created task is first persisted in a final state.
 *
 * <p>Mirrors the SDK extras {@code TaskFinalizedEvent} without CDI. Replicated saves never
 * publish it, so consumers do not react twice in a replicated deployment.</p>
 *
 * @param taskId the finalized task identifier
 * @param task the finalized task
 */
public record A2ATaskFinalizedEvent(String taskId, Task task) {
}
