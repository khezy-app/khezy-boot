package io.github.khezyapp.a2a.coredef.executor;

import io.github.khezyapp.a2a.core.executor.CancellationToken;

import java.util.concurrent.atomic.AtomicBoolean;

/** Minimal thread-safe {@link io.github.khezyapp.a2a.core.executor.CancellationToken}. */
public final class SimpleCancellationToken implements CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public boolean isCancellationRequested() {
        return cancelled.get();
    }

    @Override
    public void requestCancellation() {
        cancelled.set(true);
    }
}
