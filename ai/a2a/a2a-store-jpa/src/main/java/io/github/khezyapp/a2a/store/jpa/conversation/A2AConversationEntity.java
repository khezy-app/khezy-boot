package io.github.khezyapp.a2a.store.jpa.conversation;

import io.github.khezyapp.a2a.core.client.TaskRef;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity for one conversation continuation entry.
 *
 * <p>Only routing identifiers are stored, never message payloads: the host conversation
 * id, agent, skill, A2A context id and task id. {@code updated_at} drives the
 * time-to-live check and {@code expires_at} is kept denormalized so operators can purge
 * expired rows with a single query.</p>
 */
@Entity
@Table(name = "a2a_conversations",
        indexes = {
                @Index(name = "idx_a2a_conversations_conversation", columnList = "conversation_id"),
                @Index(name = "idx_a2a_conversations_expires", columnList = "expires_at")
        })
public class A2AConversationEntity {

    @EmbeddedId
    private A2AConversationId id;

    @Column(name = "context_id")
    private String contextId;

    @Column(name = "task_id")
    private String taskId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Creates an empty entity for JPA.
     */
    protected A2AConversationEntity() {
    }

    /**
     * Creates an entry.
     *
     * @param id the composite id
     * @param contextId the A2A context id, or {@code null}
     * @param taskId the A2A task id, or {@code null}
     * @param updatedAt when the entry was written
     * @param expiresAt when the entry stops being returned
     * @return the entity
     */
    public static A2AConversationEntity of(final A2AConversationId id, final String contextId,
                                           final String taskId, final Instant updatedAt,
                                           final Instant expiresAt) {
        final var entity = new A2AConversationEntity();
        entity.id = Objects.requireNonNull(id, "id must not be null");
        entity.update(contextId, taskId, updatedAt, expiresAt);
        return entity;
    }

    /**
     * Updates the continuation ids and timestamps.
     *
     * @param contextId the A2A context id, or {@code null}
     * @param taskId the A2A task id, or {@code null}
     * @param updatedAt when the entry was written
     * @param expiresAt when the entry stops being returned
     */
    public void update(final String contextId, final String taskId, final Instant updatedAt,
                       final Instant expiresAt) {
        this.contextId = contextId;
        this.taskId = taskId;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    /**
     * Returns the continuation reference held by this entry.
     *
     * @return the task reference
     */
    public TaskRef toTaskRef() {
        return new TaskRef(contextId, taskId);
    }

    public A2AConversationId getId() {
        return id;
    }

    public String getContextId() {
        return contextId;
    }

    public String getTaskId() {
        return taskId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
