package io.github.khezyapp.a2a.coredef.client.transport;

import java.util.List;

import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;

import io.github.khezyapp.a2a.core.client.ClientBuildOptions;

/**
 * Supplies one SDK client transport and its configuration.
 *
 * <p>JSON-RPC and REST are built in. Other transports (for example gRPC) can be
 * contributed through {@link java.util.ServiceLoader}.</p>
 */
public interface A2AClientTransport {

    /**
     * Returns the SDK transport class used as the registration key.
     *
     * @return the transport class
     */
    Class<? extends ClientTransport> transportClass();

    /**
     * Creates the transport configuration.
     *
     * @param options the shared client options
     * @param interceptors the interceptors to apply
     * @return the transport configuration
     */
    ClientTransportConfig<? extends ClientTransport> createConfig(ClientBuildOptions options,
                                                                  List<ClientCallInterceptor> interceptors);
}
