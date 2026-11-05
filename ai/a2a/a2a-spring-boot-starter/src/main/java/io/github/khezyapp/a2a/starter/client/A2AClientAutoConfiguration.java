package io.github.khezyapp.a2a.starter.client;

import java.time.Clock;

import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.a2aproject.sdk.client.http.SSEParserConfig;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.CredentialService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.github.khezyapp.a2a.core.client.A2AClientOperations;
import io.github.khezyapp.a2a.core.client.A2AConnector;
import io.github.khezyapp.a2a.core.client.A2AConversationStore;
import io.github.khezyapp.a2a.core.client.A2AInvoker;
import io.github.khezyapp.a2a.core.client.AgentCardResolver;
import io.github.khezyapp.a2a.core.client.ClientBuildOptions;
import io.github.khezyapp.a2a.coredef.client.A2AClientFactory;
import io.github.khezyapp.a2a.coredef.client.RemoteAgentRegistry;
import io.github.khezyapp.a2a.coredef.client.card.AgentCardSource;
import io.github.khezyapp.a2a.coredef.client.card.CachingAgentCardResolver;
import io.github.khezyapp.a2a.coredef.client.card.SdkAgentCardSource;
import io.github.khezyapp.a2a.coredef.client.invoke.SdkA2AInvoker;
import io.github.khezyapp.a2a.coredef.client.store.InMemoryA2AConversationStore;
import io.github.khezyapp.a2a.starter.A2AClientProperties;

/**
 * Wires the KHEZY A2A client graph.
 *
 * <p>Active only when {@code io.github.khezyapp.a2a.client.enabled=true}. One shared
 * {@link A2AHttpClient} — built with the configured SSE parser limits — is used for card
 * discovery and every client transport, and is closed on shutdown when the provider
 * returns an {@link AutoCloseable} implementation.</p>
 */
@AutoConfiguration(afterName = "io.github.khezyapp.a2a.store.jpa.A2AJpaStoreAutoConfiguration")
@ConditionalOnClass({A2AClientFactory.class, A2AHttpClient.class})
@ConditionalOnProperty(prefix = A2AClientProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(A2AClientProperties.class)
public class A2AClientAutoConfiguration {

    /**
     * Creates the shared HTTP client with the configured SSE limits.
     *
     * @param properties the client properties
     * @return the HTTP client
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AHttpClient a2aHttpClient(final A2AClientProperties properties) {
        final var sse = properties.getSse();
        final var config = SSEParserConfig.builder()
                .maxLineLength(sse.getMaxLineLength())
                .maxBufferLines(sse.getMaxBufferLines())
                .maxBufferChars(sse.getMaxBufferChars())
                .build();
        return A2AHttpClientFactory.createWithSseConfig(config);
    }

    /**
     * Closes the HTTP client on shutdown when it implements {@link AutoCloseable}.
     *
     * @param httpClient the shared HTTP client
     * @return the destroy callback
     */
    @Bean
    @ConditionalOnMissingBean(name = "a2aHttpClientCloser")
    public DisposableBean a2aHttpClientCloser(final A2AHttpClient httpClient) {
        return () -> {
            if (httpClient instanceof AutoCloseable closeable) {
                closeable.close();
            }
        };
    }

    /**
     * Creates the card source.
     *
     * @param httpClient the shared HTTP client
     * @param properties the client properties
     * @return the card source
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentCardSource agentCardSource(final A2AHttpClient httpClient, final A2AClientProperties properties) {
        final var cardPath = properties.getAgentCardPath();
        return new SdkAgentCardSource(httpClient, cardPath == null || cardPath.isBlank() ? null : cardPath,
                properties.getAuthHeaders(), null);
    }

    /**
     * Creates the caching card resolver.
     *
     * @param source the card source
     * @param properties the client properties
     * @return the card resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentCardResolver agentCardResolver(final AgentCardSource source,
                                               final A2AClientProperties properties) {
        return new CachingAgentCardResolver(source, properties.getCardCacheTtl(), Clock.systemUTC());
    }

    /**
     * Creates the client build options.
     *
     * @param httpClient the shared HTTP client
     * @param interceptors optional client call interceptors
     * @param credentialServices optional credential service
     * @param properties the client properties
     * @return the build options
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientBuildOptions clientBuildOptions(final A2AHttpClient httpClient,
                                                 final ObjectProvider<ClientCallInterceptor> interceptors,
                                                 final ObjectProvider<CredentialService> credentialServices,
                                                 final A2AClientProperties properties) {
        return ClientBuildOptions.builder()
                .httpClient(httpClient)
                .acceptedOutputModes(properties.getAcceptedOutputModes())
                .streaming(properties.isStreaming())
                .useClientPreference(properties.isUseClientPreference())
                .interceptors(interceptors.orderedStream().toList())
                .credentialService(credentialServices.getIfUnique())
                .transportParameters(properties.getTransportParameters())
                .build();
    }

    /**
     * Creates the SDK client factory.
     *
     * @return the client factory
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AClientFactory a2aClientFactory() {
        return new A2AClientFactory();
    }

    /**
     * Creates the remote agent registry and registers the configured URLs.
     *
     * @param cardResolver the card resolver
     * @param clientFactory the client factory
     * @param options the client build options
     * @param properties the client properties
     * @return the registry
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RemoteAgentRegistry remoteAgentRegistry(final AgentCardResolver cardResolver,
                                                   final A2AClientFactory clientFactory,
                                                   final ClientBuildOptions options,
                                                   final A2AClientProperties properties) {
        return new RemoteAgentRegistry(cardResolver, clientFactory, options, properties.getUrls());
    }

    /**
     * Creates the SDK-backed invoker.
     *
     * @return the invoker
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AInvoker a2aInvoker() {
        return new SdkA2AInvoker();
    }

    /**
     * Creates the in-memory conversation store.
     *
     * @param properties the client properties
     * @return the conversation store
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AConversationStore a2aConversationStore(final A2AClientProperties properties) {
        return new InMemoryA2AConversationStore(properties.getConversationStateTtl(),
                properties.getMaxConversations());
    }

    /**
     * Creates the multi-turn connector.
     *
     * @param invoker the invoker
     * @param store the conversation store
     * @return the connector
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AConnector a2aConnector(final A2AInvoker invoker, final A2AConversationStore store) {
        return new A2AConnector(invoker, store);
    }

    /**
     * Creates the task and push-config operations facade.
     *
     * @return the operations facade
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AClientOperations a2aClientOperations() {
        return new A2AClientOperations();
    }
}
