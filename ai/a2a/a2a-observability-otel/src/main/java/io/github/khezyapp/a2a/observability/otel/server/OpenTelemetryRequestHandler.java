package io.github.khezyapp.a2a.observability.otel.server;

import java.util.Objects;

import io.opentelemetry.api.trace.Tracer;
import org.a2aproject.sdk.extras.opentelemetry.server.OpenTelemetryRequestHandlerDecorator;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;

/**
 * Spring adapter around the SDK OpenTelemetry request-handler decorator.
 *
 * <p>The SDK class is a CDI decorator and therefore abstract; this subclass simply
 * forwards the constructor arguments so the decorator can be instantiated as a Spring
 * bean. No behavior is added: method routing, span names, and attributes stay in the
 * SDK.</p>
 */
public class OpenTelemetryRequestHandler extends OpenTelemetryRequestHandlerDecorator {

    /**
     * Creates the decorator around a delegate.
     *
     * @param delegate the handler to trace
     * @param tracer the OpenTelemetry tracer
     */
    public OpenTelemetryRequestHandler(final RequestHandler delegate, final Tracer tracer) {
        super(Objects.requireNonNull(delegate, "delegate must not be null"),
                Objects.requireNonNull(tracer, "tracer must not be null"));
    }
}
