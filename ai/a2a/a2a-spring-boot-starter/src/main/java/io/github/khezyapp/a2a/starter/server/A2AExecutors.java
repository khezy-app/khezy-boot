package io.github.khezyapp.a2a.starter.server;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executor factory for the SDK server graph.
 *
 * <p>Virtual threads are the default. The library targets Java 17 bytecode, so the
 * Java 21 factory is looked up reflectively and the platform pool is used as a fallback
 * when the runtime does not provide virtual threads.</p>
 */
final class A2AExecutors {

    private static final Logger LOG = LoggerFactory.getLogger(A2AExecutors.class);

    private A2AExecutors() {
    }

    /**
     * Creates the agent executor, using virtual threads when available.
     *
     * @param virtualThreads whether virtual threads are requested
     * @param platformThreads the platform pool size
     * @param queueCapacity the platform pool queue capacity
     * @return the executor
     */
    static Executor agentExecutor(final boolean virtualThreads, final int platformThreads,
                                  final int queueCapacity) {
        if (virtualThreads) {
            try {
                final var factory = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
                return (Executor) factory.invoke(null);
            } catch (final ReflectiveOperationException | RuntimeException e) {
                LOG.warn("Virtual threads are not available on this runtime; "
                        + "falling back to a platform thread pool. Set "
                        + "'io.github.khezyapp.a2a.server.executor.virtual-threads=false' to silence this warning.");
            }
        }
        return new ThreadPoolExecutor(
                Math.max(1, platformThreads),
                Math.max(1, platformThreads),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(1, queueCapacity)),
                daemonFactory("a2a-agent-"));
    }

    /**
     * Creates the uncapped daemon pool used to consume agent events.
     *
     * @return the executor
     */
    static Executor eventConsumerExecutor() {
        return Executors.newCachedThreadPool(daemonFactory("a2a-event-consumer-"));
    }

    private static ThreadFactory daemonFactory(final String prefix) {
        final var counter = new AtomicInteger(1);
        return runnable -> {
            final var thread = new Thread(runnable, prefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
