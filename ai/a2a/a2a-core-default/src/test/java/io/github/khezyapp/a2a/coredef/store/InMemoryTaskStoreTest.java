package io.github.khezyapp.a2a.coredef.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khezyapp.a2a.core.error.TaskStoreException;
import java.util.List;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.Test;

class InMemoryTaskStoreTest {

    private final InMemoryTaskStore store = new InMemoryTaskStore();

    @Test
    void shouldSaveAndFindTask() {
        final var task = task("t1", TaskState.TASK_STATE_SUBMITTED);

        store.save(task);

        assertTrue(store.find("t1").isPresent());
        assertEquals(task, store.find("t1").orElseThrow());
    }

    @Test
    void shouldReturnEmptyForMissingTask() {
        assertTrue(store.find("missing").isEmpty());
    }

    @Test
    void shouldUpdateStateOfExistingTask() {
        store.save(task("t1", TaskState.TASK_STATE_SUBMITTED));

        store.updateState("t1", TaskState.TASK_STATE_COMPLETED);

        final var updated = store.find("t1").orElseThrow();
        assertEquals(TaskState.TASK_STATE_COMPLETED, updated.status().state());
        assertEquals("t1", updated.id());
        assertEquals("c1", updated.contextId());
    }

    @Test
    void shouldThrowOnUpdateStateOfMissingTask() {
        assertThrows(TaskStoreException.class, () -> store.updateState("ghost", TaskState.TASK_STATE_FAILED));
    }

    private Task task(final String id,
                      final TaskState state) {
        return Task.builder()
                .id(id)
                .contextId("c1")
                .status(new TaskStatus(state))
                .history(List.of(
                        Message.builder()
                                .role(Message.Role.ROLE_USER)
                                .parts(new TextPart("hi"))
                                .messageId("m")
                                .build()))
                .build();
    }
}
