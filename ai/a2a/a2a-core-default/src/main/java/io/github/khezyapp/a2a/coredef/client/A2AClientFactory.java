package io.github.khezyapp.a2a.coredef.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.AuthInterceptor;
import org.a2aproject.sdk.spec.AgentCard;

import io.github.khezyapp.a2a.core.client.ClientBuildOptions;
import io.github.khezyapp.a2a.coredef.client.transport.A2AClientTransport;
import io.github.khezyapp.a2a.coredef.client.transport.A2AClientTransports;

/**
 * Builds SDK clients from agent cards.
 *
 * <p>Every discovered transport receives the shared HTTP client and the same
 * interceptors. Output modes fall back from the request to the card defaults and
 * finally to text. Streaming is requested only when the card advertises it.</p>
 */
public class A2AClientFactory {

    /**
     * Creates an SDK client.
     *
     * @param card the resolved agent card
     * @param options the client build options
     * @return the client
     */
    public Client create(final AgentCard card, final ClientBuildOptions options) {
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(options, "options must not be null");

        final var config = ClientConfig.builder()
                .setAcceptedOutputModes(acceptedOutputModes(card, options))
                .setStreaming(options.streaming() && card.capabilities().streaming())
                .setPolling(options.polling())
                .setUseClientPreference(options.useClientPreference())
                .build();
        final var builder = Client.builder(card).clientConfig(config);
        final var interceptors = interceptors(options);
        for (final var transport : A2AClientTransports.discover()) {
            register(builder, transport, options, interceptors);
        }
        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(final ClientBuilder builder, final A2AClientTransport transport,
                                 final ClientBuildOptions options,
                                 final List<ClientCallInterceptor> interceptors) {
        final ClientTransportConfig config = transport.createConfig(options, interceptors);
        builder.withTransport(transport.transportClass(), config);
    }

    private static List<String> acceptedOutputModes(final AgentCard card, final ClientBuildOptions options) {
        if (!options.acceptedOutputModes().isEmpty()) {
            return options.acceptedOutputModes();
        }
        final var cardModes = card.defaultOutputModes();
        if (cardModes != null && !cardModes.isEmpty()) {
            return cardModes;
        }
        return List.of("text");
    }

    private static List<ClientCallInterceptor> interceptors(final ClientBuildOptions options) {
        final var interceptors = new ArrayList<ClientCallInterceptor>();
        if (options.credentialService() != null) {
            interceptors.add(new AuthInterceptor(options.credentialService()));
        }
        interceptors.addAll(options.interceptors());
        return List.copyOf(interceptors);
    }
}
