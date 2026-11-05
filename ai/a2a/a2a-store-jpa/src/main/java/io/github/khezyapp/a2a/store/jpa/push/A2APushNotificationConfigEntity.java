package io.github.khezyapp.a2a.store.jpa.push;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * JPA entity for an A2A push-notification config.
 *
 * <p>The full config is serialized with the SDK {@code JsonUtil} and stored encrypted in
 * {@code task_data} because it may contain a bearer token and authentication credentials.
 * The entity only ever holds the ciphertext.</p>
 */
@Entity
@Table(name = "a2a_push_notification_configs")
public class A2APushNotificationConfigEntity {

    @EmbeddedId
    private A2APushConfigId id;

    @Column(name = "task_data", columnDefinition = "TEXT", nullable = false)
    private String configData;

    @Column(name = "protocol_version")
    private String protocolVersion;

    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * Creates an empty entity for JPA.
     */
    protected A2APushNotificationConfigEntity() {
    }

    /**
     * Creates an entity from a config and its already-encrypted payload.
     *
     * @param taskId the task identifier
     * @param config the config with a resolved (non-null) id
     * @param protocolVersion the protocol version that registered the config
     * @param encryptedPayload the encrypted serialized config
     * @return the new entity
     */
    public static A2APushNotificationConfigEntity fromConfig(
            final String taskId,
            final TaskPushNotificationConfig config,
            final String protocolVersion,
            final String encryptedPayload) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(protocolVersion, "protocolVersion must not be null");
        Objects.requireNonNull(encryptedPayload, "encryptedPayload must not be null");
        final var entity = new A2APushNotificationConfigEntity();
        entity.id = new A2APushConfigId(taskId,
                Objects.requireNonNull(config.id(), "configId must not be null"));
        entity.configData = encryptedPayload;
        entity.protocolVersion = protocolVersion;
        entity.createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return entity;
    }

    /**
     * Updates the payload and protocol version of an existing config.
     *
     * @param config the updated config
     * @param protocolVersion the protocol version
     * @param encryptedPayload the encrypted serialized config
     */
    public void updateFrom(final TaskPushNotificationConfig config,
                           final String protocolVersion,
                           final String encryptedPayload) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(protocolVersion, "protocolVersion must not be null");
        this.configData = Objects.requireNonNull(encryptedPayload,
                "encryptedPayload must not be null");
        this.protocolVersion = protocolVersion;
    }

    /**
     * Deserializes the config from a decrypted payload.
     *
     * @param plaintextPayload the decrypted serialized config
     * @return the config
     * @throws JsonProcessingException when the payload is not valid config JSON
     */
    public TaskPushNotificationConfig toConfig(final String plaintextPayload)
            throws JsonProcessingException {
        return JsonUtil.fromJson(plaintextPayload, TaskPushNotificationConfig.class);
    }

    public A2APushConfigId getId() {
        return id;
    }

    public String getConfigData() {
        return configData;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
