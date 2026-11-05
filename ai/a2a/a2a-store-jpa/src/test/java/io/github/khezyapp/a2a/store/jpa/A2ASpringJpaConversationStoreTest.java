package io.github.khezyapp.a2a.store.jpa;

import java.time.Duration;

import io.github.khezyapp.a2a.store.jpa.conversation.A2AConversationId;
import io.github.khezyapp.a2a.store.jpa.conversation.A2AConversationRepository;
import io.github.khezyapp.a2a.store.jpa.conversation.SpringA2AConversationStore;
import io.github.khezyapp.a2a.testapp.TestStoreApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the JPA conversation store: per agent/skill isolation, settled
 * semantics, clearing, expiry and purging.
 */
@SpringBootTest(classes = TestStoreApplication.class)
class A2ASpringJpaConversationStoreTest {

    @Autowired
    private SpringA2AConversationStore store;

    @Autowired
    private A2AConversationRepository repository;

    @Test
    void recordsAndReadsPendingTask() {
        store.recordPending("conv-1", "agent-a", "skill-a", "ctx-1", "task-1");

        final var ref = store.ref("conv-1", "agent-a", "skill-a");

        assertThat(ref.contextId()).isEqualTo("ctx-1");
        assertThat(ref.taskId()).isEqualTo("task-1");
        assertThat(store.ref("conv-1", "agent-a", "other").hasContext()).isFalse();
        assertThat(store.ref("conv-1", "agent-b", "skill-a").hasContext()).isFalse();
        assertThat(store.ref("unknown", "agent-a", "skill-a").hasTask()).isFalse();
    }

    @Test
    void settledKeepsContextAndDropsTask() {
        store.recordInterrupted("conv-2", "agent-a", "", "ctx-2", "task-2");

        store.recordSettled("conv-2", "agent-a", "", "ctx-2");

        final var ref = store.ref("conv-2", "agent-a", "");
        assertThat(ref.contextId()).isEqualTo("ctx-2");
        assertThat(ref.hasTask()).isFalse();
    }

    @Test
    void blankReferenceRemovesEntry() {
        store.recordPending("conv-3", "agent-a", "", "ctx-3", "task-3");

        store.recordSettled("conv-3", "agent-a", "", null);

        assertThat(store.ref("conv-3", "agent-a", "").hasContext()).isFalse();
        assertThat(repository.findById(new A2AConversationId("conv-3", "agent-a", ""))).isEmpty();
    }

    @Test
    void clearsOneConversationOnly() {
        store.recordPending("conv-4", "agent-a", "", "ctx-4", "task-4");
        store.recordPending("conv-4", "agent-b", "", "ctx-4", "task-4b");
        store.recordPending("conv-5", "agent-a", "", "ctx-5", "task-5");

        store.clearConversation("conv-4");

        assertThat(store.ref("conv-4", "agent-a", "").hasTask()).isFalse();
        assertThat(store.ref("conv-4", "agent-b", "").hasTask()).isFalse();
        assertThat(store.ref("conv-5", "agent-a", "").taskId()).isEqualTo("task-5");
    }

    @Test
    void expiredEntriesAreNotReturnedAndCanBePurged() throws InterruptedException {
        final var shortLived = new SpringA2AConversationStore(repository, Duration.ofMillis(20));
        shortLived.recordPending("conv-6", "agent-a", "", "ctx-6", "task-6");
        Thread.sleep(40L);

        assertThat(shortLived.ref("conv-6", "agent-a", "").hasTask()).isFalse();
        assertThat(shortLived.purgeExpired()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new SpringA2AConversationStore(repository, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl must be positive");
    }
}
