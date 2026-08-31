package io.github.khezyapp.a2a.coredef.store;

import io.github.khezyapp.a2a.core.store.A2AEventQueue;
import io.github.khezyapp.a2a.core.store.Subscription;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.a2aproject.sdk.spec.EventKind;

/**
 * In-memory, keyed-by-contextId {@link A2AEventQueue}. Listener exceptions are swallowed
 * so one broken subscriber never breaks other subscribers or the publisher.
 */
public final class InMemoryEventQueue implements A2AEventQueue {

    private static final System.Logger LOG = System.getLogger(InMemoryEventQueue.class.getName());

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<EventKind>>> subscribers =
            new ConcurrentHashMap<>();

    @Override
    public void publish(final String contextId,
                        final EventKind event) {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(event, "event");
        final var listeners = subscribers.get(contextId);
        if (Objects.isNull(listeners)) {
            return;
        }
        for (final var listener : listeners) {
            try {
                listener.accept(event);
            } catch (final RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Event subscriber threw for context " + contextId, e);
            }
        }
    }

    @Override
    public Subscription subscribe(final String contextId,
                                  final Consumer<EventKind> listener) {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(listener, "listener");
        final var list = subscribers.computeIfAbsent(contextId, key -> new CopyOnWriteArrayList<>());
        list.add(listener);
        return () -> list.remove(listener);
    }
}
