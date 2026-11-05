package io.github.khezyapp.a2a.coredef.client.transport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ServiceLoader;

import org.a2aproject.sdk.client.transport.spi.ClientTransport;

/**
 * Discovers the available SDK client transports.
 *
 * <p>JSON-RPC and REST are always present; additional transports are loaded with
 * {@link ServiceLoader}. A service-provided transport never replaces a built-in
 * registration for the same SDK class.</p>
 */
public final class A2AClientTransports {

    private A2AClientTransports() {
    }

    /**
     * Returns the built-in and service-loaded transports.
     *
     * @return an immutable list of transports
     */
    public static List<A2AClientTransport> discover() {
        final var unique = new LinkedHashMap<Class<? extends ClientTransport>, A2AClientTransport>();
        for (final var transport : List.of(new JsonRpcClientTransport(), new RestClientTransport())) {
            unique.put(transport.transportClass(), transport);
        }
        for (final var transport : ServiceLoader.load(A2AClientTransport.class)) {
            unique.putIfAbsent(transport.transportClass(), transport);
        }
        return List.copyOf(unique.values());
    }
}
