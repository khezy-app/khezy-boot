package io.github.khezyapp.a2a.core.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys;

/**
 * Convenience view over the SDK {@link RequestContext} and {@link AgentEmitter}.
 *
 * <p>This is a helper, not a wire model: incoming and task values are the SDK records
 * themselves, and every response method delegates to the emitter. Agents that need the
 * full SDK surface can fall back to {@link #context()} and {@link #emitter()}.</p>
 */
public final class A2ARequest {

    private final RequestContext context;
    private final AgentEmitter emitter;

    /**
     * Creates a request view.
     *
     * @param context the SDK request context
     * @param emitter the SDK event emitter
     */
    public A2ARequest(final RequestContext context,
                      final AgentEmitter emitter) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    }

    /**
     * Returns the incoming user message.
     *
     * @return the SDK message
     */
    public Message incoming() {
        return context.getMessage();
    }

    /**
     * Returns the task being continued, when this is not a new conversation.
     *
     * @return the current task, if present
     */
    public Optional<Task> currentTask() {
        return Optional.ofNullable(context.getTask());
    }

    /**
     * Returns the task identifier, falling back to the current task when needed.
     *
     * @return the task identifier, or {@code null} when unavailable
     */
    public String taskId() {
        final var direct = context.getTaskId();
        if (Objects.nonNull(direct)) {
            return direct;
        }
        final var task = context.getTask();
        return Objects.isNull(task) ? null : task.id();
    }

    /**
     * Returns the conversation identifier, falling back to the current task when needed.
     *
     * @return the context identifier, or {@code null} when unavailable
     */
    public String contextId() {
        final var direct = context.getContextId();
        if (Objects.nonNull(direct)) {
            return direct;
        }
        final var task = context.getTask();
        return Objects.isNull(task) ? null : task.contextId();
    }

    /**
     * Returns the authenticated caller.
     *
     * @return the caller, or empty when the request is unauthenticated
     */
    public Optional<User> caller() {
        final var callContext = context.getCallContext();
        if (Objects.isNull(callContext)) {
            return Optional.empty();
        }
        final var user = callContext.getUser();
        if (Objects.isNull(user) || !user.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /**
     * Returns the tenant from the request or, when absent, from the call-context state.
     *
     * @return the tenant, if present
     */
    public Optional<String> tenant() {
        final var direct = context.getTenant();
        if (Objects.nonNull(direct) && !direct.isBlank()) {
            return Optional.of(direct);
        }
        final var callContext = context.getCallContext();
        if (Objects.isNull(callContext) || Objects.isNull(callContext.getState())) {
            return Optional.empty();
        }
        final var value = callContext.getState().get(JSONRPCContextKeys.TENANT_KEY);
        if (value instanceof String tenant && !tenant.isBlank()) {
            return Optional.of(tenant);
        }
        return Optional.empty();
    }

    /**
     * Returns request metadata merged with call-context state.
     *
     * <p>Call-context state wins on key collisions. The returned map is an immutable copy.</p>
     *
     * @return the merged attributes
     */
    public Map<String, Object> attributes() {
        final var merged = new LinkedHashMap<String, Object>();
        final var metadata = context.getMetadata();
        if (Objects.nonNull(metadata)) {
            merged.putAll(metadata);
        }
        final var callContext = context.getCallContext();
        if (Objects.nonNull(callContext) && Objects.nonNull(callContext.getState())) {
            merged.putAll(callContext.getState());
        }
        return Collections.unmodifiableMap(merged);
    }

    /**
     * Returns other tasks in the same conversation context.
     *
     * @return an immutable copy of the related tasks, empty when none were populated
     */
    public List<Task> relatedTasks() {
        final var related = context.getRelatedTasks();
        return Objects.isNull(related) ? List.of() : List.copyOf(related);
    }

    /**
     * Returns all text parts of the incoming message joined with newlines.
     *
     * @return the user input, or an empty string when there is no message
     */
    public String userInput() {
        return context.getUserInput();
    }

    /**
     * Sends an agent reply.
     *
     * @param text the reply text
     */
    public void reply(final String text) {
        emitter.sendMessage(message(text, null));
    }

    /**
     * Sends an agent reply with metadata.
     *
     * @param text the reply text
     * @param metadata the message metadata
     */
    public void reply(final String text, final Map<String, Object> metadata) {
        emitter.sendMessage(message(text, metadata));
    }

    /**
     * Adds a text artifact.
     *
     * @param name the artifact name
     * @param text the artifact text
     */
    public void artifact(final String name, final String text) {
        emitter.addArtifact(List.of(new TextPart(text)), null, name, Map.of());
    }

    /**
     * Adds a text artifact with a description.
     *
     * @param name the artifact name
     * @param description the artifact description
     * @param text the artifact text
     */
    public void artifact(final String name, final String description, final String text) {
        final var artifact = Artifact.builder()
                .artifactId(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .parts(List.of(new TextPart(text)))
                .metadata(Map.of())
                .build();
        final var event = TaskArtifactUpdateEvent.builder()
                .taskId(context.getTaskId())
                .contextId(context.getContextId())
                .artifact(artifact)
                .build();
        emitter.emitEvent(event);
    }

    /**
     * Marks the task completed without a message.
     */
    public void complete() {
        emitter.complete();
    }

    /**
     * Marks the task completed with a message.
     *
     * @param text the completion text; blank means no message
     */
    public void complete(final String text) {
        if (isBlank(text)) {
            emitter.complete();
            return;
        }
        emitter.complete(message(text, null));
    }

    /**
     * Marks the task as requiring user input.
     *
     * @param question the question for the user; blank means no message
     */
    public void requireInput(final String question) {
        if (isBlank(question)) {
            emitter.requiresInput();
            return;
        }
        emitter.requiresInput(message(question, null));
    }

    /**
     * Marks the task as requiring authentication.
     *
     * @param instruction the instruction for the caller; blank means no message
     */
    public void requireAuth(final String instruction) {
        if (isBlank(instruction)) {
            emitter.requiresAuth();
            return;
        }
        emitter.requiresAuth(message(instruction, null));
    }

    /**
     * Marks the task failed.
     *
     * @param reason the failure reason; blank means no message
     */
    public void fail(final String reason) {
        if (isBlank(reason)) {
            emitter.fail();
            return;
        }
        emitter.fail(message(reason, null));
    }

    /**
     * Marks the task canceled.
     */
    public void cancel() {
        emitter.cancel();
    }

    /**
     * Returns the underlying SDK request context.
     *
     * @return the request context
     */
    public RequestContext context() {
        return context;
    }

    /**
     * Returns the underlying SDK emitter.
     *
     * @return the emitter
     */
    public AgentEmitter emitter() {
        return emitter;
    }

    private Message message(final String text,
                            final Map<String, Object> metadata) {
        return emitter.messageBuilder()
                .parts(List.of(new TextPart(text)))
                .metadata(metadata)
                .build();
    }

    private static boolean isBlank(final String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}
