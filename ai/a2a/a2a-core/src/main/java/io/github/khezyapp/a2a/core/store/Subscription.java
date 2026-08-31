package io.github.khezyapp.a2a.core.store;

/** Handle for an active event subscription. */
public interface Subscription extends AutoCloseable {

    void unsubscribe();

    @Override
    default void close() {
        unsubscribe();
    }
}
