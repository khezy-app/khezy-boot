package io.github.khezyapp.a2a.core.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.a2aproject.sdk.spec.Part;

import io.github.khezyapp.a2a.core.client.invoke.SendRequest;

/**
 * Store-aware entry point for multi-turn conversations.
 *
 * <p>Injects the ids remembered for a conversation when the caller did not set them,
 * serializes calls for the same conversation, and records the outcome so the next turn
 * can continue an interrupted or pending task. Explicit ids always win.</p>
 *
 * <p>This class is transport-agnostic: it delegates the actual send to an
 * {@link A2AInvoker}.</p>
 */
public class A2AConnector {

    private static final int MAX_LOCKS = 512;

    private final A2AInvoker invoker;
    private final A2AConversationStore store;
    private final ConversationLockMap locks = new ConversationLockMap(MAX_LOCKS);

    /**
     * Creates a connector.
     *
     * @param invoker the send implementation
     * @param store the conversation store
     */
    public A2AConnector(final A2AInvoker invoker,
                        final A2AConversationStore store) {
        this.invoker = Objects.requireNonNull(invoker, "invoker must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    /**
     * Sends text parts to an agent, continuing the stored conversation when possible.
     *
     * @param agent the target agent
     * @param parts the message parts
     * @param skillId the requested skill id, or {@code null}
     * @param conversationId the host conversation id
     * @return the normalized outcome
     */
    public SendOutcome invoke(final RemoteAgent agent,
                              final List<Part<?>> parts,
                              final String skillId,
                              final String conversationId) {
        Objects.requireNonNull(agent, "agent must not be null");
        final var builder = SendRequest.builder()
                .agent(agent)
                .parts(parts)
                .skillId(skillId);
        return invoke(agent, builder, conversationId);
    }

    /**
     * Sends a builder-prepared request, continuing the stored conversation when possible.
     *
     * <p>The agent argument is authoritative and replaces any agent already set on the
     * builder.</p>
     *
     * @param agent the target agent
     * @param builder the request builder
     * @param conversationId the host conversation id
     * @return the normalized outcome
     */
    public SendOutcome invoke(final RemoteAgent agent,
                              final SendRequest.Builder builder,
                              final String conversationId) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(builder, "builder must not be null");
        final var conversationKey = requireConversation(conversationId);
        final var entry = locks.acquire(conversationKey);
        try {
            entry.lock().lock();
            try {
                builder.agent(agent);
                final var skillId = normalizeSkill(builder.skillId());
                injectStoredIds(builder, conversationKey, agent.name(), skillId);
                final var request = builder.build();
                final var outcome = invoker.send(request);
                Objects.requireNonNull(outcome, "invoker must return an outcome");
                record(conversationKey, agent.name(), skillId, request, outcome);
                return outcome;
            } finally {
                entry.lock().unlock();
            }
        } finally {
            locks.release(conversationKey, entry);
        }
    }

    private void injectStoredIds(final SendRequest.Builder builder,
                                 final String conversationId,
                                 final String agentName,
                                 final String skillId) {
        if (builder.hasExplicitTaskRef()) {
            return;
        }
        final var stored = store.ref(conversationId, agentName, skillId);
        if (Objects.isNull(stored)) {
            return;
        }
        if (stored.hasContext()) {
            builder.contextId(stored.contextId());
        }
        if (stored.hasTask()) {
            builder.taskId(stored.taskId());
        }
    }

    private void record(final String conversationId,
                        final String agentName,
                        final String skillId,
                        final SendRequest request,
                        final SendOutcome outcome) {
        if (outcome.isError()) {
            return;
        }
        final var contextId = RemotePartExtractor.firstNonBlank(outcome.contextId(), request.contextId());
        final var taskId = RemotePartExtractor.firstNonBlank(outcome.taskId(), request.taskId());
        if (outcome.isInterrupted()) {
            store.recordInterrupted(conversationId, agentName, skillId, contextId, taskId);
        } else if (outcome.isPending()) {
            store.recordPending(conversationId, agentName, skillId, contextId, taskId);
        } else {
            store.recordSettled(conversationId, agentName, skillId, contextId);
        }
    }

    private static String normalizeSkill(final String skillId) {
        return skillId == null ? "" : skillId;
    }

    private static String requireConversation(final String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        if (conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        return conversationId;
    }

    /**
     * A small access-ordered map of locks with usage counts.
     *
     * <p>Entries are only removed when nobody holds or waits for them, which keeps the
     * map bounded without ever handing two different locks to the same conversation.</p>
     */
    private static final class ConversationLockMap {

        private final int maxEntries;
        private final LinkedHashMap<String, LockEntry> entries = new LinkedHashMap<>(16, 0.75f, true);

        private ConversationLockMap(final int maxEntries) {
            this.maxEntries = maxEntries;
        }

        private LockEntry acquire(final String key) {
            synchronized (entries) {
                var entry = entries.get(key);
                if (Objects.isNull(entry)) {
                    evict();
                    entry = new LockEntry();
                    entries.put(key, entry);
                }
                entry.users++;
                return entry;
            }
        }

        private void release(final String key,
                             final LockEntry entry) {
            synchronized (entries) {
                entry.users--;
                if (entry.users == 0 && entries.size() > maxEntries) {
                    entries.remove(key, entry);
                }
            }
        }

        private void evict() {
            if (entries.size() < maxEntries) {
                return;
            }
            final var iterator = entries.entrySet().iterator();
            while (iterator.hasNext() && entries.size() >= maxEntries) {
                final var candidate = iterator.next();
                if (candidate.getValue().users == 0) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * One lock plus the number of callers holding or waiting for it.
     */
    private static final class LockEntry {

        private final ReentrantLock lock = new ReentrantLock();
        private int users;

        private ReentrantLock lock() {
            return lock;
        }
    }
}
