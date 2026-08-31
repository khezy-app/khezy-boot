package io.github.khezyapp.a2a.starter.web;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;

import java.util.List;
import java.util.Map;

/** Shared SDK spec fixtures for the web-layer controller tests. */
final class WebFixtures {

    private WebFixtures() {
    }

    static AgentCard agentCard() {
        return AgentCard.builder()
                .name("Stub Agent")
                .description("stub agent card for web tests")
                .url("http://localhost:8080")
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of())
                .build();
    }

    static TaskStatusUpdateEvent completedUpdate() {
        return new TaskStatusUpdateEvent("t-1",
                new TaskStatus(TaskState.TASK_STATE_COMPLETED), "c-1", Map.of());
    }

    static Task workingTask(final String taskId) {
        return Task.builder()
                .id(taskId)
                .contextId("c-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .build();
    }
}
