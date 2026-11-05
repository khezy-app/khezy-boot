package io.github.khezyapp.a2a.store.jpa;

import io.github.khezyapp.a2a.core.server.A2AContextKeys;
import io.github.khezyapp.a2a.store.jpa.push.A2APushNotificationConfigRepository;
import io.github.khezyapp.a2a.store.jpa.push.SpringA2APushNotificationConfigStore;
import io.github.khezyapp.a2a.store.jpa.task.A2ATaskRepository;
import io.github.khezyapp.a2a.store.jpa.task.JpaTaskScopeLocator;
import io.github.khezyapp.a2a.store.jpa.task.SpringA2ATaskStore;
import io.github.khezyapp.a2a.testapp.AppNote;
import io.github.khezyapp.a2a.testapp.AppNoteRepository;
import io.github.khezyapp.a2a.testapp.FinalizedEventRecorder;
import io.github.khezyapp.a2a.testapp.TestStoreApplication;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTasksParams;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the JPA task, scope and push-config stores, including the additive
 * entity scan and the finalized-task event.
 */
@SpringBootTest(classes = TestStoreApplication.class)
class A2ASpringJpaStoreTest {

    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Autowired
    private SpringA2ATaskStore taskStore;

    @Autowired
    private SpringA2APushNotificationConfigStore pushStore;

    @Autowired
    private JpaTaskScopeLocator scopeLocator;

    @Autowired
    private A2ATaskRepository taskRepository;

    @Autowired
    private A2APushNotificationConfigRepository pushRepository;

    @Autowired
    private AppNoteRepository appNoteRepository;

    @Autowired
    private StringEncryptor encryptor;

    @Autowired
    private FinalizedEventRecorder finalizedEvents;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesReadsUpdatesAndDeletesTasks() {
        final var id = "task-crud";
        final var message = new Message(Message.Role.ROLE_USER,
                List.of(new TextPart("hello")), "msg-1", "ctx-crud", id, null, null, null);
        taskStore.save(Task.builder()
                .id(id)
                .contextId("ctx-crud")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, BASE))
                .history(message)
                .build(), false);

        final var loaded = taskStore.get(id);
        assertThat(loaded).isNotNull();
        assertThat(loaded.contextId()).isEqualTo("ctx-crud");
        assertThat(loaded.status().state()).isEqualTo(TaskState.TASK_STATE_WORKING);
        assertThat(loaded.history()).hasSize(1);

        taskStore.save(Task.builder()
                .id(id)
                .contextId("ctx-crud")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING,
                        new Message(Message.Role.ROLE_AGENT, List.of(new TextPart("working")),
                                "msg-2", "ctx-crud", id, null, null, null),
                        BASE.plusSeconds(1)))
                .history(message)
                .build(), false);

        final var updated = taskStore.get(id);
        assertThat(updated).isNotNull();
        assertThat(updated.status().timestamp()).isEqualTo(BASE.plusSeconds(1));
        assertThat(taskRepository.count()).isPositive();

        taskStore.delete(id);
        assertThat(taskStore.get(id)).isNull();
    }

    @Test
    void listsWithKeysetPaginationAndFilters() {
        final var context = "ctx-page";
        taskStore.save(task("page-a", context, TaskState.TASK_STATE_WORKING, BASE), false);
        taskStore.save(task("page-b", context, TaskState.TASK_STATE_WORKING,
                BASE.plusSeconds(1)), false);
        taskStore.save(task("page-c", context, TaskState.TASK_STATE_COMPLETED,
                BASE.plusSeconds(2)), false);

        final var first = taskStore.list(
                ListTasksParams.builder().contextId(context).pageSize(2).build(), null);
        assertThat(first.tasks()).extracting(Task::id).containsExactly("page-c", "page-b");
        assertThat(first.totalSize()).isEqualTo(3);
        assertThat(first.nextPageToken()).isNotNull();

        final var second = taskStore.list(ListTasksParams.builder()
                .contextId(context)
                .pageSize(2)
                .pageToken(first.nextPageToken())
                .build(), null);
        assertThat(second.tasks()).extracting(Task::id).containsExactly("page-a");
        assertThat(second.nextPageToken()).isNull();

        final var completed = taskStore.list(ListTasksParams.builder()
                .contextId(context)
                .status(TaskState.TASK_STATE_COMPLETED)
                .build(), null);
        assertThat(completed.tasks()).extracting(Task::id).containsExactly("page-c");

        final var after = taskStore.list(ListTasksParams.builder()
                .contextId(context)
                .statusTimestampAfter(BASE.plusSeconds(1).toInstant())
                .build(), null);
        assertThat(after.tasks()).extracting(Task::id).containsExactly("page-c");
    }

    @Test
    void trimsHistoryAndArtifacts() {
        final var id = "task-trim";
        final var first = message("msg-trim-1", id, "first");
        final var second = message("msg-trim-2", id, "second");
        taskStore.save(Task.builder()
                .id(id)
                .contextId("ctx-trim")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED, null, BASE))
                .history(first, second)
                .build(), false);

        final var result = taskStore.list(ListTasksParams.builder()
                .contextId("ctx-trim")
                .historyLength(1)
                .includeArtifacts(false)
                .build(), null);

        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().get(0).history()).hasSize(1);
        assertThat(result.tasks().get(0).artifacts()).isEmpty();
    }

    @Test
    void filtersByAgentScopeAndFailsClosedForBlankScope() {
        taskStore.save(Task.builder()
                .id("scope-a")
                .contextId("ctx-scope")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, BASE))
                .metadata(Map.of(A2AContextKeys.AGENT_SCOPE, "prod:scope-a"))
                .build(), false);
        taskStore.save(Task.builder()
                .id("scope-b")
                .contextId("ctx-scope")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, BASE))
                .metadata(Map.of(A2AContextKeys.AGENT_SCOPE, "prod:scope-b"))
                .build(), false);

        final var scopedA = taskStore.list(ListTasksParams.builder().build(),
                scopedContext("prod:scope-a"));
        assertThat(scopedA.tasks()).extracting(Task::id).containsExactly("scope-a");

        final var scopedB = taskStore.list(ListTasksParams.builder().build(),
                scopedContext("prod:scope-b"));
        assertThat(scopedB.tasks()).extracting(Task::id).containsExactly("scope-b");

        final var blankScope = taskStore.list(ListTasksParams.builder().build(),
                new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of()));
        assertThat(blankScope.tasks()).isEmpty();

        final var unscoped = taskStore.list(ListTasksParams.builder()
                .contextId("ctx-scope").build(), null);
        assertThat(unscoped.tasks()).extracting(Task::id)
                .containsExactlyInAnyOrder("scope-a", "scope-b");
    }

    @Test
    void preservesScopeWhenIncomingTaskHasNoScopeMetadata() {
        taskStore.save(Task.builder()
                .id("scope-preserved")
                .contextId("ctx-preserved")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, null, BASE))
                .metadata(Map.of(A2AContextKeys.AGENT_SCOPE, "prod:preserved"))
                .build(), false);
        assertThat(scopeLocator.findScope("scope-preserved")).isEqualTo("prod:preserved");

        taskStore.save(task("scope-preserved", "ctx-preserved",
                TaskState.TASK_STATE_COMPLETED, BASE.plusSeconds(1)), false);

        assertThat(scopeLocator.findScope("scope-preserved")).isEqualTo("prod:preserved");
        assertThat(scopeLocator.findScope("missing-task")).isNull();
    }

    @Test
    void appliesGracePeriodToActiveState() {
        final var id = "task-grace";
        taskStore.save(task(id, "ctx-grace", TaskState.TASK_STATE_COMPLETED, BASE), false);

        assertThat(taskStore.isTaskFinalized(id)).isTrue();
        assertThat(taskStore.isTaskActive(id)).isTrue();

        jdbc.update("update a2a_tasks set finalized_at = ? where task_id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), id);

        assertThat(taskStore.isTaskFinalized(id)).isTrue();
        assertThat(taskStore.isTaskActive(id)).isFalse();
        assertThat(taskStore.isTaskActive("missing-task")).isFalse();
        assertThat(taskStore.isTaskFinalized("missing-task")).isFalse();
    }

    @Test
    void publishesFinalizedEventOnceForLocalSaves() {
        finalizedEvents.clear();

        taskStore.save(task("event-local", "ctx-event", TaskState.TASK_STATE_WORKING, BASE), false);
        assertThat(finalizedEvents.taskIds()).isEmpty();

        taskStore.save(task("event-local", "ctx-event", TaskState.TASK_STATE_COMPLETED,
                BASE.plusSeconds(1)), false);
        taskStore.save(task("event-local", "ctx-event", TaskState.TASK_STATE_COMPLETED,
                BASE.plusSeconds(2)), false);
        taskStore.save(task("event-replicated", "ctx-event", TaskState.TASK_STATE_COMPLETED,
                BASE), true);

        assertThat(finalizedEvents.taskIds()).containsExactly("event-local");
    }

    @Test
    void createsUpdatesAndDeletesPushConfigs() {
        final var taskId = "push-crud";
        final var config = TaskPushNotificationConfig.builder()
                .id("cfg-1")
                .taskId(taskId)
                .url("https://example.com/hook")
                .token("secret-token")
                .build();
        pushStore.setInfo(config);

        final var loaded = pushStore.getInfo(new ListTaskPushNotificationConfigsParams(taskId));
        assertThat(loaded.configs()).hasSize(1);
        assertThat(loaded.configs().get(0).url()).isEqualTo("https://example.com/hook");
        assertThat(loaded.configs().get(0).token()).isEqualTo("secret-token");

        pushStore.setInfo(TaskPushNotificationConfig.builder(config)
                .url("https://example.com/hook-2").build());
        final var updated = pushStore.getInfo(new ListTaskPushNotificationConfigsParams(taskId));
        assertThat(updated.configs().get(0).url()).isEqualTo("https://example.com/hook-2");

        pushStore.deleteInfo(taskId, "cfg-1");
        pushStore.deleteInfo(taskId, "cfg-1");
        assertThat(pushStore.getInfo(new ListTaskPushNotificationConfigsParams(taskId)).configs())
                .isEmpty();
    }

    @Test
    void paginatesPushConfigs() {
        final var taskId = "push-pages";
        for (final var id : List.of("cfg-a", "cfg-b", "cfg-c")) {
            pushStore.setInfo(TaskPushNotificationConfig.builder()
                    .id(id)
                    .taskId(taskId)
                    .url("https://example.com/" + id)
                    .build());
        }

        final var first = pushStore.getInfo(
                new ListTaskPushNotificationConfigsParams(taskId, 2, "", null));
        assertThat(first.configs()).hasSize(2);
        assertThat(first.nextPageToken()).isNotNull();

        final var second = pushStore.getInfo(new ListTaskPushNotificationConfigsParams(
                taskId, 2, first.nextPageToken(), null));
        assertThat(second.configs()).hasSize(1);
        assertThat(second.nextPageToken()).isNull();

        final var ids = new HashSet<String>();
        first.configs().forEach(config -> ids.add(config.id()));
        second.configs().forEach(config -> ids.add(config.id()));
        assertThat(ids).containsExactlyInAnyOrder("cfg-a", "cfg-b", "cfg-c");
    }

    @Test
    void enforcesPerTaskPushConfigLimit() {
        final var limited = new SpringA2APushNotificationConfigStore(pushRepository, encryptor, 2);
        final var taskId = "push-limit";
        for (final var id : List.of("limit-a", "limit-b")) {
            limited.setInfo(TaskPushNotificationConfig.builder()
                    .id(id)
                    .taskId(taskId)
                    .url("https://example.com/" + id)
                    .build());
        }

        assertThatThrownBy(() -> limited.setInfo(TaskPushNotificationConfig.builder()
                .id("limit-c")
                .taskId(taskId)
                .url("https://example.com/limit-c")
                .build()))
                .isInstanceOf(InvalidParamsError.class);
    }

    @Test
    void omittedPushConfigIdIsCreateOnly() {
        final var taskId = "push-default";
        final var created = pushStore.setInfo(TaskPushNotificationConfig.builder()
                .taskId(taskId)
                .url("https://example.com/default")
                .build());
        assertThat(created.id()).isEqualTo(taskId);

        assertThatThrownBy(() -> pushStore.setInfo(TaskPushNotificationConfig.builder()
                .taskId(taskId)
                .url("https://example.com/replacement")
                .build()))
                .isInstanceOf(InvalidParamsError.class);

        final var updated = pushStore.setInfo(TaskPushNotificationConfig.builder()
                .id(taskId)
                .taskId(taskId)
                .url("https://example.com/updated-default")
                .build());
        assertThat(updated.url()).isEqualTo("https://example.com/updated-default");

        final var loaded = pushStore.getInfo(
                new ListTaskPushNotificationConfigsParams(taskId));
        assertThat(loaded.configs()).hasSize(1);
        assertThat(loaded.configs().get(0).url())
                .isEqualTo("https://example.com/updated-default");
    }

    @Test
    void storesAndReturnsProtocolVersions() {
        final var taskId = "push-protocol";
        pushStore.setInfo(TaskPushNotificationConfig.builder()
                .id("protocol-config")
                .taskId(taskId)
                .url("https://example.com/protocol")
                .build(), "1.4");

        assertThat(pushStore.getProtocolVersion(taskId, "protocol-config")).isEqualTo("1.4");
        assertThat(pushStore.getProtocolVersions(taskId))
                .containsEntry("protocol-config", "1.4");
        assertThat(pushStore.getProtocolVersion(taskId, "missing-config"))
                .isEqualTo("1.0");
    }

    @Test
    void coexistsWithApplicationEntitiesAndRepositories() {
        assertThat(taskRepository).isNotNull();
        assertThat(pushRepository).isNotNull();
        assertThat(taskStore).isNotNull();

        appNoteRepository.save(new AppNote("hello"));
        assertThat(appNoteRepository.count()).isPositive();
    }

    private static Task task(final String id,
                             final String contextId,
                             final TaskState state,
                             final OffsetDateTime timestamp) {
        return Task.builder()
                .id(id)
                .contextId(contextId)
                .status(new TaskStatus(state, null, timestamp))
                .build();
    }

    private static Message message(final String messageId,
                                   final String taskId,
                                   final String text) {
        return new Message(Message.Role.ROLE_USER, List.of(new TextPart(text)),
                messageId, "ctx-trim", taskId, null, null, null);
    }

    private static ServerCallContext scopedContext(final String scope) {
        return new ServerCallContext(UnauthenticatedUser.INSTANCE,
                Map.of(A2AContextKeys.AGENT_SCOPE, scope), Set.of());
    }
}
