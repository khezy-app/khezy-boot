package io.github.khezyapp.a2a.store.jpa.push;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data repository for {@link A2APushNotificationConfigEntity}.
 *
 * <p>Listing uses keyset (cursor) pagination ordered by {@code (created_at DESC, config_id ASC)}.
 * Existing rows without a timestamp are ordered using a sentinel instant.</p>
 */
public interface A2APushNotificationConfigRepository
        extends JpaRepository<A2APushNotificationConfigEntity, A2APushConfigId> {

    /**
     * Counts the configs registered for a task.
     *
     * @param taskId the task identifier
     * @return the config count
     */
    long countByIdTaskId(String taskId);

    /**
     * Returns the first page of configs for a task.
     *
     * @param taskId the task identifier
     * @param nullSentinel the instant used for rows with no timestamp
     * @param pageable the page request
     * @return the page rows
     */
    @Query("""
            select c from A2APushNotificationConfigEntity c
            where c.id.taskId = :taskId
            order by coalesce(c.createdAt, :nullSentinel) desc, c.id.configId asc
            """)
    List<A2APushNotificationConfigEntity> findFirstPage(
            @Param("taskId") String taskId,
            @Param("nullSentinel") Instant nullSentinel,
            Pageable pageable);

    /**
     * Returns the page after a keyset cursor.
     *
     * @param taskId the task identifier
     * @param nullSentinel the instant used for rows with no timestamp
     * @param tokenTimestamp the cursor timestamp
     * @param tokenId the cursor config id
     * @param pageable the page request
     * @return the page rows
     */
    @Query("""
            select c from A2APushNotificationConfigEntity c
            where c.id.taskId = :taskId
              and (coalesce(c.createdAt, :nullSentinel) < :tokenTimestamp
                   or (coalesce(c.createdAt, :nullSentinel) = :tokenTimestamp
                       and c.id.configId > :tokenId))
            order by coalesce(c.createdAt, :nullSentinel) desc, c.id.configId asc
            """)
    List<A2APushNotificationConfigEntity> findAfter(
            @Param("taskId") String taskId,
            @Param("nullSentinel") Instant nullSentinel,
            @Param("tokenTimestamp") Instant tokenTimestamp,
            @Param("tokenId") String tokenId,
            Pageable pageable);

    /**
     * Returns the config id and stored protocol version for a task.
     *
     * @param taskId the task identifier
     * @return rows of {@code [configId, protocolVersion]}
     */
    @Query("""
            select c.id.configId, c.protocolVersion from A2APushNotificationConfigEntity c
            where c.id.taskId = :taskId and c.protocolVersion is not null
            """)
    List<Object[]> findProtocolVersions(@Param("taskId") String taskId);
}
