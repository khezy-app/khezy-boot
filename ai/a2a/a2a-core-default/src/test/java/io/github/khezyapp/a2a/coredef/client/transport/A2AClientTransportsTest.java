package io.github.khezyapp.a2a.coredef.client.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfig;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.junit.jupiter.api.Test;

import io.github.khezyapp.a2a.core.client.ClientBuildOptions;

class A2AClientTransportsTest {

    @Test
    void shouldDiscoverJsonRpcAndRest() {
        final var transports = A2AClientTransports.discover();

        assertTrue(transports.stream()
                .anyMatch(transport -> transport.transportClass().equals(JSONRPCTransport.class)));
        assertTrue(transports.stream()
                .anyMatch(transport -> transport.transportClass().equals(RestTransport.class)));
    }

    @Test
    void shouldDiscoverServiceLoaderAdditions() {
        final var transports = A2AClientTransports.discover();

        assertTrue(transports.stream()
                .anyMatch(transport -> transport.transportClass().equals(ClientTransport.class)));
    }

    @Test
    void jsonRpcConfigShouldCarrySharedHttpClientAndParameters() {
        final var httpClient = mock(A2AHttpClient.class);
        final var options = ClientBuildOptions.builder()
                .httpClient(httpClient)
                .transportParameters(Map.of("key", "value"))
                .build();

        final var config = (JSONRPCTransportConfig) new JsonRpcClientTransport()
                .createConfig(options, List.of());

        assertSame(httpClient, config.getHttpClient());
        assertEquals("value", config.getParameters().get("key"));
    }

    @Test
    void restConfigShouldCarrySharedHttpClientAndParameters() {
        final var httpClient = mock(A2AHttpClient.class);
        final var options = ClientBuildOptions.builder()
                .httpClient(httpClient)
                .transportParameters(Map.of("key", "value"))
                .build();

        final var config = (RestTransportConfig) new RestClientTransport()
                .createConfig(options, List.of());

        assertSame(httpClient, config.getHttpClient());
        assertEquals("value", config.getParameters().get("key"));
    }
}
