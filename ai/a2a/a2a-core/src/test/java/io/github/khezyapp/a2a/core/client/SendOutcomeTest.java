package io.github.khezyapp.a2a.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class SendOutcomeTest {

    private final RemoteAgent agent = ClientFixtures.agent();

    @Test
    void ofTaskShouldMapStateIdsMessageAndArtifacts() {
        final var task = ClientFixtures.task(TaskState.TASK_STATE_COMPLETED, "ctx-1", "task-1");

        final var outcome = SendOutcome.ofTask(agent, null, task);

        assertEquals(TaskState.TASK_STATE_COMPLETED, outcome.state());
        assertEquals("ctx-1", outcome.contextId());
        assertEquals("task-1", outcome.taskId());
        assertEquals("status text", outcome.message());
        assertTrue(outcome.artifacts().isEmpty());
        assertNull(outcome.error());
        assertTrue(outcome.isFinal());
        assertFalse(outcome.isInterrupted());
        assertFalse(outcome.isPending());
    }

    @Test
    void ofMessageShouldBeFinalWithoutTaskState() {
        final var message = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("hello")))
                .contextId("ctx-1")
                .taskId("task-1")
                .build();

        final var outcome = SendOutcome.ofMessage(agent, null, message);

        assertNull(outcome.state());
        assertEquals("hello", outcome.message());
        assertEquals("ctx-1", outcome.contextId());
        assertEquals("task-1", outcome.taskId());
        assertTrue(outcome.isFinal());
        assertFalse(outcome.isPending());
    }

    @Test
    void errorShouldNotBeFinalOrPending() {
        final var outcome = SendOutcome.error(agent, null, "boom");

        assertTrue(outcome.isError());
        assertFalse(outcome.isFinal());
        assertFalse(outcome.isInterrupted());
        assertFalse(outcome.isPending());
        assertTrue(outcome.artifacts().isEmpty());
        assertEquals("boom", outcome.error());
    }

    @Test
    void interruptedStateShouldBeInterruptedNotPending() {
        final var task = ClientFixtures.task(TaskState.TASK_STATE_INPUT_REQUIRED, "ctx-1", "task-1");

        final var outcome = SendOutcome.ofTask(agent, null, task);

        assertTrue(outcome.isInterrupted());
        assertFalse(outcome.isFinal());
        assertFalse(outcome.isPending());
    }

    @Test
    void workingStateShouldBePending() {
        final var task = ClientFixtures.task(TaskState.TASK_STATE_WORKING, "ctx-1", "task-1");

        final var outcome = SendOutcome.ofTask(agent, null, task);

        assertTrue(outcome.isPending());
        assertFalse(outcome.isFinal());
        assertFalse(outcome.isInterrupted());
    }

    @Test
    void taskRefShouldCarryBothIds() {
        final var task = ClientFixtures.task(TaskState.TASK_STATE_AUTH_REQUIRED, "ctx-1", "task-1");

        final var ref = SendOutcome.ofTask(agent, null, task).taskRef();

        assertEquals("ctx-1", ref.contextId());
        assertEquals("task-1", ref.taskId());
        assertTrue(ref.hasContext());
        assertTrue(ref.hasTask());
    }

    @Test
    void constructorShouldRejectNullAgent() {
        assertThrows(NullPointerException.class,
                () -> SendOutcome.ofTask(null, null,
                        ClientFixtures.task(TaskState.TASK_STATE_COMPLETED, "ctx", "task")));
    }

    @Test
    void artifactsShouldBeImmutable() {
        final var outcome = SendOutcome.ofTask(agent, null,
                ClientFixtures.task(TaskState.TASK_STATE_COMPLETED, "ctx", "task"));

        assertThrows(UnsupportedOperationException.class,
                () -> outcome.artifacts().add(new RemoteArtifact("a", "b", "c")));
        assertSame(agent, outcome.agent());
    }
}
