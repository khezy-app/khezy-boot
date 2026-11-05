package io.github.khezyapp.a2a.coredef.client.invoke;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;

import io.github.khezyapp.a2a.core.client.A2AInvoker;
import io.github.khezyapp.a2a.core.client.RemoteAgent;
import io.github.khezyapp.a2a.core.client.RemoteSkill;
import io.github.khezyapp.a2a.core.client.SendOutcome;
import io.github.khezyapp.a2a.core.client.invoke.SendRequest;

/**
 * Blocking {@link A2AInvoker} backed by the SDK client.
 *
 * <p>The invoker waits for the first settled event: a final task, an interrupted
 * task when {@code stopOnInterrupt} is set, or the first event when the request
 * asks the server to return immediately. Transport failures and timeouts become
 * {@link SendOutcome#error} instead of exceptions. The shared client is never
 * closed.</p>
 */
public class SdkA2AInvoker implements A2AInvoker {

    /**
     * Message metadata key carrying the requested skill id.
     */
    public static final String SKILL_ID_METADATA_KEY = "skillId";

    @Override
    public SendOutcome send(final SendRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        final RemoteAgent agent = request.agent();
        final RemoteSkill skill = resolveSkill(agent, request.skillId());
        try {
            final Message message = buildMessage(request);
            final MessageSendParams params = buildParams(request, message, agent);
            final ClientCallContext callContext = new ClientCallContext(
                    request.options().state(), request.options().headers());
            return dispatch(agent, skill, request, params, callContext);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendOutcome.error(agent, skill, "interrupted");
        } catch (RuntimeException e) {
            return SendOutcome.error(agent, skill, errorMessage(e));
        }
    }

    private SendOutcome dispatch(final RemoteAgent agent, final RemoteSkill skill,
                                 final SendRequest request, final MessageSendParams params,
                                 final ClientCallContext callContext) throws InterruptedException {
        final boolean returnImmediately = request.configuration() != null
                && Boolean.TRUE.equals(request.configuration().returnImmediately());
        final boolean stopOnInterrupt = request.options().stopOnInterrupt();
        final var future = new CompletableFuture<SendOutcome>();
        final var lastOutcome = new AtomicReference<SendOutcome>();
        final List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of((event, card) -> {
            final var outcome = toOutcome(agent, skill, event);
            if (outcome == null) {
                return;
            }
            lastOutcome.set(outcome);
            if (returnImmediately || outcome.isFinal() || (outcome.isInterrupted() && stopOnInterrupt)) {
                future.complete(outcome);
            }
        });
        final Consumer<Throwable> errorHandler = throwable ->
                future.complete(SendOutcome.error(agent, skill, errorMessage(throwable)));

        try {
            agent.client().sendMessage(params, consumers, errorHandler, callContext);
        } catch (RuntimeException e) {
            return SendOutcome.error(agent, skill, errorMessage(e));
        }

        if (future.isDone()) {
            return future.join();
        }
        if (!supportsStreaming(agent)) {
            final var blockingOutcome = lastOutcome.get();
            return blockingOutcome == null
                    ? SendOutcome.error(agent, skill, "agent returned no response")
                    : blockingOutcome;
        }

        try {
            return future.get(request.options().timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(false);
            return SendOutcome.error(agent, skill,
                    "timed out after " + request.options().timeout().toMillis() + " ms");
        } catch (InterruptedException e) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            return SendOutcome.error(agent, skill, "interrupted");
        } catch (ExecutionException e) {
            return SendOutcome.error(agent, skill, errorMessage(e.getCause()));
        }
    }

    private static SendOutcome toOutcome(final RemoteAgent agent, final RemoteSkill skill,
                                         final ClientEvent event) {
        if (event instanceof MessageEvent messageEvent) {
            return SendOutcome.ofMessage(agent, skill, messageEvent.getMessage());
        }
        if (event instanceof TaskEvent taskEvent) {
            return SendOutcome.ofTask(agent, skill, taskEvent.getTask());
        }
        if (event instanceof TaskUpdateEvent updateEvent) {
            return SendOutcome.ofTask(agent, skill, updateEvent.getTask());
        }
        return null;
    }

    private static Message buildMessage(final SendRequest request) {
        final var metadata = new LinkedHashMap<String, Object>();
        metadata.putAll(request.messageMetadata());
        if (request.skillId() != null && !request.skillId().isBlank()) {
            metadata.put(SKILL_ID_METADATA_KEY, request.skillId());
        }
        return Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .parts(request.parts())
                .contextId(request.contextId())
                .taskId(request.taskId())
                .referenceTaskIds(request.referenceTaskIds().isEmpty() ? null : request.referenceTaskIds())
                .metadata(metadata.isEmpty() ? null : metadata)
                .extensions(request.extensions().isEmpty() ? null : request.extensions())
                .build();
    }

    private static MessageSendParams buildParams(final SendRequest request, final Message message,
                                                 final RemoteAgent agent) {
        final String tenant = firstNonBlank(request.tenant(), agent.tenant().orElse(null));
        return MessageSendParams.builder()
                .message(message)
                .configuration(request.configuration())
                .metadata(request.paramsMetadata().isEmpty() ? null : request.paramsMetadata())
                .tenant(tenant)
                .build();
    }

    private static RemoteSkill resolveSkill(final RemoteAgent agent, final String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        return agent.skills().stream()
                .filter(skill -> skillId.equals(skill.id()))
                .findFirst()
                .orElse(null);
    }

    private static boolean supportsStreaming(final RemoteAgent agent) {
        final var capabilities = agent.card().capabilities();
        return capabilities != null && capabilities.streaming();
    }

    private static String firstNonBlank(final String first, final String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String errorMessage(final Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        final var message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }
        return message;
    }
}
