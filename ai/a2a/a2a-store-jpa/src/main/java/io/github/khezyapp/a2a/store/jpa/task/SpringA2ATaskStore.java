package io.github.khezyapp.a2a.store.jpa.task;

import io.github.khezyapp.a2a.core.server.A2AContextKeys;
import io.github.khezyapp.a2a.store.jpa.crypto.A2ACipherContexts;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import io.github.khezyapp.crypto.defaults.KhezyCipherTextCodec;
import jakarta.persistence.criteria.Predicate;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.tasks.TaskPersistenceException;
import org.a2aproject.sdk.server.tasks.TaskSerializationException;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.util.PageToken;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Spring/JPA implementation of the SDK {@link TaskStore} and {@link TaskStateProvider}.
 *
 * <p>Payloads are serialized with the SDK {@code JsonUtil} (so Part/Message discriminators
 * match the wire format) and encrypted with the {@link StringEncryptor} using the
 * {@code a2a:task:<taskId>} AAD. Operational columns stay plaintext for querying. Tasks
 * written before encryption was enabled remain readable: a payload without the
 * {@code kzy:v1:} envelope is treated as plaintext.</p>
 */
public class SpringA2ATaskStore implements TaskStore, TaskStateProvider {

    /** SDK-compatible grace period for late events on finalized tasks. */
    public static final Duration DEFAULT_GRACE_PERIOD = Duration.ofSeconds(15);

    private static final Sort ORDER_BY =
            Sort.by(Sort.Order.desc("statusTimestamp"), Sort.Order.asc("id"));

    private static final ApplicationEventPublisher NO_EVENTS = event -> {
    };

    private final A2ATaskRepository repository;
    private final StringEncryptor encryptor;
    private final Duration gracePeriod;
    private final ApplicationEventPublisher events;

    /**
     * Creates a store with the default grace period and no event publisher.
     *
     * @param repository the task repository
     * @param encryptor the payload encryptor
     */
    public SpringA2ATaskStore(final A2ATaskRepository repository, final StringEncryptor encryptor) {
        this(repository, encryptor, DEFAULT_GRACE_PERIOD, NO_EVENTS);
    }

    /**
     * Creates a store with explicit grace period and event publisher.
     *
     * @param repository the task repository
     * @param encryptor the payload encryptor
     * @param gracePeriod how long finalized tasks remain active for late events
     * @param events publisher for {@link A2ATaskFinalizedEvent}
     */
    public SpringA2ATaskStore(final A2ATaskRepository repository,
                              final StringEncryptor encryptor,
                              final Duration gracePeriod,
                              final ApplicationEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.encryptor = Objects.requireNonNull(encryptor, "encryptor must not be null");
        this.gracePeriod = Objects.requireNonNull(gracePeriod, "gracePeriod must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
    }

    @Override
    @Transactional
    public void save(final Task task, final boolean isReplicated) {
        Objects.requireNonNull(task, "task must not be null");
        final var payload = encode(task);
        final boolean finalizedNow;
        try {
            final var existing = repository.findById(task.id()).orElse(null);
            if (Objects.isNull(existing)) {
                finalizedNow = task.status().state().isFinal();
                repository.save(A2ATaskEntity.fromTask(task, payload));
            } else {
                finalizedNow = task.status().state().isFinal()
                        && Objects.isNull(existing.getFinalizedAt());
                existing.updateFrom(task, payload);
                repository.save(existing);
            }
        } catch (final RuntimeException e) {
            throw new TaskPersistenceException(task.id(), "Database save failed for task", e);
        }
        if (!isReplicated && finalizedNow) {
            events.publishEvent(new A2ATaskFinalizedEvent(task.id(), task));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable Task get(final String taskId) {
        final var entity = findEntity(taskId);
        return Objects.isNull(entity) ? null : decode(entity, taskId);
    }

    @Override
    @Transactional
    public void delete(final String taskId) {
        try {
            repository.deleteById(taskId);
        } catch (final RuntimeException e) {
            throw new TaskPersistenceException(taskId, "Database deletion failed for task", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTaskActive(final String taskId) {
        final var entity = findEntity(taskId);
        if (Objects.isNull(entity)) {
            return false;
        }
        if (!isFinal(entity)) {
            return true;
        }
        final var finalizedAt = entity.getFinalizedAt();
        return Objects.nonNull(finalizedAt)
                && Instant.now().isBefore(finalizedAt.plus(gracePeriod));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTaskFinalized(final String taskId) {
        final var entity = findEntity(taskId);
        return Objects.nonNull(entity) && isFinal(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ListTasksResult list(final ListTasksParams params,
                                final @Nullable ServerCallContext context) {
        Objects.requireNonNull(params, "params must not be null");
        final var pageSize = params.getEffectivePageSize();
        final var specification = specification(params, context);
        final int totalSize;
        final List<A2ATaskEntity> rows;
        try {
            totalSize = (int) repository.count(specification);
            rows = repository.findAll(specification, PageRequest.of(0, pageSize + 1, ORDER_BY))
                    .getContent();
        } catch (final RuntimeException e) {
            throw new TaskPersistenceException(null, "Database query failed during list operation", e);
        }

        final var hasMore = rows.size() > pageSize;
        final var page = hasMore ? rows.subList(0, pageSize) : rows;
        final var tasks = new ArrayList<Task>(page.size());
        for (final var entity : page) {
            tasks.add(trim(decode(entity, entity.getId()),
                    params.getEffectiveHistoryLength(), params.shouldIncludeArtifacts()));
        }

        String nextPageToken = null;
        if (hasMore && !page.isEmpty()) {
            final var last = page.get(page.size() - 1);
            nextPageToken = new PageToken(last.getStatusTimestamp(), last.getId()).toString();
        }
        return new ListTasksResult(tasks, totalSize, tasks.size(), nextPageToken);
    }

    private Specification<A2ATaskEntity> specification(final ListTasksParams params,
                                                       final @Nullable ServerCallContext context) {
        final var scope = scopeOf(context);
        final var contextId = trimToNull(params.contextId());
        final var state = Objects.isNull(params.status()) ? null : params.status().name();
        final var statusTimestampAfter = params.statusTimestampAfter();
        final var token = PageToken.fromString(params.pageToken());
        return (root, query, builder) -> {
            final var predicates = new ArrayList<Predicate>();
            if (Objects.nonNull(scope)) {
                predicates.add(scope.isBlank()
                        ? builder.disjunction()
                        : builder.equal(root.get("agentScope"), scope));
            }
            if (Objects.nonNull(contextId)) {
                predicates.add(builder.equal(root.get("contextId"), contextId));
            }
            if (Objects.nonNull(state)) {
                predicates.add(builder.equal(root.get("state"), state));
            }
            if (Objects.nonNull(statusTimestampAfter)) {
                predicates.add(builder.greaterThan(root.get("statusTimestamp"),
                        statusTimestampAfter));
            }
            if (Objects.nonNull(token)) {
                predicates.add(builder.or(
                        builder.lessThan(root.get("statusTimestamp"), token.timestamp()),
                        builder.and(
                                builder.equal(root.get("statusTimestamp"), token.timestamp()),
                                builder.greaterThan(root.get("id"), token.id()))));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String encode(final Task task) {
        try {
            final var json = JsonUtil.toJson(task);
            return encryptor.encrypt(json, A2ACipherContexts.task(task.id()));
        } catch (final JsonProcessingException e) {
            throw new TaskSerializationException(task.id(),
                    "Failed to serialize task for persistence", e);
        }
    }

    private Task decode(final A2ATaskEntity entity, final String taskId) {
        try {
            final var payload = decrypt(entity.getTaskData(), taskId);
            return entity.toTask(payload);
        } catch (final JsonProcessingException e) {
            throw new TaskSerializationException(taskId,
                    "Failed to deserialize task from database", e);
        } catch (final RuntimeException e) {
            throw new TaskSerializationException(taskId,
                    "Failed to decrypt task payload", e);
        }
    }

    private String decrypt(final String stored, final String taskId) {
        if (stored.startsWith(KhezyCipherTextCodec.PREFIX + ":")) {
            return encryptor.decrypt(stored, A2ACipherContexts.task(taskId));
        }
        return stored;
    }

    private @Nullable A2ATaskEntity findEntity(final String taskId) {
        try {
            return repository.findById(taskId).orElse(null);
        } catch (final RuntimeException e) {
            throw new TaskPersistenceException(taskId, "Database retrieval failed for task", e);
        }
    }

    private static boolean isFinal(final A2ATaskEntity entity) {
        final var state = entity.getState();
        if (Objects.isNull(state)) {
            return false;
        }
        try {
            return TaskState.valueOf(state).isFinal();
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    private static @Nullable String scopeOf(final @Nullable ServerCallContext context) {
        if (Objects.isNull(context)) {
            return null;
        }
        final var value = context.getState().get(A2AContextKeys.AGENT_SCOPE);
        return Objects.isNull(value) ? "" : value.toString();
    }

    private static @Nullable String trimToNull(final @Nullable String value) {
        return Objects.isNull(value) || value.isBlank() ? null : value.trim();
    }

    private static Task trim(final Task task,
                             final int historyLength,
                             final boolean includeArtifacts) {
        List<Message> history = task.history();
        if (historyLength == 0) {
            history = List.of();
        } else if (history.size() > historyLength) {
            history = history.subList(history.size() - historyLength, history.size());
        }
        final List<Artifact> artifacts = includeArtifacts ? task.artifacts() : List.of();
        if (history == task.history() && artifacts == task.artifacts()) {
            return task;
        }
        return Task.builder(task).artifacts(artifacts).history(history).build();
    }
}
