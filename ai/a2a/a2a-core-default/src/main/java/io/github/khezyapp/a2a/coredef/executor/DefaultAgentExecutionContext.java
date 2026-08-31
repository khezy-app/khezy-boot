package io.github.khezyapp.a2a.coredef.executor;

import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import io.github.khezyapp.a2a.core.executor.CancellationToken;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;

/** Immutable default {@link AgentExecutionContext} built by the dispatcher. */
public final class DefaultAgentExecutionContext implements AgentExecutionContext {

    private final Message incoming;
    private final Optional<Task> currentTask;
    private final CallerIdentity caller;
    private final Map<String, Object> attributes;
    private final CancellationToken cancellationToken;

    public DefaultAgentExecutionContext(final Message incoming,
                                        final Optional<Task> currentTask,
                                        final CallerIdentity caller,
                                        final Map<String, Object> attributes,
                                        final CancellationToken cancellationToken) {
        this.incoming = Objects.requireNonNull(incoming, "incoming");
        this.currentTask = Objects.requireNonNull(currentTask, "currentTask");
        this.caller = Objects.requireNonNull(caller, "caller");
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
    }

    @Override
    public Message incoming() {
        return incoming;
    }

    @Override
    public Optional<Task> currentTask() {
        return currentTask;
    }

    @Override
    public CallerIdentity caller() {
        return caller;
    }

    @Override
    public Map<String, Object> attributes() {
        return attributes;
    }

    @Override
    public CancellationToken cancellationToken() {
        return cancellationToken;
    }
}
