package io.github.khezyapp.a2a.store.jpa.task;

import io.github.khezyapp.a2a.core.server.A2AContextKeys;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.Task;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * JPA entity for an A2A {@link Task}.
 *
 * <p>The full task is stored as serialized JSON (SDK proto-JSON shape) in {@code task_data};
 * operational columns ({@code context_id}, {@code state}, {@code status_timestamp},
 * {@code agent_scope}, {@code finalized_at}) are denormalized for querying. The payload is
 * already encrypted when it reaches this entity: entities never own an encryptor.</p>
 */
@Entity
@Table(name = "a2a_tasks",
        indexes = @Index(name = "idx_a2a_tasks_agent_scope", columnList = "agent_scope"))
public class A2ATaskEntity {

    @Id
    @Column(name = "task_id", nullable = false)
    private String id;

    @Column(name = "context_id")
    private String contextId;

    @Column(name = "state")
    private String state;

    @Column(name = "status_timestamp")
    private Instant statusTimestamp;

    @Column(name = "agent_scope")
    private String agentScope;

    @Column(name = "task_data", columnDefinition = "TEXT", nullable = false)
    private String taskData;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    /**
     * Creates an empty entity for JPA.
     */
    protected A2ATaskEntity() {
    }

    /**
     * Creates an entity from a task and its already-encrypted payload.
     *
     * @param task the task to persist
     * @param encryptedPayload the encrypted serialized task
     * @return the new entity
     */
    public static A2ATaskEntity fromTask(final Task task, final String encryptedPayload) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(encryptedPayload, "encryptedPayload must not be null");
        final var entity = new A2ATaskEntity();
        entity.applyTask(task);
        entity.taskData = encryptedPayload;
        return entity;
    }

    /**
     * Updates the denormalized columns and payload from a newer task revision.
     *
     * <p>The existing {@code agent_scope} is preserved when the incoming task carries no
     * scope metadata, because intermediate SDK task updates are built without it.</p>
     *
     * @param task the task to persist
     * @param encryptedPayload the encrypted serialized task
     */
    public void updateFrom(final Task task, final String encryptedPayload) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(encryptedPayload, "encryptedPayload must not be null");
        applyTask(task);
        this.taskData = encryptedPayload;
    }

    /**
     * Deserializes the task from a decrypted payload.
     *
     * @param plaintextPayload the decrypted serialized task
     * @return the task
     * @throws JsonProcessingException when the payload is not valid task JSON
     */
    public Task toTask(final String plaintextPayload) throws JsonProcessingException {
        return JsonUtil.fromJson(plaintextPayload, Task.class);
    }

    private void applyTask(final Task task) {
        this.id = task.id();
        this.contextId = task.contextId();
        final var status = task.status();
        this.state = status.state().name();
        this.statusTimestamp = status.timestamp().toInstant()
                .truncatedTo(ChronoUnit.MILLIS);
        final var scope = agentScope(task);
        if (Objects.nonNull(scope)) {
            this.agentScope = scope;
        }
        if (status.state().isFinal() && Objects.isNull(this.finalizedAt)) {
            this.finalizedAt = Instant.now();
        }
    }

    private static @Nullable String agentScope(final Task task) {
        final var metadata = task.metadata();
        if (Objects.isNull(metadata)) {
            return null;
        }
        final var value = metadata.get(A2AContextKeys.AGENT_SCOPE);
        if (value instanceof String scope && !scope.isBlank()) {
            return scope;
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public String getContextId() {
        return contextId;
    }

    public String getState() {
        return state;
    }

    public Instant getStatusTimestamp() {
        return statusTimestamp;
    }

    public String getAgentScope() {
        return agentScope;
    }

    public String getTaskData() {
        return taskData;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }
}
