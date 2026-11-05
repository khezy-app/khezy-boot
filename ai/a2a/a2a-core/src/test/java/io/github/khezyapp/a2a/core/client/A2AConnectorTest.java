package io.github.khezyapp.a2a.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

import io.github.khezyapp.a2a.core.client.invoke.SendRequest;

class A2AConnectorTest {

    private final RemoteAgent agent = ClientFixtures.agent();

    @Test
    void newConversationShouldSendNoIds() {
        final var store = new FakeStore();
        final var seen = new AtomicReference<SendRequest>();
        final var connector = new A2AConnector(request -> {
            seen.set(request);
            return SendOutcome.error(request.agent(), null, "stop");
        }, store);

        connector.invoke(agent, List.of(new TextPart("hello")), "echo", "conv-1");

        assertNull(seen.get().contextId());
        assertNull(seen.get().taskId());
    }

    @Test
    void storedInterruptedIdsShouldBeInjected() {
        final var store = new FakeStore();
        store.recordInterrupted("conv-1", "echo-agent", "echo", "ctx-stored", "task-stored");
        final var seen = new AtomicReference<SendRequest>();
        final var connector = new A2AConnector(request -> {
            seen.set(request);
            return SendOutcome.error(request.agent(), null, "stop");
        }, store);

        connector.invoke(agent, SendRequest.builder().part(new TextPart("hello")).skillId("echo"), "conv-1");

        assertEquals("ctx-stored", seen.get().contextId());
        assertEquals("task-stored", seen.get().taskId());
    }

    @Test
    void explicitIdsShouldWinOverStoredIds() {
        final var store = new FakeStore();
        store.recordInterrupted("conv-1", "echo-agent", "echo", "ctx-stored", "task-stored");
        final var seen = new AtomicReference<SendRequest>();
        final var connector = new A2AConnector(request -> {
            seen.set(request);
            return SendOutcome.error(request.agent(), null, "stop");
        }, store);

        final var builder = SendRequest.builder()
                .part(new TextPart("hello"))
                .skillId("echo")
                .contextId("ctx-explicit")
                .taskId("task-explicit");
        connector.invoke(agent, builder, "conv-1");

        assertEquals("ctx-explicit", seen.get().contextId());
        assertEquals("task-explicit", seen.get().taskId());
    }

    @Test
    void interruptedResultShouldKeepBothIds() {
        final var store = new FakeStore();
        final var connector = new A2AConnector(request -> SendOutcome.ofTask(
                request.agent(), null,
                ClientFixtures.task(TaskState.TASK_STATE_INPUT_REQUIRED, "ctx-1", "task-1")), store);

        final var outcome = connector.invoke(agent, List.of(new TextPart("hello")), "echo", "conv-1");

        assertTrue(outcome.isInterrupted());
        final var ref = store.ref("conv-1", "echo-agent", "echo");
        assertEquals("ctx-1", ref.contextId());
        assertEquals("task-1", ref.taskId());
    }

    @Test
    void pendingResultShouldKeepBothIds() {
        final var store = new FakeStore();
        final var connector = new A2AConnector(request -> SendOutcome.ofTask(
                request.agent(), null,
                ClientFixtures.task(TaskState.TASK_STATE_WORKING, "ctx-1", "task-1")), store);

        connector.invoke(agent, List.of(new TextPart("hello")), "echo", "conv-1");

        final var ref = store.ref("conv-1", "echo-agent", "echo");
        assertEquals("ctx-1", ref.contextId());
        assertEquals("task-1", ref.taskId());
    }

    @Test
    void settledResultShouldKeepContextAndDropTask() {
        final var store = new FakeStore();
        store.recordInterrupted("conv-1", "echo-agent", "echo", "ctx-1", "task-old");
        final var connector = new A2AConnector(request -> SendOutcome.ofTask(
                request.agent(), null,
                ClientFixtures.task(TaskState.TASK_STATE_COMPLETED, "ctx-1", "task-1")), store);

        connector.invoke(agent, List.of(new TextPart("hello")), "echo", "conv-1");

        final var ref = store.ref("conv-1", "echo-agent", "echo");
        assertEquals("ctx-1", ref.contextId());
        assertNull(ref.taskId());
        assertFalse(ref.hasTask());
    }

    @Test
    void errorResultShouldLeaveStoreUnchanged() {
        final var store = new FakeStore();
        store.recordInterrupted("conv-1", "echo-agent", "echo", "ctx-stored", "task-stored");
        final var connector = new A2AConnector(
                request -> SendOutcome.error(request.agent(), null, "boom"), store);

        final var outcome = connector.invoke(agent, List.of(new TextPart("hello")), "echo", "conv-1");

        assertTrue(outcome.isError());
        final var ref = store.ref("conv-1", "echo-agent", "echo");
        assertEquals("ctx-stored", ref.contextId());
        assertEquals("task-stored", ref.taskId());
    }

    @Test
    void nullOutcomeShouldFail() {
        final var connector = new A2AConnector(request -> null, new FakeStore());

        assertThrows(NullPointerException.class,
                () -> connector.invoke(agent, List.of(new TextPart("hello")), "echo", "conv-1"));
    }

    @Test
    void sameConversationCallsShouldBeSerialized() throws Exception {
        final var store = new FakeStore();
        final var firstStarted = new CountDownLatch(1);
        final var allowFirst = new CountDownLatch(1);
        final var secondEntered = new CountDownLatch(1);
        final var calls = new AtomicInteger();
        final var active = new AtomicInteger();
        final var maxActive = new AtomicInteger();
        final A2AInvoker invoker = request -> {
            final var current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            try {
                if (calls.incrementAndGet() == 1) {
                    firstStarted.countDown();
                    await(allowFirst);
                } else {
                    secondEntered.countDown();
                }
                return SendOutcome.error(request.agent(), null, "stop");
            } finally {
                active.decrementAndGet();
            }
        };
        final var connector = new A2AConnector(invoker, store);
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var first = executor.submit(
                    () -> connector.invoke(agent, List.of(new TextPart("one")), "echo", "conv-1"));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            final var second = executor.submit(
                    () -> connector.invoke(agent, List.of(new TextPart("two")), "echo", "conv-1"));

            assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS));

            allowFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertEquals(0, secondEntered.getCount());
            assertEquals(1, maxActive.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectMissingConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new A2AConnector(null, new FakeStore()));
        assertThrows(NullPointerException.class,
                () -> new A2AConnector(request -> SendOutcome.error(agent, null, "x"), null));
    }

    @Test
    void shouldRejectNullAgentAndBlankConversation() {
        final var connector = new A2AConnector(
                request -> SendOutcome.error(request.agent(), null, "x"), new FakeStore());

        assertThrows(NullPointerException.class,
                () -> connector.invoke(null, List.of(new TextPart("hello")), "echo", "conv-1"));
        assertThrows(IllegalArgumentException.class,
                () -> connector.invoke(agent, List.of(new TextPart("hello")), "echo", " "));
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class FakeStore implements A2AConversationStore {

        private final Map<String, TaskRef> refs = new ConcurrentHashMap<>();

        @Override
        public TaskRef ref(final String conversationId,
                           final String agentName,
                           final String skillId) {
            return refs.getOrDefault(key(conversationId, agentName, skillId), TaskRef.empty());
        }

        @Override
        public void recordInterrupted(final String conversationId,
                                      final String agentName,
                                      final String skillId,
                                      final String contextId,
                                      final String taskId) {
            refs.put(key(conversationId, agentName, skillId), new TaskRef(contextId, taskId));
        }

        @Override
        public void recordPending(final String conversationId,
                                  final String agentName,
                                  final String skillId,
                                  final String contextId,
                                  final String taskId) {
            refs.put(key(conversationId, agentName, skillId), new TaskRef(contextId, taskId));
        }

        @Override
        public void recordSettled(final String conversationId,
                                  final String agentName,
                                  final String skillId,
                                  final String contextId) {
            refs.put(key(conversationId, agentName, skillId), new TaskRef(contextId, null));
        }

        @Override
        public void clearConversation(final String conversationId) {
            final var prefix = conversationId + '\u0000';
            refs.keySet().removeIf(key -> key.startsWith(prefix));
        }

        @Override
        public void clear() {
            refs.clear();
        }

        private static String key(final String conversationId,
                                  final String agentName,
                                  final String skillId) {
            return conversationId + '\u0000' + agentName + '\u0000' + skillId;
        }
    }
}
