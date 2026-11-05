package io.github.khezyapp.a2a.observability.otel;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import io.github.khezyapp.a2a.observability.otel.client.OpenTelemetryClientBuildOptionsPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the auto-configuration contract without booting a server: registration,
 * classpath guards, conditional beans and the no-tracer path.
 */
class A2AObservabilityAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                    .of(A2AObservabilityAutoConfiguration.class));

    @Test
    void isAnAutoConfigurationGuardedByTracerClass() {
        final var annotation = A2AObservabilityAutoConfiguration.class.getAnnotation(AutoConfiguration.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.afterName())
                .contains("io.github.khezyapp.a2a.starter.server.A2AServerAutoConfiguration");
        final var onClass = A2AObservabilityAutoConfiguration.class.getAnnotation(ConditionalOnClass.class);
        assertThat(onClass.name()).contains("io.opentelemetry.api.trace.Tracer");
    }

    @Test
    void doesNotCreateHandlersWithoutATracer() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RequestHandler.class);
            assertThat(context).doesNotHaveBean(OpenTelemetryClientBuildOptionsPostProcessor.class);
        });
    }

    @Test
    void beanMethodsAreConditionalOnMissingBean() {
        assertConditionalBeanMethods(A2AObservabilityAutoConfiguration.class);
    }

    @Test
    void isRegisteredInAutoConfigurationImports() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(IMPORTS_RESOURCE)) {
            assertThat(stream).isNotNull();
            final var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains(A2AObservabilityAutoConfiguration.class.getName());
        }
    }

    @Test
    void propertiesDefaultToEnabledWithoutPayloadCapture() {
        final var properties = new A2AObservabilityProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isCaptureRequest()).isFalse();
        assertThat(properties.isCaptureResponse()).isFalse();
    }

    private static void assertConditionalBeanMethods(final Class<?> type) {
        for (final Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)) {
                assertThat(method.getAnnotation(ConditionalOnMissingBean.class))
                        .as("bean method %s.%s", type.getSimpleName(), method.getName())
                        .isNotNull();
            }
        }
        for (final Class<?> nested : type.getDeclaredClasses()) {
            assertConditionalBeanMethods(nested);
        }
    }
}
