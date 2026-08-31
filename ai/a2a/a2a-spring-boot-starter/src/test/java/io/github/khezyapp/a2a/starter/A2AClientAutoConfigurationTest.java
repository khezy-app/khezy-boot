package io.github.khezyapp.a2a.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.khezyapp.a2a.core.client.A2ARemoteAgentClient;
import io.github.khezyapp.a2a.core.client.A2AStreamingRemoteAgentClient;
import io.github.khezyapp.a2a.core.client.CallContext;
import io.github.khezyapp.a2a.coredef.client.SdkA2ARemoteAgentClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoJsonUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Boots {@link A2AClientAutoConfiguration} against a minimal in-JVM upstream agent
 * (plain JDK HTTP server speaking the SDK's protobuf-JSON wire format) and verifies the
 * property-driven wiring end to end: client bean creation, bearer-token interception,
 * per-call {@link CallContext} overrides and user-supplied interceptor participation.
 */
class A2AClientAutoConfigurationTest {

    private static final String SERVICE_TOKEN = "service-token";
    private static final String SERVICE_AUTHORIZATION = "Bearer service-token";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicReference<String> LAST_AUTHORIZATION = new AtomicReference<>();

    private static HttpServer upstream;

    @BeforeAll
    static void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        upstream.createContext("/", A2AClientAutoConfigurationTest::handle);
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(A2AClientAutoConfiguration.class));

    private String baseUrl() {
        return "http://localhost:" + upstream.getAddress().getPort();
    }

    private ApplicationContextRunner configured(final String... extraProperties) {
        final var values = new ArrayList<String>();
        values.add("io.github.khezyapp.a2a.client.base-url=" + baseUrl());
        values.add("io.github.khezyapp.a2a.client.timeout=10s");
        values.addAll(List.of(extraProperties));
        return runner.withPropertyValues(values.toArray(String[]::new));
    }

    @Test
    @DisplayName("client bean activates with base-url and attaches the configured bearer token")
    void shouldCreateClientWithBearerAuth() {
        configured("io.github.khezyapp.a2a.client.auth.type=bearer",
                "io.github.khezyapp.a2a.client.auth.token=" + SERVICE_TOKEN).run(context -> {
            assertTrue(context.containsBean("a2aRemoteAgentClient"));
            final var client = context.getBean(A2ARemoteAgentClient.class);
            assertInstanceOf(SdkA2ARemoteAgentClient.class, client);
            final var result = client.sendMessage(echoRequest("wire-task-1", "wire-ctx-1"), Duration.ofSeconds(10));
            assertTerminalEcho(result);
            assertEquals(SERVICE_AUTHORIZATION, LAST_AUTHORIZATION.get());
        });
    }

    @Test
    @DisplayName("per-call CallContext authorization overrides the service token")
    void shouldLetPerCallContextOverrideServiceToken() {
        configured("io.github.khezyapp.a2a.client.auth.type=bearer",
                "io.github.khezyapp.a2a.client.auth.token=" + SERVICE_TOKEN).run(context -> {
            final var result = context.getBean(A2ARemoteAgentClient.class).sendMessage(
                    echoRequest("wire-task-2", "wire-ctx-2"),
                    Duration.ofSeconds(10),
                    CallContext.ofHeaders(Map.of("Authorization", "Bearer caller-token")));
            assertTerminalEcho(result);
            assertEquals("Bearer caller-token", LAST_AUTHORIZATION.get());
        });
    }

    @Test
    @DisplayName("auth type none sends without an Authorization header")
    void shouldSendWithoutAuthorizationWhenAuthTypeNone() {
        configured().run(context -> {
            final var result = context.getBean(A2ARemoteAgentClient.class)
                    .sendMessage(echoRequest("wire-task-3", "wire-ctx-3"), Duration.ofSeconds(10));
            assertTerminalEcho(result);
            assertNull(LAST_AUTHORIZATION.get());
        });
    }

    @Test
    @DisplayName("streaming send honors a per-call context header")
    void shouldStreamWithContext() {
        configured("io.github.khezyapp.a2a.client.auth.type=bearer",
                "io.github.khezyapp.a2a.client.auth.token=" + SERVICE_TOKEN).run(context -> {
                    final var streaming = (A2AStreamingRemoteAgentClient)
                            context.getBean(A2ARemoteAgentClient.class);
                    final var received = new CopyOnWriteArrayList<EventKind>();
                    final var firstEvent = new CountDownLatch(1);
                    streaming.streamMessage(
                            echoRequest("wire-task-4", "wire-ctx-4"),
                            event -> {
                                received.add(event);
                                firstEvent.countDown();
                            },
                            CallContext.ofHeaders(Map.of("Authorization", "Bearer stream-caller")));
                    assertTrue(firstEvent.await(10, TimeUnit.SECONDS), "expected at least one event");
                    assertTerminalEcho(received.get(0));
                    assertEquals("Bearer stream-caller", LAST_AUTHORIZATION.get());
                });
    }

    @Test
    @DisplayName("user-defined interceptors participate in the call chain")
    void shouldCollectUserInterceptorBeans() {
        configured("io.github.khezyapp.a2a.client.auth.type=bearer",
                "io.github.khezyapp.a2a.client.auth.token=" + SERVICE_TOKEN)
                .withUserConfiguration(RecordingInterceptorConfiguration.class).run(context -> {
                    final var recording = context.getBean(RecordingInterceptor.class);
                    final var result = context.getBean(A2ARemoteAgentClient.class)
                            .sendMessage(echoRequest("wire-task-5", "wire-ctx-5"), Duration.ofSeconds(10));
                    assertTerminalEcho(result);
                    assertEquals(List.of(SERVICE_AUTHORIZATION), recording.seenAuthorization());
                });
    }

    @Test
    @DisplayName("no base-url means no client bean")
    void shouldNotCreateClientWithoutBaseUrl() {
        runner.run(context -> assertFalse(context.containsBean("a2aRemoteAgentClient")));
    }

    @Test
    @DisplayName("bearer auth without a token fails context startup")
    void shouldFailFastWhenBearerTokenMissing() {
        configured("io.github.khezyapp.a2a.client.auth.type=bearer").run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(context.getStartupFailure().getMessage()
                    .contains("io.github.khezyapp.a2a.client.auth.token"));
        });
    }

    private static void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            if ("GET".equals(exchange.getRequestMethod())) {
                serveAgentCard(exchange);
            } else {
                serveStreamingEcho(exchange);
            }
        }
    }

    /**
     * Serves the agent card serialized exactly the way the SDK parses it: proto-JSON of
     * the spec card, mirroring what {@code JSONRPCUtils} produces internally.
     */
    private static void serveAgentCard(final HttpExchange exchange) throws IOException {
        final var url = "http://localhost:" + exchange.getLocalAddress().getPort();
        final var card = AgentCard.builder()
                .name("fake-upstream")
                .description("in-test upstream agent")
                .url(url)
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), url)))
                .build();
        final var body = ProtoJsonUtils.toJson(
                JsonFormat.printer().alwaysPrintFieldsWithNoPresence().omittingInsignificantWhitespace(),
                ProtoUtils.ToProto.agentCard(card)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /**
     * Records the Authorization header and answers every JSON-RPC POST with one SSE
     * frame carrying a final {@code TaskStatusUpdateEvent}, same format as the starter's
     * own {@code SdkSseEventSink}.
     */
    private static void serveStreamingEcho(final HttpExchange exchange) throws IOException {
        final var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        LAST_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
        final var envelope = JSON.readTree(body);
        final var idNode = envelope.get("id");
        final Object requestId = idNode == null || idNode.isNull() ? null
                : idNode.isNumber() ? idNode.longValue() : idNode.asString();
        final var message = envelope.get("params").get("message");
        final var taskId = textOr(message, "taskId", UUID.randomUUID().toString());
        final var contextId = textOr(message, "contextId", "test-context");
        final var reply = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(firstText(message))))
                .taskId(taskId)
                .contextId(contextId)
                .build();
        final var update = new TaskStatusUpdateEvent(
                taskId,
                new TaskStatus(TaskState.TASK_STATE_COMPLETED, reply, OffsetDateTime.now()),
                contextId,
                Map.of());
        final var frame = JSONRPCUtils.toJsonRPCResultResponse(
                requestId, ProtoUtils.ToProto.taskOrMessageStream(update));
        final var bytes = ("data: " + frame + "\n\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String textOr(final JsonNode node, final String field, final String fallback) {
        final var value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asString();
    }

    private static String firstText(final JsonNode message) {
        for (final var part : message.get("parts")) {
            if (part.has("text")) {
                return part.get("text").asString();
            }
        }
        throw new IllegalStateException("no text part in request message");
    }

    private static Message echoRequest(final String taskId, final String contextId) {
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(ECHO_TEXT)))
                .taskId(taskId)
                .contextId(contextId)
                .build();
    }

    private static final String ECHO_TEXT = "hello wire";

    private static void assertTerminalEcho(final EventKind event) {
        final var update = assertInstanceOf(TaskStatusUpdateEvent.class, event);
        assertEquals(TaskState.TASK_STATE_COMPLETED, update.status().state());
        assertEquals(ECHO_TEXT, firstTextOf(update.status().message()));
    }

    private static String firstTextOf(final Message message) {
        return message.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .findFirst()
                .orElseThrow();
    }

    /** Captures the Authorization header visible after earlier interceptors ran. */
    static class RecordingInterceptor extends ClientCallInterceptor {

        private final CopyOnWriteArrayList<String> seen = new CopyOnWriteArrayList<>();

        @Override
        public PayloadAndHeaders intercept(final String methodName,
                                           final Object payload,
                                           final Map<String, String> headers,
                                           final AgentCard agentCard,
                                           final ClientCallContext clientCallContext) {
            seen.add(Objects.requireNonNullElse(headers, Map.<String, String>of()).get("Authorization"));
            return new PayloadAndHeaders(payload, headers);
        }

        List<String> seenAuthorization() {
            return new ArrayList<>(seen);
        }
    }

    /** Registers a user-defined interceptor bean next to the starter-provided bearer one. */
    @Configuration(proxyBeanMethods = false)
    static class RecordingInterceptorConfiguration {

        @Bean
        RecordingInterceptor recordingInterceptor() {
            return new RecordingInterceptor();
        }
    }
}
