package io.github.khezyapp.a2a.observability.otel.server;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AMethods;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the Spring adapter delegates and produces one server span per request.
 */
class OpenTelemetryRequestHandlerTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();

    @AfterEach
    void closeProvider() {
        tracerProvider.close();
    }

    @Test
    void delegatesAndCreatesOneSpanPerRequest() {
        final var handler = new OpenTelemetryRequestHandler(delegate(task()), tracerProvider.get("test"));

        final var result = handler.onGetTask(new TaskQueryParams("task-1"), context());

        assertThat(result.id()).isEqualTo("task-1");
        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        final var span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getName()).isEqualTo(A2AMethods.GET_TASK_METHOD);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    void marksErrorsAndRethrows() {
        final var handler = new OpenTelemetryRequestHandler(failingDelegate(), tracerProvider.get("test"));

        assertThatThrownBy(() -> handler.onGetTask(new TaskQueryParams("task-1"), context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");
        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
    }

    private static RequestHandler delegate(final Task task) {
        return (RequestHandler) Proxy.newProxyInstance(RequestHandler.class.getClassLoader(),
                new Class<?>[]{RequestHandler.class},
                (proxy, method, args) -> "onGetTask".equals(method.getName()) ? task : defaultValue(method));
    }

    private static RequestHandler failingDelegate() {
        return (RequestHandler) Proxy.newProxyInstance(RequestHandler.class.getClassLoader(),
                new Class<?>[]{RequestHandler.class},
                (proxy, method, args) -> {
                    if ("onGetTask".equals(method.getName())) {
                        throw new IllegalStateException("boom");
                    }
                    return defaultValue(method);
                });
    }

    private static Object defaultValue(final java.lang.reflect.Method method) {
        if (method.getReturnType().isPrimitive()) {
            return false;
        }
        return null;
    }

    private static Task task() {
        return Task.builder()
                .id("task-1")
                .contextId("context-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .build();
    }

    private static ServerCallContext context() {
        return new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());
    }
}
