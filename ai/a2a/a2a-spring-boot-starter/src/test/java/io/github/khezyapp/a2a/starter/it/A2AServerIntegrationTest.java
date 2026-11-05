package io.github.khezyapp.a2a.starter.it;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.events.StreamCloseHandle;
import org.a2aproject.sdk.server.events.TaskStreamLifecycleHook;
import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.server.multitenancy.AgentExecutorRouter;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.khezyapp.a2a.core.server.A2AContextKeys;
import io.github.khezyapp.a2a.coredef.client.card.CardCacheHeader;
import io.github.khezyapp.a2a.coredef.client.card.SdkAgentCardSource;
import io.github.khezyapp.a2a.starter.server.MapAgentCardRouter;
import io.github.khezyapp.a2a.starter.server.MapAgentExecutorRouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = A2AServerIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Import(A2AServerIntegrationTest.TestConfig.class)
class A2AServerIntegrationTest {

    private static final String SCOPE_HEADER = "X-Agent-Scope";
    private static final String TENANT_HEADER = "X-Tenant";
    private static final int PORT = freePort();
    private static final String BASE_URL = "http://localhost:" + PORT + "/a2a";

    @Autowired
    private RecordingTaskStreamLifecycleHook lifecycleHook;

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("io.github.khezyapp.a2a.server.agent-card.name", () -> "integration-agent");
        registry.add("io.github.khezyapp.a2a.server.agent-card.url", () -> BASE_URL);
        registry.add("io.github.khezyapp.a2a.server.call-context.scope-header", () -> SCOPE_HEADER);
        registry.add("io.github.khezyapp.a2a.server.call-context.tenant-header", () -> TENANT_HEADER);
        registry.add("io.github.khezyapp.a2a.server.multitenancy.enabled", () -> "true");
        registry.add("io.github.khezyapp.a2a.client.enabled", () -> "false");
    }

    @Test
    @DisplayName("Fetches the agent card over HTTP")
    void shouldFetchCard() throws Exception {
        final var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/.well-known/agent-card.json")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("integration-agent").contains("supportedInterfaces");
        assertThat(response.headers().firstValue("ETag")).isPresent();
    }

    @Test
    @DisplayName("Blocking SendMessage completes and GetTask sees the task")
    void shouldSendMessageAndGetTask() throws Exception {
        final var client = client(false);
        final var events = send(client, "hello", Map.of(SCOPE_HEADER, "alpha"));
        final var task = completedTask(events);

        assertThat(task.status().state()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
        final var stored = client.getTask(new TaskQueryParams(task.id()), callContext(Map.of(SCOPE_HEADER, "alpha")));
        assertThat(stored.id()).isEqualTo(task.id());
        assertThat(artifactText(task)).contains("echo:hello");
    }

    @Test
    @DisplayName("SendStreamingMessage yields at least one event and drives the lifecycle hook")
    void shouldStreamMessages() throws Exception {
        final var client = client(true);
        final var events = send(client, "stream me", Map.of(SCOPE_HEADER, "alpha"));

        assertThat(events).isNotEmpty();
        assertThat(completedTask(events).status().state()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
        assertThat(lifecycleHook.subscriptions.get()).isPositive();
        awaitAtLeastOneUnsubscribe();
    }

    @Test
    @DisplayName("Tasks from another scope are hidden as TaskNotFound")
    void shouldDenyCrossScopeAccess() throws Exception {
        final var client = client(false);
        final var task = completedTask(send(client, "secret", Map.of(SCOPE_HEADER, "alpha")));

        assertThatThrownBy(() -> client.getTask(new TaskQueryParams(task.id()),
                callContext(Map.of(SCOPE_HEADER, "beta"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("application/a2a+json is accepted")
    void shouldAcceptA2aJson() throws Exception {
        final var client = client(false);
        final var task = completedTask(send(client, "media type", Map.of(SCOPE_HEADER, "alpha")));
        final var body = "{\"jsonrpc\":\"2.0\",\"id\":\"raw-1\",\"method\":\"GetTask\","
                + "\"params\":{\"id\":\"" + task.id() + "\"}}";
        final var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(BASE_URL))
                        .header("Content-Type", "application/a2a+json")
                        .header("A2A-Version", "1.0")
                        .header(SCOPE_HEADER, "alpha")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"result\"").contains(task.id());
    }

    @Test
    @DisplayName("Tenant routing selects the tenant executor and exposes the tenant")
    void shouldRouteTenant() throws Exception {
        final var client = client(false);
        final var events = send(client, "route", Map.of(SCOPE_HEADER, "alpha", TENANT_HEADER, "acme"));

        assertThat(artifactText(completedTask(events))).contains("tenant-agent:acme");
    }

    private static Client client(final boolean streaming) throws Exception {
        final var http = A2AHttpClientFactory.create();
        final var fetch = new SdkAgentCardSource(http).fetch(BASE_URL, CardCacheHeader.empty());
        assertThat(fetch.isOk()).isTrue();
        return Client.builder(fetch.card())
                .clientConfig(ClientConfig.builder().setStreaming(streaming).build())
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder().httpClient(http).build())
                .build();
    }

    private static List<ClientEvent> send(final Client client, final String text,
                                          final Map<String, String> headers) throws Exception {
        final var events = new CopyOnWriteArrayList<ClientEvent>();
        final var settled = new CountDownLatch(1);
        final var message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .parts(List.of(new TextPart(text)))
                .build();
        final var params = MessageSendParams.builder().message(message).build();
        client.sendMessage(params, List.of((event, card) -> {
            events.add(event);
            final var task = taskOf(event);
            if (task != null && task.status().state().isFinal()) {
                settled.countDown();
            }
        }), error -> settled.countDown(), callContext(headers));
        settled.await(10L, TimeUnit.SECONDS);
        return events;
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

    private static ClientCallContext callContext(final Map<String, String> headers) {
        return new ClientCallContext(Map.of(), headers);
    }

    private static Task completedTask(final List<ClientEvent> events) {
        Task task = null;
        for (final ClientEvent event : events) {
            final var candidate = taskOf(event);
            if (candidate != null) {
                task = candidate;
            }
        }
        if (task == null) {
            throw new AssertionError("No task event received: " + events);
        }
        return task;
    }

    private static String artifactText(final Task task) {
        final var text = new StringBuilder();
        task.artifacts().forEach(artifact -> artifact.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .forEach(part -> text.append(part.text())));
        return text.toString();
    }

    private void awaitAtLeastOneUnsubscribe() throws InterruptedException {
        final var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (lifecycleHook.unsubscriptions.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(50L);
        }
        assertThat(lifecycleHook.unsubscriptions.get()).isPositive();
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
        AgentExecutor echoAgentExecutor() {
            return new EchoAgentExecutor();
        }

        @Bean
        RecordingTaskStreamLifecycleHook recordingTaskStreamLifecycleHook() {
            return new RecordingTaskStreamLifecycleHook();
        }

        @Bean
        AgentExecutorRouter agentExecutorRouter(final AgentExecutor echoAgentExecutor) {
            return new MapAgentExecutorRouter(echoAgentExecutor, Map.of("acme", new TenantAgentExecutor()));
        }

        @Bean
        AgentCardRouter agentCardRouter(final AgentCard agentCard) {
            return new MapAgentCardRouter(agentCard, Map.of("acme", tenantCard()));
        }

        private static AgentCard tenantCard() {
            return AgentCard.builder()
                    .name("acme-agent")
                    .description("acme")
                    .version("1.0.0")
                    .capabilities(AgentCapabilities.builder().streaming(true).build())
                    .defaultInputModes(List.of("text"))
                    .defaultOutputModes(List.of("text"))
                    .skills(List.of())
                    .supportedInterfaces(List.of(new AgentInterface("JSONRPC", BASE_URL)))
                    .url(BASE_URL)
                    .build();
        }
    }

    /** Records stream lifecycle callbacks. */
    static final class RecordingTaskStreamLifecycleHook implements TaskStreamLifecycleHook {

        private final AtomicInteger subscriptions = new AtomicInteger();
        private final AtomicInteger unsubscriptions = new AtomicInteger();
        private final AtomicInteger events = new AtomicInteger();

        @Override
        public void onSubscribe(final String taskId, final StreamCloseHandle handle) {
            subscriptions.incrementAndGet();
        }

        @Override
        public void onUnsubscribe(final String taskId, final StreamCloseHandle handle) {
            unsubscriptions.incrementAndGet();
        }

        @Override
        public void onEvent(final String taskId, final Event event, final StreamCloseHandle handle) {
            events.incrementAndGet();
        }
    }

    /** Echo agent that records the agent scope in task metadata. */
    static final class EchoAgentExecutor implements AgentExecutor {

        @Override
        public void execute(final RequestContext context, final AgentEmitter emitter) throws A2AError {
            AgentSupport.seed(context, emitter);
            emitter.startWork();
            emitter.addArtifact(List.of(new TextPart("echo:" + context.getUserInput()
                    + ":tenant=" + Objects.toString(context.getTenant(), "none"))));
            emitter.complete();
        }
    }

    /** Tenant-specific agent used by the executor router. */
    static final class TenantAgentExecutor implements AgentExecutor {

        @Override
        public void execute(final RequestContext context, final AgentEmitter emitter) throws A2AError {
            AgentSupport.seed(context, emitter);
            emitter.startWork();
            emitter.addArtifact(List.of(new TextPart("tenant-agent:" + Objects.toString(context.getTenant(), "none"))));
            emitter.complete();
        }
    }

    /** Shared task setup for the test agents. */
    static final class AgentSupport {

        private AgentSupport() {
        }

        static void seed(final RequestContext context, final AgentEmitter emitter) {
            if (context.getTask() != null) {
                return;
            }
            final var scope = context.getCallContext() == null
                    ? null
                    : context.getCallContext().getState().get(A2AContextKeys.AGENT_SCOPE);
            final var builder = emitter.taskBuilder()
                    .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                    .history(List.of(context.getMessage()));
            if (scope instanceof String value && !value.isBlank()) {
                builder.metadata(Map.of(A2AContextKeys.AGENT_SCOPE, value));
            }
            emitter.addTask(builder.build());
        }
    }
}
