package io.github.khezyapp.a2a.core.executor;

import java.util.Map;
import java.util.Optional;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;

/**
 * Execution context handed to agents — everything they are allowed to know.
 * Framework-neutral: the transport-level SDK context is wrapped by implementations.
 */
public interface AgentExecutionContext {

    Message incoming();

    Optional<Task> currentTask();

    CallerIdentity caller();

    Map<String, Object> attributes();

    CancellationToken cancellationToken();
}
