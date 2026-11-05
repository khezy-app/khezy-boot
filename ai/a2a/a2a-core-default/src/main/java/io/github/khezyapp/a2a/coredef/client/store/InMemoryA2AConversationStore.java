package io.github.khezyapp.a2a.coredef.client.store;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.khezyapp.a2a.core.client.A2AConversationStore;
import io.github.khezyapp.a2a.core.client.TaskRef;

/**
 * In-memory conversation store with TTL and LRU bounds.
 *
 * <p>Entries are keyed by conversation, agent, and skill. A settled task keeps its
 * context id and drops the task id so the next turn starts a new task in the same
 * context.</p>
 */
public class InMemoryA2AConversationStore implements A2AConversationStore {

    /**
     * The default entry time-to-live.
     */
    public static final Duration DEFAULT_TTL = Duration.ofHours(1);

    /**
     * The default maximum number of entries.
     */
    public static final int DEFAULT_MAX_ENTRIES = 1000;

    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Key, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * Creates a store with the default TTL, bound, and system clock.
     */
    public InMemoryA2AConversationStore() {
        this(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    /**
     * Creates a store with the default system clock.
     *
     * @param ttl the entry time-to-live
     * @param maxEntries the maximum number of entries
     */
    public InMemoryA2AConversationStore(final Duration ttl, final int maxEntries) {
        this(ttl, maxEntries, Clock.systemUTC());
    }

    /**
     * Creates a store.
     *
     * @param ttl the entry time-to-live
     * @param maxEntries the maximum number of entries
     * @param clock the clock used for expiry
     */
    public InMemoryA2AConversationStore(final Duration ttl, final int maxEntries, final Clock clock) {
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public synchronized TaskRef ref(final String conversationId, final String agentName, final String skillId) {
        final var key = key(conversationId, agentName, skillId);
        final var entry = entries.get(key);
        if (entry == null) {
            return TaskRef.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(key);
            return TaskRef.empty();
        }
        return entry.ref();
    }

    @Override
    public synchronized void recordInterrupted(final String conversationId, final String agentName,
                                               final String skillId, final String contextId,
                                               final String taskId) {
        record(conversationId, agentName, skillId, new TaskRef(contextId, taskId));
    }

    @Override
    public synchronized void recordPending(final String conversationId, final String agentName,
                                           final String skillId, final String contextId,
                                           final String taskId) {
        record(conversationId, agentName, skillId, new TaskRef(contextId, taskId));
    }

    @Override
    public synchronized void recordSettled(final String conversationId, final String agentName,
                                           final String skillId, final String contextId) {
        record(conversationId, agentName, skillId, new TaskRef(contextId, null));
    }

    @Override
    public synchronized void clearConversation(final String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        final Iterator<Key> iterator = entries.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().conversationId().equals(conversationId)) {
                iterator.remove();
            }
        }
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    /**
     * Returns the number of live entries.
     *
     * @return the number of entries, including not-yet-purged expired entries
     */
    public synchronized int size() {
        return entries.size();
    }

    private void record(final String conversationId, final String agentName,
                        final String skillId, final TaskRef ref) {
        final var key = key(conversationId, agentName, skillId);
        if (!ref.hasContext() && !ref.hasTask()) {
            entries.remove(key);
            return;
        }
        entries.put(key, new CacheEntry(ref, clock.instant().plus(ttl)));
        evict();
    }

    private void evict() {
        final Iterator<Key> iterator = entries.keySet().iterator();
        while (entries.size() > maxEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static Key key(final String conversationId, final String agentName, final String skillId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(agentName, "agentName must not be null");
        return new Key(conversationId, agentName, skillId == null ? "" : skillId);
    }

    private record Key(String conversationId, String agentName, String skillId) {
    }

    private record CacheEntry(TaskRef ref, Instant expiresAt) {
    }
}
