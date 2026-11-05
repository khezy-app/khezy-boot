package io.github.khezyapp.a2a.store.jpa;

import io.github.khezyapp.a2a.store.jpa.crypto.A2ACipherContexts;
import io.github.khezyapp.a2a.store.jpa.push.A2APushNotificationConfigRepository;
import io.github.khezyapp.a2a.store.jpa.push.SpringA2APushNotificationConfigStore;
import io.github.khezyapp.a2a.store.jpa.task.A2ATaskRepository;
import io.github.khezyapp.a2a.store.jpa.task.SpringA2ATaskStore;
import io.github.khezyapp.a2a.testapp.TestStoreApplication;
import io.github.khezyapp.crypto.contract.CipherTextCodec;
import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.contract.KeyTransitionContext;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import io.github.khezyapp.crypto.defaults.AesGcmBackend;
import io.github.khezyapp.crypto.defaults.DefaultEncryptor;
import io.github.khezyapp.crypto.defaults.DefaultStringEncryptor;
import io.github.khezyapp.crypto.defaults.InMemoryEncryptionCounter;
import io.github.khezyapp.crypto.defaults.InMemoryKeyProvider;
import io.github.khezyapp.crypto.defaults.KhezyCipherTextCodec;
import io.github.khezyapp.crypto.exception.CiphertextIntegrityException;
import io.github.khezyapp.crypto.rotation.DefaultReEncryptor;
import io.github.khezyapp.crypto.rotation.ReEncryptionExecutor;
import io.github.khezyapp.crypto.rotation.ReEncryptionRecord;
import io.github.khezyapp.crypto.rotation.ReEncryptionSink;
import io.github.khezyapp.crypto.rotation.ReEncryptionSource;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies stored payloads survive a key rotation, can be migrated with the shared
 * {@link ReEncryptionExecutor}, and keep their row AAD stable during migration.
 */
@SpringBootTest(classes = TestStoreApplication.class)
class A2ARotationMigrationTest {

    private static final String SECRET = "rotated-secret-value";

    @Autowired
    private A2ATaskRepository taskRepository;

    @Autowired
    private A2APushNotificationConfigRepository pushRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migratesTaskAndPushRowsToTheActiveKey() {
        final var keyManager = new InMemoryKeyProvider();
        keyManager.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        final CipherTextCodec codec = new KhezyCipherTextCodec();
        final var crypto = new DefaultEncryptor(keyManager, new AesGcmBackend(),
                new InMemoryEncryptionCounter());
        final StringEncryptor initial = new DefaultStringEncryptor(crypto, codec);
        final var taskStore = new SpringA2ATaskStore(taskRepository, initial);
        final var pushStore = new SpringA2APushNotificationConfigStore(pushRepository, initial);

        final var taskId = "rotation-task";
        taskStore.save(Task.builder()
                .id(taskId)
                .contextId("ctx-rotation")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null,
                        OffsetDateTime.parse("2026-01-01T00:00:00Z")))
                .history(new Message(Message.Role.ROLE_USER, List.of(new TextPart(SECRET)),
                        "msg-rotation", "ctx-rotation", taskId, null, null, null))
                .build(), false);
        pushStore.setInfo(TaskPushNotificationConfig.builder()
                .id("cfg-rotation")
                .taskId(taskId)
                .url("https://example.com/rotation")
                .token(SECRET)
                .build());

        assertThat(keyIdOf(rawTask(taskId))).isEqualTo("k1");
        assertThat(keyIdOf(rawPush(taskId, "cfg-rotation"))).isEqualTo("k1");

        keyManager.create(KeyCreateRequest.builder().id("k2").activateNow(false).build());
        keyManager.swapActive("k2", KeyStatus.DECRYPT_ONLY,
                new KeyTransitionContext("test", "rotation", Instant.now()));
        assertThat(crypto.activeKeyId()).isEqualTo("k2");

        // Old rows remain readable after the active key changes.
        assertThat(taskStore.get(taskId)).isNotNull();
        assertThat(pushStore.getInfo(new ListTaskPushNotificationConfigsParams(taskId))
                .configs()).hasSize(1);

        final var reEncryptor = new DefaultReEncryptor(crypto, codec);
        final var executor = new ReEncryptionExecutor();
        final var taskReport = executor.run(
                oneShot(new StoredRow(taskId, rawTask(taskId), null)),
                taskSink(), reEncryptor, A2ACipherContexts.task(taskId), 10);
        final var pushReport = executor.run(
                oneShot(new StoredRow(taskId, rawPush(taskId, "cfg-rotation"), "cfg-rotation")),
                pushSink(), reEncryptor,
                A2ACipherContexts.pushConfig(taskId, "cfg-rotation"), 10);

        assertThat(taskReport.rewritten()).isEqualTo(1);
        assertThat(pushReport.rewritten()).isEqualTo(1);
        assertThat(keyIdOf(rawTask(taskId))).isEqualTo("k2");
        assertThat(keyIdOf(rawPush(taskId, "cfg-rotation"))).isEqualTo("k2");

        // The AAD is unchanged by migration: the stores still decrypt with the row context.
        final var loadedTask = taskStore.get(taskId);
        assertThat(loadedTask).isNotNull();
        assertThat(((TextPart) loadedTask.history().get(0).parts().get(0)).text())
                .isEqualTo(SECRET);
        assertThat(pushStore.getInfo(new ListTaskPushNotificationConfigsParams(taskId))
                .configs().get(0).token()).isEqualTo(SECRET);

        assertThatThrownBy(() -> crypto.decrypt(
                codec.decode(rawTask(taskId)),
                A2ACipherContexts.task("a-different-row")))
                .isInstanceOf(CiphertextIntegrityException.class);
    }

    private String rawTask(final String taskId) {
        return jdbc.queryForObject("select task_data from a2a_tasks where task_id = ?",
                String.class, taskId);
    }

    private String rawPush(final String taskId, final String configId) {
        return jdbc.queryForObject(
                "select task_data from a2a_push_notification_configs"
                        + " where task_id = ? and config_id = ?",
                String.class, taskId, configId);
    }

    private static String keyIdOf(final String envelope) {
        return envelope.split(":", -1)[3];
    }

    private static ReEncryptionSource<StoredRow> oneShot(final StoredRow row) {
        final var first = new AtomicBoolean(true);
        return () -> first.getAndSet(false) ? List.of(row) : List.of();
    }

    private ReEncryptionSink<StoredRow> taskSink() {
        return row -> jdbc.update("update a2a_tasks set task_data = ? where task_id = ?",
                row.ciphertext(), row.taskId());
    }

    private ReEncryptionSink<StoredRow> pushSink() {
        return row -> jdbc.update(
                "update a2a_push_notification_configs set task_data = ?"
                        + " where task_id = ? and config_id = ?",
                row.ciphertext(), row.taskId(), row.configId());
    }

    /**
     * Row adapter used by the crypto library's batch migration helper.
     */
    private record StoredRow(String taskId, String ciphertext, String configId)
            implements ReEncryptionRecord<StoredRow> {

        @Override
        public StoredRow withCiphertext(final String value) {
            return new StoredRow(taskId, value, configId);
        }
    }
}
