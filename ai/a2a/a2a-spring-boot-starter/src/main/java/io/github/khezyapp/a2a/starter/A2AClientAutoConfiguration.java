package io.github.khezyapp.a2a.starter;

import java.net.URI;

import io.github.khezyapp.a2a.core.client.A2ARemoteAgentClient;
import io.github.khezyapp.a2a.coredef.client.BearerTokenInterceptor;
import io.github.khezyapp.a2a.coredef.client.SdkA2ARemoteAgentClient;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * Wires the outbound remote-agent client when {@code io.github.khezyapp.a2a.client.base-url}
 * is configured. All {@code ClientCallInterceptor} beans in the context are registered on
 * the JSON-RPC transport, so adding authentication or tracing is just another bean. With
 * {@code client.auth.type=bearer} a static-token {@link BearerTokenInterceptor} is
 * contributed automatically; define your own interceptor bean for dynamic tokens. The
 * client bean itself stays {@code @ConditionalOnMissingBean} — provide your own to take
 * over completely.
 */
@AutoConfiguration
@EnableConfigurationProperties(A2AProperties.class)
@ConditionalOnClass(A2ARemoteAgentClient.class)
public class A2AClientAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(A2ARemoteAgentClient.class)
    @ConditionalOnProperty(prefix = "io.github.khezyapp.a2a.client", name = "base-url")
    A2ARemoteAgentClient a2aRemoteAgentClient(final A2AProperties properties,
                                              final ObjectProvider<ClientCallInterceptor> interceptors) {
        final var client = properties.getClient();
        if (client.getBaseUrl().isBlank()) {
            throw new IllegalStateException("io.github.khezyapp.a2a.client.base-url must not be blank");
        }
        return SdkA2ARemoteAgentClient.forJsonRpc(
                URI.create(client.getBaseUrl()),
                client.getTimeout(),
                interceptors.orderedStream().toList());
    }

    /**
     * Static-token auth contributed ahead of unordered user interceptors ({@code @Order(0)}),
     * so custom interceptors observe the final headers.
     */
    @Bean
    @Order(0)
    @ConditionalOnProperty(prefix = "io.github.khezyapp.a2a.client.auth", name = "type", havingValue = "bearer")
    ClientCallInterceptor bearerTokenInterceptor(final A2AProperties properties) {
        final var token = properties.getClient().getAuth().getToken();
        if (token.isBlank()) {
            throw new IllegalStateException(
                    "io.github.khezyapp.a2a.client.auth.token must be set when auth.type=bearer");
        }
        return new BearerTokenInterceptor(() -> token);
    }
}
