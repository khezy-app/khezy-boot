package io.github.khezyapp.a2a.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.khezyapp.a2a.core.error.A2ACoreException;
import io.github.khezyapp.a2a.core.error.TaskCancelledException;
import io.github.khezyapp.a2a.core.error.TaskNotCancelableException;
import io.github.khezyapp.a2a.core.error.TaskNotFoundException;
import io.github.khezyapp.a2a.core.error.TaskStoreException;
import org.junit.jupiter.api.Test;

class ErrorTaxonomyTest {

    @Test
    void taskNotCancelableShouldCarryTaskIdAndMessage() {
        final TaskNotCancelableException e = new TaskNotCancelableException("task-1");

        assertEquals("task-1", e.getTaskId());
        assertEquals("Task is not cancelable: task-1", e.getMessage());
    }

    @Test
    void taskNotFoundShouldCarryTaskIdAndMessage() {
        final TaskNotFoundException e = new TaskNotFoundException("task-2");

        assertEquals("task-2", e.getTaskId());
        assertEquals("Task not found: task-2", e.getMessage());
    }

    @Test
    void taskCancelledShouldCarryMessage() {
        final TaskCancelledException e = new TaskCancelledException("task-3");

        assertEquals("Task cancelled cooperatively: task-3", e.getMessage());
    }

    @Test
    void taskStoreShouldCarryCause() {
        final RuntimeException cause = new RuntimeException("boom");
        final TaskStoreException e = new TaskStoreException("persist failed", cause);

        assertEquals("persist failed", e.getMessage());
        assertSame(cause, e.getCause());
    }

    @Test
    void taskNotCancelableShouldBeA2ACoreException() {
        assertInstanceOf(A2ACoreException.class, new TaskNotCancelableException("t"));
    }
}
