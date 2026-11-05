package io.github.khezyapp.a2a.store.jpa.push;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for a push-notification config: {@code (task_id, config_id)}.
 *
 * <p>Both parts are required. Omitted config ids are resolved to the task id by the store
 * before an id is constructed.</p>
 */
@Embeddable
public class A2APushConfigId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "config_id", nullable = false)
    private String configId;

    /**
     * Creates an empty id for JPA.
     */
    protected A2APushConfigId() {
    }

    /**
     * Creates a fully populated id.
     *
     * @param taskId the task identifier
     * @param configId the resolved config identifier
     */
    public A2APushConfigId(final String taskId, final String configId) {
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.configId = Objects.requireNonNull(configId, "configId must not be null");
    }

    public String getTaskId() {
        return taskId;
    }

    public String getConfigId() {
        return configId;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof A2APushConfigId that)) {
            return false;
        }
        return taskId.equals(that.taskId) && configId.equals(that.configId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, configId);
    }

    @Override
    public String toString() {
        return "A2APushConfigId{taskId='" + taskId + "', configId='" + configId + "'}";
    }
}
