package io.github.khezyapp.a2a.coredef.client;

import io.github.khezyapp.a2a.core.client.A2ARemoteAgentClient;
import io.github.khezyapp.a2a.core.client.A2AStreamingRemoteAgentClient;
import io.github.khezyapp.a2a.core.client.CallContext;
import io.github.khezyapp.a2a.core.error.A2ACoreException;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;

/**
 * Default {@link A2ARemoteAgentClient} backed by the A2A Java SDK client over
 * JSON-RPC/HTTP. Wraps an SDK {@link Client}; discovery goes through
 * {@link A2A#getAgentCard(String)} and messaging through the client's task/event
 * stream. The streaming variant forwards every SDK {@link ClientEvent} onto the
 * {@link A2AStreamingRemoteAgentClient} listener as a spec {@link EventKind}. Per-call
 * {@link CallContext} headers/attributes map onto the SDK's {@code ClientCallContext}
 * so interceptors and transports can apply them per request.
 */
public final class SdkA2ARemoteAgentClient implements A2ARemoteAgentClient, A2AStreamingRemoteAgentClient {

    private final Client sdkClient;
    private final Duration defaultTimeout;

    /**
     * Daemon worker running SDK sends off the caller thread so {@link #sendMessage}'s
     * single deadline stays in control even when the transport blocks.
     */
    private final ExecutorService sendExecutor;

    public SdkA2ARemoteAgentClient(final Client sdkClient,
                                   final Duration defaultTimeout) {
        this.sdkClient = Objects.requireNonNull(sdkClient, "sdkClient");
        this.defaultTimeout = Objects.requireNonNull(defaultTimeout, "defaultTimeout");
        this.sendExecutor = Executors.newSingleThreadExecutor(r -> {
            final var thread = new Thread(r, "sdk-a2a-send");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Discovers the agent card, builds a streaming-capable SDK client bound to the
     * discovered {@code jsonrpc} interface and returns the wrapper.
     *
     * @param baseUrl the remote agent base URL (its {@code /.well-known/agent-card.json}
     *                is fetched for discovery)
     * @param defaultTimeout the default reply timeout for {@link #sendMessage}
     */
    public static SdkA2ARemoteAgentClient forJsonRpc(final URI baseUrl,
                                                     final Duration defaultTimeout) {
        return forJsonRpc(baseUrl, defaultTimeout, List.of());
    }

    /**
     * Same as {@link #forJsonRpc(URI, Duration)} but registers the given SDK call
     * interceptors (authentication, tracing, ...) on the JSON-RPC transport. They run
     * in list order on every request and receive each call's {@code ClientCallContext}.
     *
     * @param interceptors transport call interceptors; may be empty
     */
    public static SdkA2ARemoteAgentClient forJsonRpc(final URI baseUrl,
                                                     final Duration defaultTimeout,
                                                     final List<ClientCallInterceptor> interceptors) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(defaultTimeout, "defaultTimeout");
        Objects.requireNonNull(interceptors, "interceptors");
        final var card = discoverCard(baseUrl);
        final var clientConfig = ClientConfig.builder().setStreaming(true).build();
        final var transportConfig = new JSONRPCTransportConfigBuilder();
        interceptors.forEach(transportConfig::addInterceptor);
        final var client = Client.builder(card)
                .clientConfig(clientConfig)
                .withTransport(JSONRPCTransport.class, transportConfig)
                .build();
        return new SdkA2ARemoteAgentClient(client, defaultTimeout);
    }

    @Override
    public AgentCard discover(final URI baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        return discoverCard(baseUrl);
    }

    @Override
    public EventKind sendMessage(final Message message,
                                 final Duration timeout) {
        return sendMessage(message, timeout, null);
    }

    @Override
    public EventKind sendMessage(final Message message,
                                 final Duration timeout,
                                 final CallContext context) {
        Objects.requireNonNull(message, "message");
        final var effectiveTimeout = Objects.requireNonNullElse(timeout, defaultTimeout);
        final var terminal = new CompletableFuture<EventKind>();
        final Consumer<Throwable> errorHandler = error ->
                terminal.completeExceptionally(Objects.isNull(error) ?
                        new A2ACoreException("Agent stream ended before a final event")
                        : wrap(error)
                );

        CompletableFuture.runAsync(() ->
                sdkClient.sendMessage(message, List.of(terminalConsumer(terminal)), errorHandler,
                        toSdkContext(context)), sendExecutor);
        try {
            return terminal.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            throw new A2ACoreException("Agent did not respond within " + effectiveTimeout, e);
        } catch (final ExecutionException e) {
            final var cause = Objects.requireNonNullElse(e.getCause(), e);
            if (cause instanceof final A2ACoreException known) {
                throw known;
            }
            throw wrap(cause);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new A2ACoreException("Interrupted while sending message", e);
        }
    }

    @Override
    public void streamMessage(final Message message,
                              final Consumer<EventKind> listener) {
        streamMessage(message, listener, null);
    }

    @Override
    public void streamMessage(final Message message,
                              final Consumer<EventKind> listener,
                              final CallContext context) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(listener, "listener");
        final var consumer = forwardingConsumer(listener);
        sdkClient.sendMessage(message, List.of(consumer), null, toSdkContext(context));
    }

    @Override
    public void close() {
        sdkClient.close();
        sendExecutor.shutdownNow();
    }

    private static ClientCallContext toSdkContext(final CallContext context) {
        return Objects.isNull(context) ? null : new ClientCallContext(context.attributes(), context.headers());
    }

    private static AgentCard discoverCard(final URI baseUrl) {
        try {
            return A2A.getAgentCard(baseUrl.toString());
        } catch (final RuntimeException e) {
            throw new A2ACoreException("Agent card discovery failed for " + baseUrl, e);
        }
    }

    /**
     * Completes the future with the first terminal event (a reply message, a final task,
     * or a task update whose state is final); later events are ignored because
     * {@code complete} keeps the first result.
     */
    private static BiConsumer<ClientEvent, AgentCard> terminalConsumer(final CompletableFuture<EventKind> terminal) {
        return (event, agentCard) -> {
            if (event instanceof final MessageEvent messageEvent) {
                terminal.complete(messageEvent.getMessage());
            } else if (event instanceof final TaskEvent taskEvent) {
                terminal.complete(taskEvent.getTask());
            } else if (event instanceof final TaskUpdateEvent taskUpdateEvent
                    && isFinal(taskUpdateEvent)) {
                terminal.complete((EventKind) taskUpdateEvent.getUpdateEvent());
            }
        };
    }

    /**
     * Forwards every SDK event to the listener as a spec {@link EventKind}.
     */
    private static BiConsumer<ClientEvent, AgentCard> forwardingConsumer(final Consumer<EventKind> listener) {
        return (event, agentCard) -> {
            if (event instanceof final MessageEvent messageEvent) {
                listener.accept(messageEvent.getMessage());
            } else if (event instanceof final TaskEvent taskEvent) {
                listener.accept(taskEvent.getTask());
            } else if (event instanceof final TaskUpdateEvent taskUpdateEvent) {
                listener.accept((EventKind) taskUpdateEvent.getUpdateEvent());
            }
        };
    }

    private static boolean isFinal(final TaskUpdateEvent event) {
        final var task = event.getTask();
        return task.status().state().isFinal();
    }

    private static A2ACoreException wrap(final Throwable cause) {
        final var root = Objects.isNull(cause) ? new RuntimeException("unknown SDK error") : cause;
        return new A2ACoreException("SDK A2A client call failed: " + root.getMessage(), root);
    }
}
