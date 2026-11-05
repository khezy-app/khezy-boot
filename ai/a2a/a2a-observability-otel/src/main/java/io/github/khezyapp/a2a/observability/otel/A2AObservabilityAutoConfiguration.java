package io.github.khezyapp.a2a.observability.otel;

import io.github.khezyapp.a2a.observability.otel.client.OpenTelemetryClientBuildOptionsPostProcessor;
import io.github.khezyapp.a2a.observability.otel.server.OpenTelemetryRequestHandler;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.a2aproject.sdk.extras.opentelemetry.A2AObservabilityNames;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Optional OpenTelemetry tracing for the A2A server and client.
 *
 * <p>Active only when an OpenTelemetry {@link Tracer} bean exists and the SDK
 * OpenTelemetry artifacts are on the classpath. Every bean is
 * {@link ConditionalOnMissingBean}, and the module never becomes a hard dependency of
 * {@code a2a-core} or the starter.</p>
 *
 * <p>The server wraps the SDK {@link DefaultRequestHandler} in a {@link RequestHandler}
 * bean marked {@link Primary}, so the JSON-RPC handler uses the traced handler while the
 * starter keeps its own handler bean (and customizers) untouched. The client replaces the
 * {@code ClientBuildOptions} bean through a post-processor, adding the SDK wrapper
 * parameters without depending on the starter.</p>
 */
@AutoConfiguration(afterName = "io.github.khezyapp.a2a.starter.server.A2AServerAutoConfiguration")
@ConditionalOnClass(name = "io.opentelemetry.api.trace.Tracer")
@ConditionalOnProperty(prefix = A2AObservabilityProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(A2AObservabilityProperties.class)
public class A2AObservabilityAutoConfiguration {

    /**
     * Creates the traced request handler.
     *
     * @param delegate the starter's SDK request handler
     * @param tracer the OpenTelemetry tracer
     * @param properties the observability properties
     * @return the traced request handler
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "a2aOpenTelemetryRequestHandler")
    @ConditionalOnClass(name = "org.a2aproject.sdk.extras.opentelemetry.server"
            + ".OpenTelemetryRequestHandlerDecorator")
    @ConditionalOnBean({Tracer.class, DefaultRequestHandler.class})
    public RequestHandler a2aOpenTelemetryRequestHandler(final DefaultRequestHandler delegate,
                                                         final Tracer tracer,
                                                         final A2AObservabilityProperties properties) {
        applyCaptureFlags(properties);
        return new OpenTelemetryRequestHandler(delegate, tracer);
    }

    /**
     * Applies the SDK payload-capture system properties when explicitly enabled.
     *
     * @param properties the observability properties
     */
    static void applyCaptureFlags(final A2AObservabilityProperties properties) {
        if (properties.isCaptureRequest()) {
            System.setProperty(A2AObservabilityNames.EXTRACT_REQUEST_SYS_PROPERTY, "true");
        }
        if (properties.isCaptureResponse()) {
            System.setProperty(A2AObservabilityNames.EXTRACT_RESPONSE_SYS_PROPERTY, "true");
        }
    }

    /** Client-side configuration, active when the SDK client wrappers are present. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.a2aproject.sdk.extras.opentelemetry.client.OpenTelemetryClientTransportWrapper",
            "org.a2aproject.sdk.extras.opentelemetry.client.propagation"
                    + ".OpenTelemetryClientPropagatorTransportWrapper"})
    @ConditionalOnBean(Tracer.class)
    static class ClientTracingConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "a2aOpenTelemetryClientBuildOptionsPostProcessor")
        static BeanPostProcessor a2aOpenTelemetryClientBuildOptionsPostProcessor(
                final Tracer tracer, final ObjectProvider<OpenTelemetry> openTelemetry) {
            return new OpenTelemetryClientBuildOptionsPostProcessor(tracer,
                    openTelemetry.getIfAvailable());
        }
    }
}
