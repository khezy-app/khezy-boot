package io.github.khezyapp.a2a.sampleserver;

import org.a2aproject.sdk.server.events.StreamCloseHandle;
import org.a2aproject.sdk.server.events.TaskStreamLifecycleHook;
import org.a2aproject.sdk.spec.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional SDK 1.4 demo: logs task-stream subscriber lifecycle events.
 *
 * <p>The default hook does nothing; this one keeps the same no-op behavior and only adds
 * observability. Remove the bean to fall back to the SDK default hook.</p>
 */
@Configuration(proxyBeanMethods = false)
public class StreamLifecycleLoggingConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(StreamLifecycleLoggingConfiguration.class);

    /**
     * Creates a lifecycle hook that logs subscribe/unsubscribe/event counts.
     *
     * @return the logging hook
     */
    @Bean
    public TaskStreamLifecycleHook loggingTaskStreamLifecycleHook() {
        return new TaskStreamLifecycleHook() {
            @Override
            public void onSubscribe(final String taskId,
                                    final StreamCloseHandle handle) {
                LOG.info("Stream subscribed: task={} subscribers={}", taskId, handle.getActiveSubscriberCount());
            }

            @Override
            public void onUnsubscribe(final String taskId,
                                      final StreamCloseHandle handle) {
                LOG.info("Stream unsubscribed: task={} subscribers={}", taskId, handle.getActiveSubscriberCount());
            }

            @Override
            public void onEvent(final String taskId,
                                final Event event,
                                final StreamCloseHandle handle) {
                LOG.debug("Stream event: task={} type={}", taskId, event.getClass().getSimpleName());
            }
        };
    }
}
