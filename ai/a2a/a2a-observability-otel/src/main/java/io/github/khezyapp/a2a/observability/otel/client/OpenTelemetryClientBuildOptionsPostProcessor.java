package io.github.khezyapp.a2a.observability.otel.client;

import java.util.Objects;

import io.github.khezyapp.a2a.core.client.ClientBuildOptions;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Replaces the {@link ClientBuildOptions} bean with one that carries the OpenTelemetry
 * wrapper parameters.
 *
 * <p>A post-processor is used instead of redefining the options bean so the starter keeps
 * ownership of property binding and interceptors, and this module needs no compile
 * dependency on the starter.</p>
 */
public class OpenTelemetryClientBuildOptionsPostProcessor implements BeanPostProcessor {

    private final Tracer tracer;
    private final @Nullable OpenTelemetry openTelemetry;

    /**
     * Creates the post-processor.
     *
     * @param tracer the tracer passed to the SDK client wrapper
     * @param openTelemetry the instance used for trace-context propagation, or {@code null}
     */
    public OpenTelemetryClientBuildOptionsPostProcessor(final Tracer tracer,
                                                        final @Nullable OpenTelemetry openTelemetry) {
        this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
        this.openTelemetry = openTelemetry;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        if (bean instanceof ClientBuildOptions options) {
            return OpenTelemetryTransportParameters.apply(options, tracer, openTelemetry);
        }
        return bean;
    }
}
