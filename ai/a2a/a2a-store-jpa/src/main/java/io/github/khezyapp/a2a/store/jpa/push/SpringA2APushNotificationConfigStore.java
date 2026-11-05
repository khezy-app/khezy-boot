package io.github.khezyapp.a2a.store.jpa.push;

import io.github.khezyapp.a2a.store.jpa.crypto.A2ACipherContexts;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import io.github.khezyapp.crypto.defaults.KhezyCipherTextCodec;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.util.PageToken;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Spring/JPA implementation of the SDK {@link PushNotificationConfigStore}.
 *
 * <p>Payloads are serialized with the SDK {@code JsonUtil} and encrypted with the
 * {@code a2a:push:<taskId>:<configId>} AAD. SDK 1.4 semantics are honored: an omitted
 * {@code id} resolves to the task id and is create-only; updating the default config
 * requires an explicit id. The per-task limit defaults to
 * {@link PushNotificationConfigStore#maxPushConfigsPerTask} and can be overridden.</p>
 */
public class SpringA2APushNotificationConfigStore implements PushNotificationConfigStore {

    private final A2APushNotificationConfigRepository repository;
    private final StringEncryptor encryptor;
    private final int maxPerTask;

    /**
     * Creates a store with the SDK default per-task limit.
     *
     * @param repository the push-config repository
     * @param encryptor the payload encryptor
     */
    public SpringA2APushNotificationConfigStore(
            final A2APushNotificationConfigRepository repository,
            final StringEncryptor encryptor) {
        this(repository, encryptor,
                PushNotificationConfigStore.maxPushConfigsPerTask(null));
    }

    /**
     * Creates a store with an explicit per-task limit.
     *
     * @param repository the push-config repository
     * @param encryptor the payload encryptor
     * @param maxPerTask maximum configs per task; non-positive falls back to the SDK default
     */
    public SpringA2APushNotificationConfigStore(
            final A2APushNotificationConfigRepository repository,
            final StringEncryptor encryptor,
            final int maxPerTask) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.encryptor = Objects.requireNonNull(encryptor, "encryptor must not be null");
        this.maxPerTask = maxPerTask > 0
                ? maxPerTask
                : PushNotificationConfigStore.maxPushConfigsPerTask(null);
    }

    @Override
    @Transactional
    public TaskPushNotificationConfig setInfo(
            final TaskPushNotificationConfig notificationConfig) {
        return setInfo(notificationConfig, null);
    }

    @Override
    @Transactional
    public TaskPushNotificationConfig setInfo(
            final TaskPushNotificationConfig notificationConfig,
            final @Nullable String protocolVersion) {
        Objects.requireNonNull(notificationConfig, "notificationConfig must not be null");
        final var taskId = Objects.requireNonNull(notificationConfig.taskId(),
                "taskId must not be null");
        final var missingId = Objects.isNull(notificationConfig.id())
                || notificationConfig.id().isBlank();
        final var configId = missingId ? taskId : notificationConfig.id();
        final var resolved = missingId
                ? TaskPushNotificationConfig.builder(notificationConfig).id(configId).build()
                : notificationConfig;
        final var version = PushNotificationConfigStore.resolveProtocolVersion(protocolVersion);
        final var id = new A2APushConfigId(taskId, configId);
        final var existing = repository.findById(id).orElse(null);

        if (Objects.nonNull(existing) && missingId) {
            throw new InvalidParamsError("Push notification config '" + configId
                    + "' already exists for task '" + taskId
                    + "'; supply an explicit id to update it");
        }
        final var payload = encode(resolved, taskId, configId);
        if (Objects.isNull(existing)) {
            if (repository.countByIdTaskId(taskId) >= maxPerTask) {
                throw new InvalidParamsError("Too many push notification configs for task '"
                        + taskId + "' (max " + maxPerTask + ")");
            }
            repository.save(A2APushNotificationConfigEntity.fromConfig(
                    taskId, resolved, version, payload));
        } else {
            existing.updateFrom(resolved, version, payload);
            repository.save(existing);
        }
        return resolved;
    }

    @Override
    @Transactional(readOnly = true)
    public ListTaskPushNotificationConfigsResult getInfo(
            final ListTaskPushNotificationConfigsParams params) {
        Objects.requireNonNull(params, "params must not be null");
        final var taskId = params.id();
        final var pageSize = params.getEffectivePageSize();
        final var token = PageToken.fromString(params.pageToken());
        final var sentinel = Instant.EPOCH;
        final var pageRequest = PageRequest.of(0, pageSize + 1);
        final var rows = Objects.isNull(token)
                ? repository.findFirstPage(taskId, sentinel, pageRequest)
                : repository.findAfter(taskId, sentinel, token.timestamp(), token.id(),
                        pageRequest);

        String nextPageToken = null;
        var page = rows;
        if (rows.size() > pageSize) {
            page = rows.subList(0, pageSize);
            final var last = page.get(page.size() - 1);
            final var timestamp = Objects.requireNonNullElse(last.getCreatedAt(), sentinel);
            nextPageToken = new PageToken(timestamp, last.getId().getConfigId()).toString();
        }

        final var configs = new ArrayList<TaskPushNotificationConfig>(page.size());
        for (final var entity : page) {
            configs.add(decode(entity));
        }
        return new ListTaskPushNotificationConfigsResult(configs, nextPageToken);
    }

    @Override
    @Transactional
    public void deleteInfo(final String taskId, final @Nullable String configId) {
        final var resolvedConfigId = Objects.isNull(configId) || configId.isBlank()
                ? taskId
                : configId;
        repository.findById(new A2APushConfigId(taskId, resolvedConfigId))
                .ifPresent(repository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public String getProtocolVersion(final String taskId, final @Nullable String configId) {
        final var resolvedConfigId = Objects.isNull(configId) || configId.isBlank()
                ? taskId
                : configId;
        return repository.findById(new A2APushConfigId(taskId, resolvedConfigId))
                .map(A2APushNotificationConfigEntity::getProtocolVersion)
                .filter(Objects::nonNull)
                .orElseGet(() -> PushNotificationConfigStore.resolveProtocolVersion(null));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getProtocolVersions(final String taskId) {
        final var versions = new HashMap<String, String>();
        for (final var row : repository.findProtocolVersions(taskId)) {
            versions.put((String) row[0], (String) row[1]);
        }
        return Map.copyOf(versions);
    }

    private String encode(final TaskPushNotificationConfig config,
                          final String taskId,
                          final String configId) {
        try {
            final var json = JsonUtil.toJson(config);
            return encryptor.encrypt(json, A2ACipherContexts.pushConfig(taskId, configId));
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize push notification config for task '"
                    + taskId + "'", e);
        }
    }

    private TaskPushNotificationConfig decode(final A2APushNotificationConfigEntity entity) {
        final var taskId = entity.getId().getTaskId();
        final var configId = entity.getId().getConfigId();
        try {
            final var stored = entity.getConfigData();
            final var payload = stored.startsWith(KhezyCipherTextCodec.PREFIX + ":")
                    ? encryptor.decrypt(stored, A2ACipherContexts.pushConfig(taskId, configId))
                    : stored;
            return entity.toConfig(payload);
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize push notification config for task '"
                    + taskId + "'", e);
        } catch (final RuntimeException e) {
            throw new IllegalStateException("Failed to decrypt push notification config for task '"
                    + taskId + "'", e);
        }
    }
}
