package io.github.khezyapp.a2a.core.client;

import io.github.khezyapp.a2a.core.client.invoke.SendRequest;

/**
 * Sends one request to a remote agent.
 *
 * <p>The invoker owns transport details and timeout behavior. It returns a normalized
 * {@link SendOutcome}; implementations are expected to convert SDK failures into
 * {@link SendOutcome#error} rather than leak transport exceptions to callers.</p>
 */
@FunctionalInterface
public interface A2AInvoker {

    /**
     * Sends a request and waits according to its options.
     *
     * @param request the request to send
     * @return the normalized outcome
     */
    SendOutcome send(SendRequest request);
}
