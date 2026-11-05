package io.github.khezyapp.a2a.coredef.client.transport;

import java.util.List;

import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfig;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;

import io.github.khezyapp.a2a.core.client.ClientBuildOptions;

/**
 * Registers the SDK REST (HTTP+JSON) transport.
 */
public class RestClientTransport implements A2AClientTransport {

    @Override
    public Class<? extends ClientTransport> transportClass() {
        return RestTransport.class;
    }

    @Override
    public ClientTransportConfig<? extends ClientTransport> createConfig(
            final ClientBuildOptions options, final List<ClientCallInterceptor> interceptors) {
        final var builder = new RestTransportConfigBuilder().httpClient(options.httpClient());
        interceptors.forEach(builder::addInterceptor);
        final RestTransportConfig config = builder.build();
        config.setParameters(options.transportParameters());
        return config;
    }
}
