package io.github.khezyapp.a2a.core.client.invoke;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.Part;

import io.github.khezyapp.a2a.core.client.RemoteAgent;

/**
 * One client send, mirroring an SDK {@code MessageSendParams} plus the client
 * conventions KHEZY adds (skill id, conversation ids, extensions, options).
 *
 * @param agent the target agent
 * @param parts the message parts
 * @param skillId the requested skill id, or {@code null}
 * @param contextId an explicit A2A context id, or {@code null}
 * @param taskId an explicit A2A task id, or {@code null}
 * @param referenceTaskIds task ids referenced by this message
 * @param tenant the SDK tenant, or {@code null}
 * @param configuration the SDK send configuration, or {@code null}
 * @param paramsMetadata metadata for {@code MessageSendParams}
 * @param messageMetadata metadata for the SDK message
 * @param extensions protocol extensions requested for this send
 * @param options client options
 */
public record SendRequest(RemoteAgent agent, List<Part<?>> parts, String skillId,
                          String contextId, String taskId, List<String> referenceTaskIds,
                          String tenant, MessageSendConfiguration configuration,
                          Map<String, Object> paramsMetadata, Map<String, Object> messageMetadata,
                          List<String> extensions, SendOptions options) {

    /**
     * Creates a request with defensive collections and default options.
     */
    public SendRequest {
        Objects.requireNonNull(agent, "agent must not be null");
        parts = parts == null ? List.of() : List.copyOf(parts);
        referenceTaskIds = referenceTaskIds == null ? List.of() : List.copyOf(referenceTaskIds);
        extensions = extensions == null ? List.of() : List.copyOf(extensions);
        paramsMetadata = copyMap(paramsMetadata);
        messageMetadata = copyMap(messageMetadata);
        options = options == null ? SendOptions.defaults() : options;
    }

    /**
     * Returns a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder pre-populated with this request.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Returns whether this request pins an A2A context or task.
     *
     * @return {@code true} when a non-blank context id or task id is present
     */
    public boolean hasExplicitTaskRef() {
        return hasText(contextId) || hasText(taskId);
    }

    private static Map<String, Object> copyMap(final Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        final var copy = new LinkedHashMap<String, Object>(source.size());
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Builder for {@link SendRequest}.
     */
    public static final class Builder {

        private RemoteAgent agent;
        private List<Part<?>> parts = List.of();
        private String skillId;
        private String contextId;
        private String taskId;
        private List<String> referenceTaskIds = List.of();
        private String tenant;
        private MessageSendConfiguration configuration;
        private Map<String, Object> paramsMetadata = Map.of();
        private Map<String, Object> messageMetadata = Map.of();
        private List<String> extensions = List.of();
        private SendOptions options = SendOptions.defaults();
        private boolean explicitTaskRef;

        private Builder() {
        }

        private Builder(final SendRequest request) {
            this.agent = request.agent();
            this.parts = request.parts();
            this.skillId = request.skillId();
            this.contextId = request.contextId();
            this.taskId = request.taskId();
            this.referenceTaskIds = request.referenceTaskIds();
            this.tenant = request.tenant();
            this.configuration = request.configuration();
            this.paramsMetadata = request.paramsMetadata();
            this.messageMetadata = request.messageMetadata();
            this.extensions = request.extensions();
            this.options = request.options();
            this.explicitTaskRef = request.hasExplicitTaskRef();
        }

        /**
         * Sets the target agent.
         *
         * @param value the agent
         * @return this builder
         */
        public Builder agent(final RemoteAgent value) {
            this.agent = value;
            return this;
        }

        /**
         * Sets the message parts.
         *
         * @param value the parts
         * @return this builder
         */
        public Builder parts(final List<? extends Part<?>> value) {
            this.parts = value == null ? List.of() : new ArrayList<>(value);
            return this;
        }

        /**
         * Adds one message part.
         *
         * @param value the part
         * @return this builder
         */
        public Builder part(final Part<?> value) {
            final var updated = new ArrayList<Part<?>>(this.parts);
            updated.add(value);
            this.parts = updated;
            return this;
        }

        /**
         * Sets the requested skill id.
         *
         * @param value the skill id
         * @return this builder
         */
        public Builder skillId(final String value) {
            this.skillId = value;
            return this;
        }

        /**
         * Sets an explicit A2A context id.
         *
         * @param value the context id
         * @return this builder
         */
        public Builder contextId(final String value) {
            this.contextId = blankToNull(value);
            if (this.contextId != null) {
                this.explicitTaskRef = true;
            }
            return this;
        }

        /**
         * Sets an explicit A2A task id.
         *
         * @param value the task id
         * @return this builder
         */
        public Builder taskId(final String value) {
            this.taskId = blankToNull(value);
            if (this.taskId != null) {
                this.explicitTaskRef = true;
            }
            return this;
        }

        /**
         * Sets reference task ids.
         *
         * @param value the reference task ids
         * @return this builder
         */
        public Builder referenceTaskIds(final List<String> value) {
            this.referenceTaskIds = value;
            return this;
        }

        /**
         * Sets the SDK tenant.
         *
         * @param value the tenant
         * @return this builder
         */
        public Builder tenant(final String value) {
            this.tenant = value;
            return this;
        }

        /**
         * Sets the SDK send configuration.
         *
         * @param value the configuration
         * @return this builder
         */
        public Builder configuration(final MessageSendConfiguration value) {
            this.configuration = value;
            return this;
        }

        /**
         * Sets {@code MessageSendParams} metadata.
         *
         * @param value the metadata
         * @return this builder
         */
        public Builder paramsMetadata(final Map<String, Object> value) {
            this.paramsMetadata = value;
            return this;
        }

        /**
         * Sets message metadata.
         *
         * @param value the metadata
         * @return this builder
         */
        public Builder messageMetadata(final Map<String, Object> value) {
            this.messageMetadata = value;
            return this;
        }

        /**
         * Sets requested protocol extensions.
         *
         * @param value the extension URIs
         * @return this builder
         */
        public Builder extensions(final List<String> value) {
            this.extensions = value;
            return this;
        }

        /**
         * Sets client options.
         *
         * @param value the options
         * @return this builder
         */
        public Builder options(final SendOptions value) {
            this.options = value;
            return this;
        }

        /**
         * Returns whether the builder pins an A2A context or task.
         *
         * @return {@code true} when an explicit non-blank id was set
         */
        public boolean hasExplicitTaskRef() {
            return explicitTaskRef;
        }

        /**
         * Returns the requested skill id.
         *
         * @return the skill id, or {@code null}
         */
        public String skillId() {
            return skillId;
        }

        /**
         * Builds the request.
         *
         * @return the request
         */
        public SendRequest build() {
            return new SendRequest(
                    agent,
                    parts,
                    skillId,
                    contextId,
                    taskId,
                    referenceTaskIds,
                    tenant,
                    configuration,
                    paramsMetadata,
                    messageMetadata,
                    extensions,
                    options);
        }

        private static String blankToNull(final String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }
}
