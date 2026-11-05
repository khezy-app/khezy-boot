package io.github.khezyapp.a2a.store.jpa.crypto;

import io.github.khezyapp.crypto.contract.CipherContext;

import java.util.Objects;

/**
 * Cipher contexts used by the A2A JPA stores.
 *
 * <p>Each stored payload is bound to its row through the associated data (AAD), so a
 * ciphertext copied to another row can no longer be decrypted. The AAD is never stored;
 * it is derived from the row identity on every read and write.</p>
 */
public final class A2ACipherContexts {

    /** Purpose label for serialized {@code Task} payloads. */
    public static final String TASK_PURPOSE = "a2a-task";

    /** Purpose label for serialized push-notification configs. */
    public static final String PUSH_CONFIG_PURPOSE = "a2a-push-config";

    /** AAD prefix for serialized {@code Task} payloads. */
    public static final String TASK_AAD_PREFIX = "a2a:task:";

    /** AAD prefix for serialized push-notification configs. */
    public static final String PUSH_CONFIG_AAD_PREFIX = "a2a:push:";

    private A2ACipherContexts() {
    }

    /**
     * Builds the context for a task row.
     *
     * @param taskId the task identifier
     * @return the task cipher context
     */
    public static CipherContext task(final String taskId) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        return CipherContext.of(TASK_PURPOSE, TASK_AAD_PREFIX + taskId);
    }

    /**
     * Builds the context for a push-notification config row.
     *
     * @param taskId the task identifier
     * @param configId the resolved (never {@code null}) config identifier
     * @return the push-config cipher context
     */
    public static CipherContext pushConfig(final String taskId, final String configId) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(configId, "configId must not be null");
        return CipherContext.of(PUSH_CONFIG_PURPOSE,
                PUSH_CONFIG_AAD_PREFIX + taskId + ":" + configId);
    }
}
