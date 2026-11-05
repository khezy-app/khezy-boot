package io.github.khezyapp.a2a.core.server;

import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;

/**
 * Hook for adjusting the SDK request handler after it has been built.
 *
 * <p>The starter calls every customizer in ascending {@code @Order} after
 * {@link DefaultRequestHandler.Builder#build()}. Timeouts and other configuration values belong
 * in the SDK {@code A2AConfigProvider}, not here, because the handler reads them during
 * its post-construction initialization.</p>
 */
@FunctionalInterface
public interface A2AServerCustomizer {

    /**
     * Customizes the built request handler.
     *
     * @param handler the SDK request handler
     */
    void customize(DefaultRequestHandler handler);
}
