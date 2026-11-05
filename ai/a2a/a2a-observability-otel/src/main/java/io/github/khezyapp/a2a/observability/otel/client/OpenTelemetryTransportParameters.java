package io.github.khezyapp.a2a.observability.otel.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.khezyapp.a2a.core.client.ClientBuildOptions;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.a2aproject.sdk.extras.opentelemetry.client.OpenTelemetryClientTransportWrapper;
import org.a2aproject.sdk.extras.opentelemetry.client.propagation.OpenTelemetryClientPropagatorTransportWrapper;
import org.jspecify.annotations.Nullable;

/**
 * Adds the SDK OpenTelemetry wrapper parameters to client build options.
 *
 * <p>The SDK activations are purely parameter driven: {@code OpenTelemetryClientTransportWrapper}
 * reads the tracer key and {@code OpenTelemetryClientPropagatorTransportWrapper} reads the
 * OpenTelemetry key. Both wrappers are discovered by the SDK through {@code ServiceLoader}
 * when the corresponding artifacts are on the classpath.</p>
 */
public final class OpenTelemetryTransportParameters {

    /** SDK key used by the client tracing wrapper. */
    public static final String OTEL_TRACER_KEY = OpenTelemetryClientTransportWrapper.OTEL_TRACER_KEY;

    /** SDK key used by the client context-propagation wrapper. */
    public static final String OTEL_OPEN_TELEMETRY_KEY =
            OpenTelemetryClientPropagatorTransportWrapper.OTEL_OPEN_TELEMETRY_KEY;

    private OpenTelemetryTransportParameters() {
    }

    /**
     * Returns a copy of the options with OpenTelemetry parameters added.
     *
     * @param options the current options
     * @param tracer the tracer to pass to the SDK wrapper
     * @param openTelemetry the OpenTelemetry instance used for propagation, or {@code null}
     * @return the options with the OpenTelemetry parameters merged in
     */
    public static ClientBuildOptions apply(final ClientBuildOptions options, final Tracer tracer,
                                           final @Nullable OpenTelemetry openTelemetry) {
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(tracer, "tracer must not be null");
        final var parameters = new LinkedHashMap<String, Object>(options.transportParameters());
        parameters.put(OTEL_TRACER_KEY, tracer);
        if (Objects.nonNull(openTelemetry)) {
            parameters.put(OTEL_OPEN_TELEMETRY_KEY, openTelemetry);
        }
        return ClientBuildOptions.builder()
                .httpClient(options.httpClient())
                .acceptedOutputModes(options.acceptedOutputModes())
                .streaming(options.streaming())
                .polling(options.polling())
                .useClientPreference(options.useClientPreference())
                .interceptors(options.interceptors())
                .credentialService(options.credentialService())
                .transportParameters(Map.copyOf(parameters))
                .build();
    }
}
