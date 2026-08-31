package io.github.khezyapp.a2a.coredef.store;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.Test;

class InMemoryTaskStoreConcurrencyTest {

    private static final int THREADS = 8;
    private static final int TASKS_PER_THREAD = 200;

    @Test
    void concurrentSavesShouldNotLoseTasks() throws Exception {
        final var store = new InMemoryTaskStore();
        final var latch = new CountDownLatch(THREADS);
        final var pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int t = 0; t < THREADS; t++) {
                final int threadIndex = t;
                pool.submit(() -> {
                    try {
                        for (int i = 0; i < TASKS_PER_THREAD; i++) {
                            final String id = "task-" + threadIndex + "-" + i;
                            store.save(task(id));
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
            for (int t = 0; t < THREADS; t++) {
                for (int i = 0; i < TASKS_PER_THREAD; i++) {
                    assertTrue(store.find("task-" + t + "-" + i).isPresent());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Task task(final String id) {
        return Task.builder()
                .id(id)
                .contextId(id)
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                .history(List.of(
                        Message.builder()
                                .role(Message.Role.ROLE_USER)
                                .parts(new TextPart("hi"))
                                .messageId("m")
                                .build()))
                .build();
    }
}
