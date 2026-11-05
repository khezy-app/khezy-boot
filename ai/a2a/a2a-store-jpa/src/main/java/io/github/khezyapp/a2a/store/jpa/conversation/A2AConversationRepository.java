package io.github.khezyapp.a2a.store.jpa.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Spring Data repository for {@link A2AConversationEntity}.
 */
public interface A2AConversationRepository
        extends JpaRepository<A2AConversationEntity, A2AConversationId> {

    /**
     * Deletes every entry for a host conversation.
     *
     * @param conversationId the host conversation id
     * @return the number of deleted rows
     */
    @Modifying
    @Transactional
    @Query("delete from A2AConversationEntity e where e.id.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") String conversationId);

    /**
     * Deletes entries that expired before the given instant.
     *
     * @param instant the cutoff
     * @return the number of deleted rows
     */
    @Modifying
    @Transactional
    @Query("delete from A2AConversationEntity e where e.expiresAt < :instant")
    int deleteExpiredBefore(@Param("instant") Instant instant);
}
