package io.github.khezyapp.a2a.coredef.client.transport;

import java.util.List;

import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;

import io.github.khezyapp.a2a.core.client.ClientBuildOptions;

/**
 * Registers the SDK JSON-RPC transport.
 */
public class JsonRpcClientTransport implements A2AClientTransport {

    @Override
    public Class<? extends ClientTransport> transportClass() {
        return JSONRPCTransport.class;
    }

    @Override
    public ClientTransportConfig<? extends ClientTransport> createConfig(
            final ClientBuildOptions options, final List<ClientCallInterceptor> interceptors) {
        final var builder = new JSONRPCTransportConfigBuilder().httpClient(options.httpClient());
        interceptors.forEach(builder::addInterceptor);
        final JSONRPCTransportConfig config = builder.build();
        config.setParameters(options.transportParameters());
        return config;
    }
}
