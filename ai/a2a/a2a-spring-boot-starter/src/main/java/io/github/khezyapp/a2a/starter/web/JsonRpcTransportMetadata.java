package io.github.khezyapp.a2a.starter.web;

import org.a2aproject.sdk.server.TransportMetadata;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Declares the JSON-RPC HTTP transport to the SDK card validator.
 *
 * <p>Registered through {@code META-INF/services/org.a2aproject.sdk.server.TransportMetadata};
 * without it the validator rejects cards that advertise a JSON-RPC interface.</p>
 */
public class JsonRpcTransportMetadata implements TransportMetadata {

    @Override
    public String getTransportProtocol() {
        return TransportProtocol.JSONRPC.asString();
    }
}
