package io.github.khezyapp.a2a.store.jpa;

import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the A2A JPA stores.
 *
 * <p>Encryption keys and rotation belong to {@code khezy-crypto}
 * ({@code io.github.khezyapp.crypto.*}); the A2A store is fail-closed and is not activated
 * when no {@code StringEncryptor} bean is present, so payloads are never silently stored as
 * plaintext.</p>
 */
@ConfigurationProperties(prefix = "io.github.khezyapp.a2a.store")
public class A2AStoreProperties {

    /**
     * Store backend selected by the server auto-configuration.
     */
    public enum StoreType {

        /** In-memory SDK stores. */
        MEMORY,

        /** JPA-backed stores from this module. */
        JPA
    }

    /**
     * Selected backend. Defaults to JPA because this module only activates when JPA is
     * present on the classpath.
     */
    private StoreType type = StoreType.JPA;

    private final TaskProperties task = new TaskProperties();

    private final PushProperties push = new PushProperties();

    private final ConversationProperties conversation = new ConversationProperties();

    public StoreType getType() {
        return type;
    }

    public void setType(final StoreType type) {
        this.type = type;
    }

    public TaskProperties getTask() {
        return task;
    }

    public PushProperties getPush() {
        return push;
    }

    public ConversationProperties getConversation() {
        return conversation;
    }

    /**
     * Task-store settings.
     */
    public static class TaskProperties {

        private Duration gracePeriod = Duration.ofSeconds(15);

        public Duration getGracePeriod() {
            return gracePeriod;
        }

        public void setGracePeriod(final Duration gracePeriod) {
            this.gracePeriod = gracePeriod;
        }
    }

    /**
     * Push-notification store settings.
     */
    public static class PushProperties {

        private int maxPerTask = PushNotificationConfigStore.DEFAULT_MAX_PUSH_CONFIGS_PER_TASK;

        public int getMaxPerTask() {
            return maxPerTask;
        }

        public void setMaxPerTask(final int maxPerTask) {
            this.maxPerTask = maxPerTask;
        }
    }

    /**
     * Conversation-store settings.
     */
    public static class ConversationProperties {

        private Duration ttl = Duration.ofHours(1);

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(final Duration ttl) {
            this.ttl = ttl;
        }
    }
}
