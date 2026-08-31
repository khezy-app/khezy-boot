package io.github.khezyapp.a2a.core.executor;

import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskState;

/** Callback sink receiving incremental execution events. */
public interface AgentEventSink {

    void statusChanged(TaskState state, String message);

    void artifactAdded(Artifact artifact);

    void chunk(Part delta);

    void completed(EventKind result);

    void failed(A2AError error);
}
