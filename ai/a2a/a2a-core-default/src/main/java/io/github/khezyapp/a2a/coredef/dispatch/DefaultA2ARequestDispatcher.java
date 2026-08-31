package io.github.khezyapp.a2a.coredef.dispatch;

import io.github.khezyapp.a2a.core.error.A2ACoreException;
import io.github.khezyapp.a2a.core.error.TaskCancelledException;
import io.github.khezyapp.a2a.core.error.TaskNotCancelableException;
import io.github.khezyapp.a2a.core.error.TaskNotFoundException;
import io.github.khezyapp.a2a.core.executor.AbstractA2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import io.github.khezyapp.a2a.core.executor.A2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.A2AStreamingAgentExecutor;
import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.dispatch.CallContext;
import io.github.khezyapp.a2a.core.store.A2AEventQueue;
import io.github.khezyapp.a2a.core.store.A2ATaskStore;
import io.github.khezyapp.a2a.core.store.PushNotificationSender;
import io.github.khezyapp.a2a.coredef.executor.DefaultAgentExecutionContext;
import io.github.khezyapp.a2a.coredef.executor.SimpleCancellationToken;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;

/**
 * Default task lifecycle dispatcher: validate → resolve/create task → invoke executor →
 * persist events → return. Transport-free by design.
 */
public final class DefaultA2ARequestDispatcher implements A2ARequestDispatcher {

    private static final int INTERNAL_ERROR_CODE = -32000;

    private final A2AAgentExecutor executor;
    private final A2ATaskStore taskStore;
    private final A2AEventQueue eventQueue;
    private final PushNotificationSender pushNotificationSender;
    private final Supplier<AgentCard> agentCardSupplier;

    public DefaultA2ARequestDispatcher(final A2AAgentExecutor executor,
                                       final A2ATaskStore taskStore,
                                       final A2AEventQueue eventQueue,
                                       final PushNotificationSender pushNotificationSender,
                                       final Supplier<AgentCard> agentCardSupplier) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.taskStore = Objects.requireNonNull(taskStore, "taskStore");
        this.eventQueue = Objects.requireNonNull(eventQueue, "eventQueue");
        this.pushNotificationSender = Objects.requireNonNull(pushNotificationSender, "pushNotificationSender");
        this.agentCardSupplier = Objects.requireNonNull(agentCardSupplier, "agentCardSupplier");
    }

    @Override
    public EventKind onMessageSend(final MessageSendParams params,
                                   final CallContext ctx) {
        final var identity = resolveIdentity(params);
        final var taskId = identity.taskId();
        final var contextId = identity.contextId();
        final var message = params.message();
        final var metadata = metadataOrEmpty(params);

        taskStore.save(buildTask(taskId, contextId, TaskState.TASK_STATE_SUBMITTED, message));
        publishStatus(taskId, contextId, TaskState.TASK_STATE_SUBMITTED, metadata);
        pushNotificationSender.send(taskStore.find(taskId).orElseThrow());
        final var context = buildContext(params, ctx, taskId);
        try {
            final var result = executor.execute(context);
            completeTask(taskId, contextId, metadata);
            return result;
        } catch (final TaskCancelledException e) {
            cancelTask(taskId, contextId, metadata);
            return new TaskStatusUpdateEvent(
                    taskId, new TaskStatus(TaskState.TASK_STATE_CANCELED), contextId, metadata);
        } catch (final A2AError e) {
            failTask(taskId, contextId, e);
            throw e;
        } catch (final RuntimeException e) {
            failTask(taskId, contextId, new A2AError(INTERNAL_ERROR_CODE, "Internal error", Map.of()));
            throw new A2ACoreException("Agent execution failed for task: " + taskId, e);
        }
    }

    @Override
    public void onMessageStream(final MessageSendParams params,
                                final AgentEventSink sink,
                                final CallContext ctx) {
        final var identity = resolveIdentity(params);
        final var taskId = identity.taskId();
        final var contextId = identity.contextId();
        final var metadata = metadataOrEmpty(params);

        taskStore.save(buildTask(taskId, contextId, TaskState.TASK_STATE_SUBMITTED, params.message()));
        publishStatus(taskId, contextId, TaskState.TASK_STATE_SUBMITTED, metadata);
        pushNotificationSender.send(taskStore.find(taskId).orElseThrow());
        final var context = buildContext(params, ctx, taskId);
        final A2AStreamingAgentExecutor streaming =
                (executor instanceof final A2AStreamingAgentExecutor s) ? s : new StreamingFallback(executor);
        streaming.stream(context, new PersistingEventSink(sink, taskId, contextId));
    }

    @Override
    public Optional<Task> onGetTask(final TaskQueryParams query,
                                    final CallContext ctx) {
        Objects.requireNonNull(query, "query");
        return taskStore.find(query.id());
    }

    @Override
    public Task onCancelTask(final TaskIdParams params,
                             final CallContext ctx) {
        Objects.requireNonNull(params, "params");
        final var existing = taskStore.find(params.id())
                .orElseThrow(() -> new TaskNotFoundException(params.id()));
        if (existing.status().state().isFinal()) {
            throw new TaskNotCancelableException(params.id());
        }
        executor.cancel(params.id());
        taskStore.updateState(params.id(), TaskState.TASK_STATE_CANCELED);
        final var updated = taskStore.find(params.id()).orElseThrow();
        eventQueue.publish(
                updated.contextId(),
                new TaskStatusUpdateEvent(
                        updated.id(),
                        updated.status(),
                        updated.contextId(),
                        updated.metadata()
                )
        );
        pushNotificationSender.send(updated);
        return updated;
    }

    @Override
    public AgentCard agentCard(final CallContext ctx) {
        return agentCardSupplier.get();
    }

    private void completeTask(final String taskId,
                              final String contextId,
                              final Map<String, Object> metadata) {
        taskStore.updateState(taskId, TaskState.TASK_STATE_COMPLETED);
        publishStatus(taskId, contextId, TaskState.TASK_STATE_COMPLETED, metadata);
    }

    private void cancelTask(final String taskId,
                            final String contextId,
                            final Map<String, Object> metadata) {
        taskStore.updateState(taskId, TaskState.TASK_STATE_CANCELED);
        publishStatus(taskId, contextId, TaskState.TASK_STATE_CANCELED, metadata);
    }

    private void failTask(final String taskId,
                          final String contextId,
                          final A2AError error) {
        taskStore.updateState(taskId, TaskState.TASK_STATE_FAILED);
        eventQueue.publish(contextId, failedEvent(taskId, contextId, error));
    }

    private TaskStatusUpdateEvent failedEvent(final String taskId,
                                              final String contextId,
                                              final A2AError error) {
        return new TaskStatusUpdateEvent(
                taskId,
                new TaskStatus(TaskState.TASK_STATE_FAILED),
                contextId,
                Map.of("errorCode", error.getCode(), "errorMessage", String.valueOf(error.getMessage()))
        );
    }

    private void publishStatus(final String taskId,
                               final String contextId,
                               final TaskState state,
                               final Map<String, Object> metadata) {
        eventQueue.publish(
                contextId,
                new TaskStatusUpdateEvent(
                        taskId,
                        new TaskStatus(state),
                        contextId,
                        metadata
                )
        );
    }

    private AgentExecutionContext buildContext(final MessageSendParams params,
                                               final CallContext ctx,
                                               final String taskId) {
        return new DefaultAgentExecutionContext(
                params.message(),
                taskStore.find(taskId),
                ctx.caller(),
                ctx.attributes(),
                new SimpleCancellationToken()
        );
    }

    private Task buildTask(final String taskId,
                           final String contextId,
                           final TaskState state,
                           final Message message) {
        return Task.builder()
                .id(taskId)
                .contextId(contextId)
                .status(new TaskStatus(state))
                .history(List.of(message))
                .build();
    }

    private TaskIdentity resolveIdentity(final MessageSendParams params) {
        final var message = params.message();
        final var providedTaskId = message.taskId();
        if (Objects.nonNull(providedTaskId) && !providedTaskId.isBlank()) {
            final var contextId = (Objects.nonNull(message.contextId()) && !message.contextId().isBlank())
                    ? message.contextId() : providedTaskId;
            return new TaskIdentity(providedTaskId, contextId);
        }
        final var generated = UUID.randomUUID().toString();
        return new TaskIdentity(generated, generated);
    }

    private Map<String, Object> metadataOrEmpty(final MessageSendParams params) {
        final var metadata = params.metadata();
        return Objects.nonNull(metadata) ? metadata : Map.of();
    }

    private record TaskIdentity(String taskId, String contextId) {
    }

    /**
     * Bridges a blocking-only executor into the streaming contract.
     */
    private static final class StreamingFallback extends AbstractA2AAgentExecutor {

        private final A2AAgentExecutor delegate;

        private StreamingFallback(final A2AAgentExecutor delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public EventKind execute(final AgentExecutionContext context) {
            return delegate.execute(context);
        }
    }

    /**
     * Forwards every sink callback to the caller's sink AND persists state to store/queue.
     */
    private final class PersistingEventSink implements AgentEventSink {

        private final AgentEventSink delegate;
        private final String taskId;
        private final String contextId;

        private PersistingEventSink(final AgentEventSink delegate,
                                    final String taskId,
                                    final String contextId) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.taskId = Objects.requireNonNull(taskId, "taskId");
            this.contextId = Objects.requireNonNull(contextId, "contextId");
        }

        @Override
        public void statusChanged(final TaskState state,
                                  final String message) {
            delegate.statusChanged(state, message);
            if (!state.isFinal()) {
                taskStore.updateState(taskId, state);
                publishStatus(taskId, contextId, state, Map.of());
            }
        }

        @Override
        public void artifactAdded(final Artifact artifact) {
            delegate.artifactAdded(artifact);
        }

        @Override
        public void chunk(final Part delta) {
            delegate.chunk(delta);
        }

        @Override
        public void completed(final EventKind result) {
            delegate.completed(result);
            taskStore.updateState(taskId, TaskState.TASK_STATE_COMPLETED);
            publishStatus(taskId, contextId, TaskState.TASK_STATE_COMPLETED, Map.of());
            pushNotificationSender.send(taskStore.find(taskId).orElseThrow());
        }

        @Override
        public void failed(final A2AError error) {
            delegate.failed(error);
            taskStore.updateState(taskId, TaskState.TASK_STATE_FAILED);
            eventQueue.publish(contextId, failedEvent(taskId, contextId, error));
            pushNotificationSender.send(taskStore.find(taskId).orElseThrow());
        }
    }
}
