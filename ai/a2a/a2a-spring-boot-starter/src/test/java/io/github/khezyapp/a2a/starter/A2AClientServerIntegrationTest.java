package io.github.khezyapp.a2a.starter;

import io.github.khezyapp.a2a.core.executor.AbstractA2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.A2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import io.github.khezyapp.a2a.coredef.client.SdkA2ARemoteAgentClient;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test replicating the community project's {@code A2AClientServerIntegrationTests}
 * scenario: boots the starter on a random port with an echo executor, then drives it through
 * our own {@link SdkA2ARemoteAgentClient} (which wraps the SDK's JSON-RPC client) —
 * discover → sendMessage → getTask → streamMessage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration")
class A2AClientServerIntegrationTest {

    private static final String ECHO_AGENT_NAME = "Khezy Echo Agent";
    private static final AtomicInteger SERVER_PORT = new AtomicInteger();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    void capturePort(final int port) {
        SERVER_PORT.set(port);
    }

    private URI baseUrl() {
        return URI.create("http://localhost:" + SERVER_PORT.get());
    }

    @Test
    @DisplayName("discover returns the configured agent card")
    void shouldDiscoverAgentCard() {
        try (var client = SdkA2ARemoteAgentClient.forJsonRpc(baseUrl(), Duration.ofSeconds(10))) {
            final AgentCard card = client.discover(baseUrl());
            assertNotNull(card);
            assertEquals(ECHO_AGENT_NAME, card.name());
            assertTrue(card.capabilities().streaming());
        }
    }

    @Test
    @DisplayName("sendMessage returns a completed TaskStatusUpdateEvent echoing the input text")
    void shouldEchoViaSendMessage() {
        try (var client = SdkA2ARemoteAgentClient.forJsonRpc(baseUrl(), Duration.ofSeconds(10))) {
            final var result = client.sendMessage(
                    echoRequest("it-task-1", "it-ctx-1", "hello echo"), Duration.ofSeconds(10));
            assertInstanceOf(TaskStatusUpdateEvent.class, result);
            final var update = (TaskStatusUpdateEvent) result;
            assertEquals(TaskState.TASK_STATE_COMPLETED, update.status().state());
            assertNotNull(update.status().message());
            assertEquals("hello echo", firstText(update.status().message()));
        }
    }

    @Test
    @DisplayName("getTask through the JSON-RPC endpoint returns the completed task")
    void shouldReturnCompletedTask() throws Exception {
        try (var client = SdkA2ARemoteAgentClient.forJsonRpc(baseUrl(), Duration.ofSeconds(10))) {
            client.sendMessage(echoRequest("it-task-2", "it-ctx-2", "persist me"), Duration.ofSeconds(10));
        }
        final var body = """
                {"jsonrpc":"2.0","id":"it-get-1","method":"GetTask",
                 "params":{"id":"it-task-2"}}
                """;
        final var response = HTTP.send(HttpRequest.newBuilder()
                        .uri(baseUrl())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("it-task-2"));
        assertTrue(response.body().contains("TASK_STATE_COMPLETED"));
    }

    @Test
    @DisplayName("streamMessage forwards at least one event to the listener")
    void shouldStreamEvents() throws Exception {
        try (var client = SdkA2ARemoteAgentClient.forJsonRpc(baseUrl(), Duration.ofSeconds(10))) {
            final var received = new CopyOnWriteArrayList<EventKind>();
            final var latch = new CountDownLatch(1);
            client.streamMessage(echoRequest("it-task-3", "it-ctx-3", "stream me"), event -> {
                received.add(event);
                latch.countDown();
            });
            assertTrue(latch.await(10, TimeUnit.SECONDS), "expected at least one streaming event");
            assertFalse(received.isEmpty());
            final var first = received.get(0);
            assertInstanceOf(TaskStatusUpdateEvent.class, first);
            assertEquals(TaskState.TASK_STATE_COMPLETED, ((TaskStatusUpdateEvent) first).status().state());
        }
    }

    private static Message echoRequest(final String taskId,
                                       final String contextId,
                                       final String text) {
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(text)))
                .taskId(taskId)
                .contextId(contextId)
                .build();
    }

    private static String firstText(final Message message) {
        return message.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Supplies the application-defined beans the auto-configuration requires: an echo
     * executor (no LLM) and a lazily-built agent card whose interface URL points at the
     * random test port (the auto-config default card is property-driven and cannot know
     * the port at context startup).
     */
    @TestConfiguration
    static class EchoAgentConfiguration {

        @Bean
        A2AAgentExecutor echoExecutor() {
            return new AbstractA2AAgentExecutor() {
                @Override
                public EventKind execute(final AgentExecutionContext context) {
                    final var incoming = context.incoming();
                    final var echo = firstText(incoming);
                    final var reply = Message.builder()
                            .messageId(UUID.randomUUID().toString())
                            .role(Message.Role.ROLE_AGENT)
                            .parts(List.of(new TextPart(echo)))
                            .taskId(incoming.taskId())
                            .contextId(incoming.contextId())
                            .build();
                    return new TaskStatusUpdateEvent(
                            incoming.taskId(),
                            new TaskStatus(TaskState.TASK_STATE_COMPLETED, reply, OffsetDateTime.now()),
                            incoming.contextId(),
                            Map.of());
                }
            };
        }

        @Bean
        Supplier<AgentCard> agentCardSupplier() {
            return () -> {
                final var url = "http://localhost:" + SERVER_PORT.get();
                return AgentCard.builder()
                        .name(ECHO_AGENT_NAME)
                        .description("echo agent used by the client-server integration test")
                        .url(url)
                        .version("1.0.0")
                        .capabilities(AgentCapabilities.builder().streaming(true).build())
                        .defaultInputModes(List.of("text"))
                        .defaultOutputModes(List.of("text"))
                        .skills(List.of())
                        .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), url)))
                        .build();
            };
        }
    }
}
