package io.github.khezyapp.a2a.coredef.client.transport;

import java.util.List;

import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;

import io.github.khezyapp.a2a.core.client.ClientBuildOptions;

/**
 * Test-only transport contributed through {@link java.util.ServiceLoader}.
 */
public class ServiceLoadedTestTransport implements A2AClientTransport {

    @Override
    public Class<? extends ClientTransport> transportClass() {
        return ClientTransport.class;
    }

    @Override
    public ClientTransportConfig<? extends ClientTransport> createConfig(
            final ClientBuildOptions options, final List<ClientCallInterceptor> interceptors) {
        return null;
    }
}
