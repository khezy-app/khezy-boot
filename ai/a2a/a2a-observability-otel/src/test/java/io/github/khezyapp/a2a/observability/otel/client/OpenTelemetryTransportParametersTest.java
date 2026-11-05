package io.github.khezyapp.a2a.observability.otel.client;

import java.util.Map;

import io.github.khezyapp.a2a.core.client.ClientBuildOptions;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the SDK wrapper keys carry the tracer and OpenTelemetry instances.
 */
class OpenTelemetryTransportParametersTest {

    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
    private final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();

    @Test
    void addsBothSdkWrapperParameters() {
        final var options = ClientBuildOptions.of(A2AHttpClientFactory.create());
        final var tracer = tracerProvider.get("test");

        final var traced = OpenTelemetryTransportParameters.apply(options, tracer, openTelemetry);

        assertThat(traced.transportParameters())
                .containsEntry(OpenTelemetryTransportParameters.OTEL_TRACER_KEY, tracer)
                .containsKey(OpenTelemetryTransportParameters.OTEL_OPEN_TELEMETRY_KEY);
        assertThat(traced.transportParameters()
                .get(OpenTelemetryTransportParameters.OTEL_OPEN_TELEMETRY_KEY))
                .isSameAs(openTelemetry);
    }

    @Test
    void keepsExistingParametersAndCopiesOtherOptions() {
        final var options = ClientBuildOptions.builder()
                .httpClient(A2AHttpClientFactory.create())
                .streaming(false)
                .polling(true)
                .transportParameters(Map.of("existing", "value"))
                .build();

        final var traced = OpenTelemetryTransportParameters.apply(options,
                tracerProvider.get("test"), null);

        assertThat(traced.transportParameters())
                .containsEntry("existing", "value")
                .containsKey(OpenTelemetryTransportParameters.OTEL_TRACER_KEY)
                .doesNotContainKey(OpenTelemetryTransportParameters.OTEL_OPEN_TELEMETRY_KEY);
        assertThat(traced.streaming()).isFalse();
        assertThat(traced.polling()).isTrue();
        assertThat(options.transportParameters()).doesNotContainKey(
                OpenTelemetryTransportParameters.OTEL_TRACER_KEY);
    }

    @Test
    void postProcessorOnlyTouchesClientBuildOptions() {
        final var postProcessor = new OpenTelemetryClientBuildOptionsPostProcessor(
                tracerProvider.get("test"), openTelemetry);
        final var options = ClientBuildOptions.of(A2AHttpClientFactory.create());

        final var processed = postProcessor.postProcessAfterInitialization(options, "clientBuildOptions");

        assertThat(processed).isInstanceOf(ClientBuildOptions.class);
        assertThat(((ClientBuildOptions) processed).transportParameters())
                .containsKey(OpenTelemetryTransportParameters.OTEL_TRACER_KEY);
        assertThat(postProcessor.postProcessAfterInitialization("plain", "plain")).isEqualTo("plain");
    }
}
