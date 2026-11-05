package io.github.khezyapp.a2a.coredef.client.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.khezyapp.a2a.coredef.client.TestFixtures;

class InMemoryA2AConversationStoreTest {

    @Test
    void shouldKeyEntriesByConversationAgentAndSkill() {
        final var store = new InMemoryA2AConversationStore();
        store.recordInterrupted("conv-1", "agent-a", "skill-a", "ctx", "task");

        assertTrue(store.ref("conv-1", "agent-a", "skill-a").hasTask());
        assertFalse(store.ref("conv-2", "agent-a", "skill-a").hasTask());
        assertFalse(store.ref("conv-1", "agent-b", "skill-a").hasTask());
        assertFalse(store.ref("conv-1", "agent-a", "skill-b").hasTask());
    }

    @Test
    void nullSkillShouldBeTreatedAsNoSkill() {
        final var store = new InMemoryA2AConversationStore();
        store.recordInterrupted("conv-1", "agent-a", null, "ctx", "task");

        assertEquals("task", store.ref("conv-1", "agent-a", "").taskId());
    }

    @Test
    void settledEntryShouldKeepContextAndDropTask() {
        final var store = new InMemoryA2AConversationStore();
        store.recordInterrupted("conv-1", "agent-a", "skill-a", "ctx", "task");

        store.recordSettled("conv-1", "agent-a", "skill-a", "ctx");

        final var ref = store.ref("conv-1", "agent-a", "skill-a");
        assertTrue(ref.hasContext());
        assertFalse(ref.hasTask());
    }

    @Test
    void expiredEntryShouldBeDropped() {
        final var clock = new TestFixtures.MutableClock(Instant.parse("2026-10-19T00:00:00Z"));
        final var store = new InMemoryA2AConversationStore(Duration.ofSeconds(1), 10, clock);
        store.recordInterrupted("conv-1", "agent-a", "skill-a", "ctx", "task");

        clock.advance(Duration.ofSeconds(2));

        assertFalse(store.ref("conv-1", "agent-a", "skill-a").hasContext());
        assertEquals(0, store.size());
    }

    @Test
    void leastRecentlyUsedEntryShouldBeEvicted() {
        final var store = new InMemoryA2AConversationStore(Duration.ofHours(1), 2);
        store.recordInterrupted("conv-1", "agent-a", "skill-a", "ctx-a", "task-a");
        store.recordInterrupted("conv-1", "agent-a", "skill-b", "ctx-b", "task-b");

        store.ref("conv-1", "agent-a", "skill-a");
        store.recordInterrupted("conv-1", "agent-a", "skill-c", "ctx-c", "task-c");

        assertTrue(store.ref("conv-1", "agent-a", "skill-a").hasTask());
        assertFalse(store.ref("conv-1", "agent-a", "skill-b").hasTask());
        assertTrue(store.ref("conv-1", "agent-a", "skill-c").hasTask());
        assertEquals(2, store.size());
    }

    @Test
    void clearConversationShouldOnlyRemoveThatConversation() {
        final var store = new InMemoryA2AConversationStore();
        store.recordInterrupted("conv-1", "agent-a", "skill-a", "ctx-1", "task-1");
        store.recordInterrupted("conv-2", "agent-a", "skill-a", "ctx-2", "task-2");

        store.clearConversation("conv-1");

        assertFalse(store.ref("conv-1", "agent-a", "skill-a").hasContext());
        assertTrue(store.ref("conv-2", "agent-a", "skill-a").hasContext());
    }

    @Test
    void clearShouldRemoveEverything() {
        final var store = new InMemoryA2AConversationStore();
        store.recordInterrupted("conv-1", "agent-a", "skill-a", "ctx-1", "task-1");
        store.recordInterrupted("conv-2", "agent-a", "skill-a", "ctx-2", "task-2");

        store.clear();

        assertEquals(0, store.size());
    }
}
