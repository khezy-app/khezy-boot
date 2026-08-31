package io.github.khezyapp.a2a.coredef.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khezyapp.a2a.core.dispatch.CallContext;
import io.github.khezyapp.a2a.core.error.TaskNotCancelableException;
import io.github.khezyapp.a2a.core.error.TaskNotFoundException;
import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import io.github.khezyapp.a2a.core.executor.A2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import io.github.khezyapp.a2a.core.store.PushNotificationSender;
import io.github.khezyapp.a2a.coredef.store.InMemoryEventQueue;
import io.github.khezyapp.a2a.coredef.store.InMemoryTaskStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;

class DefaultA2ARequestDispatcherTest {

    private final InMemoryTaskStore taskStore = new InMemoryTaskStore();
    private final InMemoryEventQueue eventQueue = new InMemoryEventQueue();
    private final RecordingListener listener = new RecordingListener();

    @Test
    void messageSendShouldTransitionSubmittedToCompletedAndPublishEvents() {
        eventQueue.subscribe("ctx-1", listener);
        final var result = statusEvent("task-1", "ctx-1", TaskState.TASK_STATE_WORKING);
        final var dispatcher = dispatcher(taskId -> result);

        final var returned = dispatcher.onMessageSend(params("task-1", "ctx-1"), callContext());

        assertEquals(result, returned);
        assertEquals(TaskState.TASK_STATE_COMPLETED, taskStore.find("task-1").orElseThrow().status().state());
        assertEquals(2, listener.events.size());
        assertEquals(TaskState.TASK_STATE_SUBMITTED,
                ((TaskStatusUpdateEvent) listener.events.get(0)).status().state());
        assertEquals(TaskState.TASK_STATE_COMPLETED,
                ((TaskStatusUpdateEvent) listener.events.get(1)).status().state());
    }

    @Test
    void cancelOfCompletedTaskShouldBeRejected() {
        final var dispatcher = dispatcher(taskId -> statusEvent(taskId, "ctx", TaskState.TASK_STATE_WORKING));
        dispatcher.onMessageSend(params("task-1", "ctx"), callContext());

        assertThrows(TaskNotCancelableException.class, () -> dispatcher.onCancelTask(new TaskIdParams("task-1"), null));
    }

    @Test
    void cancelOfActiveTaskShouldMarkCanceledAndPublish() {
        eventQueue.subscribe("ctx", listener);
        final var dispatcher = dispatcher(taskId -> statusEvent(taskId, "ctx", TaskState.TASK_STATE_WORKING));
        dispatcher.onMessageSend(params("task-1", "ctx"), callContext());
        taskStore.updateState("task-1", TaskState.TASK_STATE_WORKING);
        listener.events.clear();

        final var cancelled = dispatcher.onCancelTask(new TaskIdParams("task-1"), callContext());

        assertEquals(TaskState.TASK_STATE_CANCELED, cancelled.status().state());
        assertEquals(TaskState.TASK_STATE_CANCELED, taskStore.find("task-1").orElseThrow().status().state());
        assertEquals(1, listener.events.size());
        assertEquals(TaskState.TASK_STATE_CANCELED, ((TaskStatusUpdateEvent) listener.events.get(0)).status().state());
    }

    @Test
    void getTaskAbsentShouldReturnEmpty() {
        assertTrue(dispatcher(taskId -> null).onGetTask(new TaskQueryParams("ghost"), callContext()).isEmpty());
    }

    @Test
    void unknownCancelShouldThrowTaskNotFound() {
        assertThrows(TaskNotFoundException.class,
                () -> dispatcher(taskId -> null).onCancelTask(new TaskIdParams("ghost"), callContext()));
    }

    @Test
    void executorA2AErrorShouldPropagateAndFailTask() {
        final var error = new A2AError(-32001, "agent boom", Map.of());
        final var dispatcher = dispatcher(taskId -> {
            throw error;
        });

        final var thrown = assertThrows(A2AError.class,
                () -> dispatcher.onMessageSend(params("task-err", "ctx"), callContext()));

        assertEquals(error, thrown);
        assertEquals(TaskState.TASK_STATE_FAILED, taskStore.find("task-err").orElseThrow().status().state());
    }

    private DefaultA2ARequestDispatcher dispatcher(final Function<String, EventKind> behavior) {
        return new DefaultA2ARequestDispatcher(
                new StubExecutor(behavior),
                taskStore,
                eventQueue,
                new PushNotificationSenderStub(),
                () -> AgentCard.builder().name("test-agent").description("").version("1.0.0").build());
    }

    private MessageSendParams params(final String taskId,
                                     final String contextId) {
        return new MessageSendParams(
                Message.builder()
                        .role(Message.Role.ROLE_USER)
                        .parts(new TextPart("hi"))
                        .messageId("m-" + taskId)
                        .taskId(taskId)
                        .contextId(contextId)
                        .build(),
                null,
                Map.of("k", "v"));
    }

    private CallContext callContext() {
        return new CallContext() {
            @Override
            public CallerIdentity caller() {
                return CallerIdentity.anonymous();
            }

            @Override
            public Map<String, Object> attributes() {
                return Map.of();
            }
        };
    }

    private TaskStatusUpdateEvent statusEvent(final String taskId,
                                              final String contextId,
                                              final TaskState state) {
        return new TaskStatusUpdateEvent(taskId, new TaskStatus(state), contextId, Map.of());
    }

    private record StubExecutor(java.util.function.Function<String, EventKind> behavior) implements A2AAgentExecutor {
        @Override
        public EventKind execute(final AgentExecutionContext context) {
            return behavior.apply(context.incoming().taskId());
        }

        @Override
        public void cancel(final String taskId) {
            // cooperative cancel: no-op
        }
    }

    private static final class PushNotificationSenderStub implements PushNotificationSender {
        @Override
        public void send(final Task task) {
            // no-op
        }
    }

    private static final class RecordingListener implements java.util.function.Consumer<EventKind> {
        final List<EventKind> events = new ArrayList<>();

        @Override
        public void accept(final EventKind event) {
            events.add(event);
        }
    }
}
