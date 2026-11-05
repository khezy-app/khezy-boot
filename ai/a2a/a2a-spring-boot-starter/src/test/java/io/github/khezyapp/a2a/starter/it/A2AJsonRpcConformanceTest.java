package io.github.khezyapp.a2a.starter.it;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.khezyapp.a2a.coredef.client.card.CardCacheHeader;
import io.github.khezyapp.a2a.coredef.client.card.SdkAgentCardSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Interoperability conformance test: drives the official SDK client against the Spring
 * server for every JSON-RPC method exposed by the JSON-RPC transport.
 *
 * <p>This is the in-repo substitute for vendoring the upstream TCK: skip is
 * unsupported because this server only exposes JSON-RPC over HTTP. Streaming methods are
 * exercised with a settled event; subscribe is exercised on a non-final task and closed
 * by cancelling it.</p>
 */
@SpringBootTest(classes = A2AJsonRpcConformanceTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Import(A2AJsonRpcConformanceTest.TestConfig.class)
class A2AJsonRpcConformanceTest {

    private static final int PORT = freePort();
    private static final String BASE_URL = "http://localhost:" + PORT + "/a2a";

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("io.github.khezyapp.a2a.server.agent-card.name", () -> "conformance-agent");
        registry.add("io.github.khezyapp.a2a.server.agent-card.url", () -> BASE_URL);
        registry.add("io.github.khezyapp.a2a.server.agent-card.capabilities.push-notifications",
                () -> "true");
        registry.add("io.github.khezyapp.a2a.server.agent-card.capabilities.extended-agent-card",
                () -> "true");
        registry.add("io.github.khezyapp.a2a.server.push-notifications.enabled", () -> "true");
        registry.add("io.github.khezyapp.a2a.server.authorization.required", () -> "false");
        registry.add("io.github.khezyapp.a2a.client.enabled", () -> "false");
    }

    @Test
    @DisplayName("SendMessage")
    void sendMessage() throws Exception {
        try (var http = new HttpHandle()) {
            final var client = client(http.client(), false);
            final var task = send(client, "hello");

            assertThat(task.status().state()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
            assertThat(artifactText(task)).contains("echo:hello");
        }
    }

    @Test
    @DisplayName("SendStreamingMessage")
    void sendStreamingMessage() throws Exception {
        try (var http = new HttpHandle()) {
            final var client = client(http.client(), true);
            final var events = sendEvents(client, "stream");
            final var task = completedTask(events);

            assertThat(events).isNotEmpty();
            assertThat(task.status().state()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
        }
    }

    @Test
    @DisplayName("GetTask and ListTasks")
    void getTaskAndListTasks() throws Exception {
        try (var http = new HttpHandle()) {
            final var client = client(http.client(), false);
            final var task = send(client, "list me");

            assertThat(client.getTask(new TaskQueryParams(task.id()), context()).id())
                    .isEqualTo(task.id());
            final var listed = client.listTasks(ListTasksParams.builder()
                    .contextId(task.contextId())
                    .tenant("")
                    .build(), context());
            assertThat(listed.tasks()).extracting(Task::id).contains(task.id());
        }
    }

    @Test
    @DisplayName("CancelTask")
    void cancelTask() throws Exception {
        try (var http = new HttpHandle()) {
            final var client = client(http.client(), true);
            final var task = waitingTask(client, "wait-cancel");

            final var cancelled = client.cancelTask(new CancelTaskParams(task.id()), context());

            assertThat(cancelled.status().state()).isEqualTo(TaskState.TASK_STATE_CANCELED);
        }
    }

    @Test
    @DisplayName("SubscribeToTask")
    void subscribeToTask() throws Exception {
        try (var http = new HttpHandle()) {
            final var streamingClient = client(http.client(), true);
            final var task = waitingTask(streamingClient, "wait-subscribe");
            final var events = new CopyOnWriteArrayList<ClientEvent>();
            final var firstEvent = new CountDownLatch(1);

            streamingClient.subscribeToTask(new TaskIdParams(task.id()), List.of((event, card) -> {
                events.add(event);
                firstEvent.countDown();
            }), error -> firstEvent.countDown(), context());

            assertThat(firstEvent.await(10L, TimeUnit.SECONDS)).isTrue();
            assertThat(events).isNotEmpty();
            assertThat(taskOf(events.get(0)).id()).isEqualTo(task.id());

            final var cancelClient = client(http.client(), false);
            cancelClient.cancelTask(new CancelTaskParams(task.id()), context());
            streamingClient.close();
        }
    }

    @Test
    @DisplayName("Push notification config CRUD")
    void pushNotificationConfigCrud() throws Exception {
        try (var http = new HttpHandle()) {
            final var client = client(http.client(), false);
            final var task = send(client, "push");
            final var config = TaskPushNotificationConfig.builder()
                    .taskId(task.id())
                    .url("https://example.com/a2a-hook")
                    .build();

            final var created = client.createTaskPushNotificationConfiguration(config, context());
            assertThat(created.id()).isNotBlank();
            assertThat(client.getTaskPushNotificationConfiguration(
                    new GetTaskPushNotificationConfigParams(task.id(), created.id()), context()).id())
                    .isEqualTo(created.id());
            assertThat(client.listTaskPushNotificationConfigurations(
                    new ListTaskPushNotificationConfigsParams(task.id()), context()).configs())
                    .extracting(TaskPushNotificationConfig::id)
                    .contains(created.id());

            client.deleteTaskPushNotificationConfigurations(
                    new DeleteTaskPushNotificationConfigParams(task.id(), created.id()), context());

            assertThatThrownBy(() -> client.getTaskPushNotificationConfiguration(
                    new GetTaskPushNotificationConfigParams(task.id(), created.id()), context()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    @DisplayName("GetExtendedAgentCard is routed by the SDK handler")
    void getExtendedAgentCardIsRouted() throws Exception {
        try (var http = new HttpHandle()) {
            final var body = "{\"jsonrpc\":\"2.0\",\"id\":\"ext-1\","
                    + "\"method\":\"GetExtendedAgentCard\",\"params\":{}}";
            final var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(BASE_URL))
                            .header("Content-Type", "application/json")
                            .header("A2A-Version", "1.0")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"error\"");
            assertThat(response.body()).doesNotContain("Unsupported JSON-RPC method");
        }
    }

    @Test
    @DisplayName("Agent card is served over HTTP")
    void agentCardIsServed() {
        try (var http = new HttpHandle()) {
            final var fetch = new SdkAgentCardSource(http.client()).fetch(BASE_URL, CardCacheHeader.empty());

            assertThat(fetch.isOk()).isTrue();
            assertThat(fetch.card().name()).isEqualTo("conformance-agent");
            assertThat(fetch.card().capabilities().extendedAgentCard()).isTrue();
        }
    }

    private static Client client(final A2AHttpClient http, final boolean streaming) throws Exception {
        final var fetch = new SdkAgentCardSource(http).fetch(BASE_URL, CardCacheHeader.empty());
        assertThat(fetch.isOk()).isTrue();
        return Client.builder(fetch.card())
                .clientConfig(ClientConfig.builder().setStreaming(streaming).build())
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder().httpClient(http).build())
                .build();
    }

    private static Task send(final Client client, final String text) throws Exception {
        return completedTask(sendEvents(client, text));
    }

    private static List<ClientEvent> sendEvents(final Client client, final String text) throws Exception {
        final var events = new CopyOnWriteArrayList<ClientEvent>();
        final var settled = new CountDownLatch(1);
        final var message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .parts(List.of(new TextPart(text)))
                .build();
        client.sendMessage(MessageSendParams.builder().message(message).build(),
                List.of((event, card) -> {
                    events.add(event);
                    final var task = taskOf(event);
                    if (task != null && (task.status().state().isFinal()
                            || task.status().state() == TaskState.TASK_STATE_INPUT_REQUIRED)) {
                        settled.countDown();
                    }
                }), error -> settled.countDown(), context());
        assertThat(settled.await(10L, TimeUnit.SECONDS)).isTrue();
        return events;
    }

    private static Task waitingTask(final Client client, final String text) throws Exception {
        final var task = completedTask(sendEvents(client, text));
        assertThat(task.status().state()).isEqualTo(TaskState.TASK_STATE_INPUT_REQUIRED);
        return task;
    }

    private static Task taskOf(final ClientEvent event) {
        if (event instanceof org.a2aproject.sdk.client.TaskEvent taskEvent) {
            return taskEvent.getTask();
        }
        if (event instanceof org.a2aproject.sdk.client.TaskUpdateEvent updateEvent) {
            return updateEvent.getTask();
        }
        return null;
    }

    private static ClientCallContext context() {
        return new ClientCallContext(Map.of(), Map.of());
    }

    private static Task completedTask(final List<ClientEvent> events) {
        final var last = new AtomicReference<Task>();
        for (final ClientEvent event : events) {
            final var candidate = taskOf(event);
            if (candidate != null) {
                last.set(candidate);
            }
        }
        if (last.get() == null) {
            throw new AssertionError("No task event received: " + events);
        }
        return last.get();
    }

    private static String artifactText(final Task task) {
        final var text = new StringBuilder();
        task.artifacts().forEach(artifact -> artifact.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .forEach(part -> text.append(part.text())));
        return text.toString();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (final IOException e) {
            throw new IllegalStateException("Could not allocate a port", e);
        }
    }

    /** Test application. */
    @SpringBootApplication
    static class TestApplication {
    }

    /** Test beans. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        AgentExecutor conformanceAgentExecutor() {
            return new ConformanceAgentExecutor();
        }
    }

    /** Wraps the shared HTTP client so it is closed after each test. */
    static final class HttpHandle implements AutoCloseable {

        private final A2AHttpClient client = A2AHttpClientFactory.create();

        A2AHttpClient client() {
            return client;
        }

        @Override
        public void close() {
            if (client instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (final Exception e) {
                    throw new IllegalStateException("Could not close the HTTP client", e);
                }
            }
        }
    }

    /** Echo agent that can wait for input and supports cancellation. */
    static final class ConformanceAgentExecutor implements AgentExecutor {

        @Override
        public void execute(final RequestContext context, final AgentEmitter emitter) throws A2AError {
            if (context.getTask() == null) {
                emitter.addTask(emitter.taskBuilder()
                        .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                        .history(List.of(context.getMessage()))
                        .build());
            }
            final var input = context.getUserInput();
            if (input != null && input.startsWith("wait")) {
                emitter.startWork();
                emitter.requiresInput();
                return;
            }
            emitter.startWork();
            emitter.addArtifact(List.of(new TextPart("echo:" + input)));
            emitter.complete();
        }

        @Override
        public void cancel(final RequestContext context, final AgentEmitter emitter) throws A2AError {
            emitter.cancel();
        }
    }
}
