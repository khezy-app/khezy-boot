package io.github.khezyapp.a2a.observability.otel.client;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.extras.opentelemetry.client.propagation.OpenTelemetryClientPropagatorTransport;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the SDK propagation wrapper injects W3C trace headers into client calls.
 */
class OpenTelemetryClientPropagatorTransportTest {

    @Test
    void injectsTraceparentHeader() {
        final var tracerProvider = SdkTracerProvider.builder().build();
        final var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        final Tracer tracer = openTelemetry.getTracer("test");
        final var captured = new AtomicReference<ClientCallContext>();
        final var delegate = (ClientTransport) Proxy.newProxyInstance(
                ClientTransport.class.getClassLoader(), new Class<?>[]{ClientTransport.class},
                (proxy, method, args) -> {
                    if ("getTask".equals(method.getName())) {
                        captured.set((ClientCallContext) args[1]);
                    }
                    return null;
                });
        final var transport = new OpenTelemetryClientPropagatorTransport(delegate, openTelemetry);

        final Span span = tracer.spanBuilder("client").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            transport.getTask(new TaskQueryParams("task-1"), null);
        } finally {
            span.end();
        }

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getHeaders()).containsKey("traceparent");
        assertThat(captured.get().getHeaders().get("traceparent")).startsWith("00-");
        assertThat(captured.get().getHeaders().get("traceparent"))
                .contains(span.getSpanContext().getTraceId());
        assertThat(captured.get().getState()).isEqualTo(Map.of());
    }

    @Test
    void preservesExistingHeaders() {
        final var openTelemetry = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        final var captured = new AtomicReference<ClientCallContext>();
        final var delegate = (ClientTransport) Proxy.newProxyInstance(
                ClientTransport.class.getClassLoader(), new Class<?>[]{ClientTransport.class},
                (proxy, method, args) -> {
                    if ("getTask".equals(method.getName())) {
                        captured.set((ClientCallContext) args[1]);
                    }
                    return null;
                });
        final var transport = new OpenTelemetryClientPropagatorTransport(delegate, openTelemetry);
        final var context = new ClientCallContext(Map.of("k", "v"), Map.of("X-Custom", "1"));

        transport.getTask(new TaskQueryParams("task-1"), context);

        assertThat(captured.get().getHeaders()).containsEntry("X-Custom", "1");
        assertThat(captured.get().getState()).containsEntry("k", "v");
        assertThat(Context.current()).isNotNull();
    }
}
