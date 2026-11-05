package io.github.khezyapp.a2a.store.jpa.conversation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import io.github.khezyapp.a2a.core.client.A2AConversationStore;
import io.github.khezyapp.a2a.core.client.TaskRef;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed conversation store for multi-node multi-turn resumption.
 *
 * <p>Entries are keyed by conversation, agent, and skill, exactly like the in-memory
 * default. A settled task keeps its context id and drops the task id so the next turn
 * starts a new task in the same context. Entries are not encrypted: only A2A routing
 * identifiers are stored, and those identifiers are already visible on the wire. The
 * in-memory LRU bound does not apply; expiry is enforced on read and through
 * {@link #purgeExpired()}.</p>
 */
public class SpringA2AConversationStore implements A2AConversationStore {

    /** Default time-to-live for a conversation entry. */
    public static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final A2AConversationRepository repository;
    private final Duration ttl;
    private final Clock clock;

    /**
     * Creates a store with the default TTL and the system clock.
     *
     * @param repository the conversation repository
     */
    public SpringA2AConversationStore(final A2AConversationRepository repository) {
        this(repository, DEFAULT_TTL, Clock.systemUTC());
    }

    /**
     * Creates a store with an explicit TTL and the system clock.
     *
     * @param repository the conversation repository
     * @param ttl the entry time-to-live
     */
    public SpringA2AConversationStore(final A2AConversationRepository repository,
                                      final Duration ttl) {
        this(repository, ttl, Clock.systemUTC());
    }

    /**
     * Creates a store.
     *
     * @param repository the conversation repository
     * @param ttl the entry time-to-live
     * @param clock the clock used for expiry
     */
    public SpringA2AConversationStore(final A2AConversationRepository repository,
                                      final Duration ttl, final Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public TaskRef ref(final String conversationId, final String agentName, final String skillId) {
        final var id = new A2AConversationId(conversationId, agentName, skillId);
        final var entity = repository.findById(id).orElse(null);
        if (Objects.isNull(entity)) {
            return TaskRef.empty();
        }
        if (!clock.instant().isBefore(entity.getExpiresAt())) {
            repository.delete(entity);
            return TaskRef.empty();
        }
        return entity.toTaskRef();
    }

    @Override
    @Transactional
    public void recordInterrupted(final String conversationId, final String agentName,
                                  final String skillId, final String contextId,
                                  final String taskId) {
        record(conversationId, agentName, skillId, contextId, taskId);
    }

    @Override
    @Transactional
    public void recordPending(final String conversationId, final String agentName,
                              final String skillId, final String contextId,
                              final String taskId) {
        record(conversationId, agentName, skillId, contextId, taskId);
    }

    @Override
    @Transactional
    public void recordSettled(final String conversationId, final String agentName,
                              final String skillId, final String contextId) {
        record(conversationId, agentName, skillId, contextId, null);
    }

    @Override
    @Transactional
    public void clearConversation(final String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        repository.deleteByConversationId(conversationId);
    }

    @Override
    @Transactional
    public void clear() {
        repository.deleteAllInBatch();
    }

    /**
     * Deletes entries whose expiry passed before the current clock instant.
     *
     * @return the number of deleted rows
     */
    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(clock.instant());
    }

    private void record(final String conversationId, final String agentName,
                        final String skillId, final String contextId, final String taskId) {
        final var id = new A2AConversationId(conversationId, agentName, skillId);
        if (isBlank(contextId) && isBlank(taskId)) {
            repository.findById(id).ifPresent(repository::delete);
            return;
        }
        final Instant now = clock.instant();
        final var expiresAt = now.plus(ttl);
        final var entity = repository.findById(id)
                .map(existing -> {
                    existing.update(contextId, taskId, now, expiresAt);
                    return existing;
                })
                .orElseGet(() -> A2AConversationEntity.of(id, contextId, taskId, now, expiresAt));
        repository.save(entity);
    }

    private static boolean isBlank(final String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}
