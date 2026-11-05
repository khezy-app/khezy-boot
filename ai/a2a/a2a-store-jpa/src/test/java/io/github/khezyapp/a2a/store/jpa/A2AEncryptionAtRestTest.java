package io.github.khezyapp.a2a.store.jpa;

import io.github.khezyapp.a2a.store.jpa.push.SpringA2APushNotificationConfigStore;
import io.github.khezyapp.a2a.store.jpa.task.SpringA2ATaskStore;
import io.github.khezyapp.a2a.testapp.TestStoreApplication;
import io.github.khezyapp.crypto.exception.CiphertextIntegrityException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.tasks.TaskSerializationException;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies payload columns are ciphertext at rest, stay readable through the store, and are
 * bound to their row through the AAD.
 */
@SpringBootTest(classes = TestStoreApplication.class)
class A2AEncryptionAtRestTest {

    private static final String SECRET = "card-number-4111-1111-1111-1111";

    @Autowired
    private SpringA2ATaskStore taskStore;

    @Autowired
    private SpringA2APushNotificationConfigStore pushStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesTaskPayloadEncryptedAndReadsItBack() {
        final var taskId = "enc-task";
        final var task = Task.builder()
                .id(taskId)
                .contextId("ctx-enc")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED, null,
                        OffsetDateTime.parse("2026-01-01T00:00:00Z")))
                .history(new Message(Message.Role.ROLE_USER,
                        List.of(new TextPart("my secret is " + SECRET)),
                        "msg-enc", "ctx-enc", taskId, null, null, null))
                .build();
        taskStore.save(task, false);

        final var raw = jdbc.queryForObject(
                "select task_data from a2a_tasks where task_id = ?", String.class, taskId);
        assertThat(raw).startsWith("kzy:v1:");
        assertThat(raw).doesNotContain(SECRET);

        final var loaded = taskStore.get(taskId);
        assertThat(loaded).isNotNull();
        assertThat(((TextPart) loaded.history().get(0).parts().get(0)).text())
                .contains(SECRET);
    }

    @Test
    void storesPushConfigPayloadEncrypted() {
        final var taskId = "enc-push";
        pushStore.setInfo(TaskPushNotificationConfig.builder()
                .id("cfg-enc")
                .taskId(taskId)
                .url("https://example.com/hook")
                .token(SECRET)
                .build());

        final var raw = jdbc.queryForObject(
                "select task_data from a2a_push_notification_configs"
                        + " where task_id = ? and config_id = ?",
                String.class, taskId, "cfg-enc");
        assertThat(raw).startsWith("kzy:v1:");
        assertThat(raw).doesNotContain(SECRET);

        final var loaded = pushStore.getInfo(new ListTaskPushNotificationConfigsParams(taskId));
        assertThat(loaded.configs().get(0).token()).isEqualTo(SECRET);
    }

    @Test
    void rejectsCiphertextSwappedToAnotherRow() {
        final var sourceId = "enc-source";
        final var targetId = "enc-target";
        taskStore.save(task(sourceId, "ctx-swap"), false);
        taskStore.save(task(targetId, "ctx-swap"), false);

        final var sourcePayload = jdbc.queryForObject(
                "select task_data from a2a_tasks where task_id = ?", String.class, sourceId);
        jdbc.update("update a2a_tasks set task_data = ? where task_id = ?",
                sourcePayload, targetId);

        assertThatThrownBy(() -> taskStore.get(targetId))
                .isInstanceOf(TaskSerializationException.class)
                .hasMessageContaining("decrypt")
                .hasCauseInstanceOf(CiphertextIntegrityException.class);
    }

    @Test
    void readsLegacyPlaintextTaskPayload() throws JsonProcessingException {
        final var taskId = "legacy-task";
        final var task = task(taskId, "ctx-legacy");
        taskStore.save(task, false);
        jdbc.update("update a2a_tasks set task_data = ? where task_id = ?",
                JsonUtil.toJson(task), taskId);

        final var loaded = taskStore.get(taskId);
        assertThat(loaded).isNotNull();
        assertThat(loaded.id()).isEqualTo(taskId);
        assertThat(loaded.contextId()).isEqualTo("ctx-legacy");
    }

    private static Task task(final String taskId, final String contextId) {
        return Task.builder()
                .id(taskId)
                .contextId(contextId)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null,
                        OffsetDateTime.parse("2026-01-01T00:00:00Z")))
                .build();
    }
}
