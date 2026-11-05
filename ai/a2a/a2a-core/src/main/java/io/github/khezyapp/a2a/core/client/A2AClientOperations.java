package io.github.khezyapp.a2a.core.client;

import java.util.Objects;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;

/**
 * Thin facade over the SDK client task and push-configuration operations.
 *
 * <p>Hosts never build SDK requests directly for these calls. SDK exceptions propagate
 * unchanged: this class adds no error hierarchy of its own.</p>
 */
public class A2AClientOperations {

    /**
     * Returns a task.
     *
     * @param agent the target agent
     * @param params the SDK query parameters
     * @return the task
     */
    public Task getTask(final RemoteAgent agent,
                        final TaskQueryParams params) {
        return client(agent).getTask(params, null);
    }

    /**
     * Lists tasks visible to the caller.
     *
     * @param agent the target agent
     * @param params the SDK list parameters
     * @return the SDK list result
     */
    public ListTasksResult listTasks(final RemoteAgent agent,
                                     final ListTasksParams params) {
        return client(agent).listTasks(params, null);
    }

    /**
     * Cancels a task.
     *
     * @param agent the target agent
     * @param params the SDK cancel parameters
     * @return the updated task
     */
    public Task cancelTask(final RemoteAgent agent,
                           final CancelTaskParams params) {
        return client(agent).cancelTask(params, null);
    }

    /**
     * Creates a push-notification configuration.
     *
     * <p>Note that an omitted config id means create-only in the SDK; this method does
     * not upsert.</p>
     *
     * @param agent the target agent
     * @param config the configuration to create
     * @return the created configuration
     */
    public TaskPushNotificationConfig createPushConfig(final RemoteAgent agent,
                                                       final TaskPushNotificationConfig config) {
        return client(agent).createTaskPushNotificationConfiguration(config, null);
    }

    /**
     * Gets one push-notification configuration.
     *
     * @param agent the target agent
     * @param params the SDK get parameters
     * @return the configuration
     */
    public TaskPushNotificationConfig getPushConfig(final RemoteAgent agent,
                                                    final GetTaskPushNotificationConfigParams params) {
        return client(agent).getTaskPushNotificationConfiguration(params, null);
    }

    /**
     * Lists push-notification configurations for a task.
     *
     * @param agent the target agent
     * @param params the SDK list parameters
     * @return the configurations
     */
    public ListTaskPushNotificationConfigsResult listPushConfigs(
            final RemoteAgent agent, final ListTaskPushNotificationConfigsParams params) {
        return client(agent).listTaskPushNotificationConfigurations(params, null);
    }

    /**
     * Deletes a push-notification configuration.
     *
     * @param agent the target agent
     * @param params the SDK delete parameters
     */
    public void deletePushConfig(final RemoteAgent agent, final DeleteTaskPushNotificationConfigParams params) {
        client(agent).deleteTaskPushNotificationConfigurations(params, null);
    }

    /**
     * Returns the extended agent card, when the agent supports it.
     *
     * @param agent the target agent
     * @return the extended card
     */
    public AgentCard getExtendedAgentCard(final RemoteAgent agent) {
        return client(agent).getExtendedAgentCard(agent.tenant().orElse(null), null);
    }

    private static Client client(final RemoteAgent agent) {
        return Objects.requireNonNull(agent, "agent must not be null").client();
    }
}
