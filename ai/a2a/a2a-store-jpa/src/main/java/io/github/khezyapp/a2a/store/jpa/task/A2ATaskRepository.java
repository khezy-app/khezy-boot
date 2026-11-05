package io.github.khezyapp.a2a.store.jpa.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data repository for {@link A2ATaskEntity}.
 *
 * <p>Dynamic filters and keyset pagination use {@link JpaSpecificationExecutor}. The scalar
 * scope lookup avoids deserializing {@code task_data} during authorization checks.</p>
 */
public interface A2ATaskRepository extends JpaRepository<A2ATaskEntity, String>,
        JpaSpecificationExecutor<A2ATaskEntity> {

    /**
     * Reads the denormalized agent scope for a task.
     *
     * @param taskId the task identifier
     * @return the recorded scope, or empty when the task/scope is absent
     */
    @Query("select e.agentScope from A2ATaskEntity e where e.id = :taskId")
    Optional<String> findAgentScopeByTaskId(@Param("taskId") String taskId);
}
