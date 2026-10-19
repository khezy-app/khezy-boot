# A2A-02 — `a2a-core`: port interfaces, records, error taxonomy

**Prerequisite task files:** `00-plan-overview.md`, `01-a2a-skeleton.md` (module exists, compiles).

**Do NOT modify** other modules. All code below lives in `ai/a2a/a2a-core/src/main/java`.

---

## 1. Goal

Define the full set of A2A contract interfaces (ports) typed on the SDK spec model
(`org.a2aproject.sdk.spec.*`), plus the error taxonomy. `a2a-core` must have **no
dependency** on anything but `a2a-java-sdk-spec`. This module is the shared language
for all later tasks — signatures here are contract.

## 2. Context digest

- Design doc §4 (Contract interfaces) is the source of truth; adapt its examples to the **corrected** SDK packages from `00` §3.
- `EventKind`, `Message`, `Task`, `TaskState`, `TaskStatus`, `Artifact`, `AgentCard`, `MessageSendParams`, `TaskQueryParams`, `TaskIdParams`, `A2AError` all live in `org.a2aproject.sdk.spec.*`.
- Records/classes must satisfy checkstyle: `final` params/locals, `final var`, no star imports, 120-char lines, braces everywhere, `Objects.requireNonNull`.

## 3. Package layout

```
io.github.khezyapp.a2a.core
├── error/       A2ACoreException, TaskNotCancelableException, TaskNotFoundException,
│                TaskCancelledException, TaskStoreException
├── executor/    A2AAgentExecutor, A2AStreamingAgentExecutor, AbstractA2AAgentExecutor,
│                AgentEventSink, AgentExecutionContext, CancellationToken, CallerIdentity
├── store/       A2ATaskStore, A2AEventQueue, Subscription, PushNotificationSender
├── dispatch/    A2ARequestDispatcher, CallContext
├── client/      A2ARemoteAgentClient, A2AStreamingRemoteAgentClient
└── identity/    CallerIdentityResolver
```

Delete the placeholder `A2ACoreMarker` created in A2A-01 (no longer needed).

## 4. Contracts to implement

### 4.1 `io.github.khezyapp.a2a.core.error` — error taxonomy

```java
package io.github.khezyapp.a2a.core.error;

/** Base unchecked exception for all A2A core errors. */
public class A2ACoreException extends RuntimeException {

    public A2ACoreException(final String message) {
        super(message);
    }

    public A2ACoreException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
```

```java
package io.github.khezyapp.a2a.core.error;

/** Thrown when an agent does not support cancelling a task (maps to the spec's TaskNotCancelableError). */
public class TaskNotCancelableException extends A2ACoreException {

    private final String taskId;

    public TaskNotCancelableException(final String taskId) {
        super("Task is not cancelable: " + taskId);
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }
}
```

```java
package io.github.khezyapp.a2a.core.error;

/** Thrown when a requested task does not exist (maps to the spec's TaskNotFoundError). */
public class TaskNotFoundException extends A2ACoreException {

    private final String taskId;

    public TaskNotFoundException(final String taskId) {
        super("Task not found: " + taskId);
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }
}
```

```java
package io.github.khezyapp.a2a.core.error;

/** Thrown when cooperative cancellation has been requested for the current execution. */
public class TaskCancelledException extends A2ACoreException {

    public TaskCancelledException(final String taskId) {
        super("Task cancelled cooperatively: " + taskId);
    }
}
```

```java
package io.github.khezyapp.a2a.core.error;

/** Thrown when the task store fails to persist or load a task. */
public class TaskStoreException extends A2ACoreException {

    public TaskStoreException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
```

### 4.2 `io.github.khezyapp.a2a.core.executor` — execution contracts

Follow **Spring AI's dual-interface pattern** (blocking `execute` / streaming `stream`).

```java
package io.github.khezyapp.a2a.core.executor;

import io.github.khezyapp.a2a.core.error.TaskNotCancelableException;
import org.a2aproject.sdk.spec.EventKind;

/**
 * Blocking agent execution contract (analogous to {@code ChatModel#call}).
 * Framework-neutral: implementations may be backed by ChatClient, a plain service, anything.
 */
public interface A2AAgentExecutor {

    /** Handle a message send synchronously and return the final event/result. */
    EventKind execute(AgentExecutionContext context);

    /**
     * Cooperatively cancel a task. Default: unsupported — the caller must handle
     * {@link TaskNotCancelableException}.
     */
    default void cancel(final String taskId) {
        throw new TaskNotCancelableException(taskId);
    }
}
```

```java
package io.github.khezyapp.a2a.core.executor;

/**
 * Streaming agent execution contract (analogous to {@code StreamingChatModel#stream}).
 * Emits incremental events (status changes, partial chunks, artifacts) onto the sink.
 */
public interface A2AStreamingAgentExecutor {

    void stream(AgentExecutionContext context, AgentEventSink sink);
}
```

```java
package io.github.khezyapp.a2a.core.executor;

import org.a2aproject.sdk.spec.A2AError;

/**
 * Convenience bridge so blocking-only agents get streaming for free: {@link #stream}
 * runs {@link #execute} and emits its result as a single completed event.
 * Full streaming agents override {@link #stream} directly.
 */
public abstract class AbstractA2AAgentExecutor implements A2AAgentExecutor, A2AStreamingAgentExecutor {

    @Override
    public void stream(final AgentExecutionContext context, final AgentEventSink sink) {
        try {
            sink.completed(execute(context));
        } catch (final A2AError e) {
            sink.failed(e);
        }
    }
}
```

> Note: catch `A2AError` (the SDK error base, a `RuntimeException`). Do not catch bare `Exception` here — see `00` §3.2.

```java
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
```

```java
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
```

```java
package io.github.khezyapp.a2a.core.executor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;

/** Identity of the caller that initiated an A2A execution. */
public record CallerIdentity(Optional<String> subject, Set<String> scopes, Map<String, Object> claims) {

    public CallerIdentity {
        subject = Objects.requireNonNull(subject, "subject");
        scopes = Objects.requireNonNull(scopes, "scopes");
        claims = Objects.requireNonNull(claims, "claims");
    }

    public static CallerIdentity anonymous() {
        return new CallerIdentity(Optional.empty(), Set.of(), Map.of());
    }
}
```

```java
package io.github.khezyapp.a2a.core.executor;

import io.github.khezyapp.a2a.core.error.TaskCancelledException;

/** Cooperative cancellation support threaded through execution. */
public interface CancellationToken {

    boolean isCancellationRequested();

    void requestCancellation();

    default void checkCancellation() {
        if (isCancellationRequested()) {
            throw new TaskCancelledException("execution");
        }
    }
}
```

### 4.3 `io.github.khezyapp.a2a.core.store` — persistence & infrastructure ports

```java
package io.github.khezyapp.a2a.core.store;

import java.util.Optional;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;

/** Task persistence port. Backends: in-memory (A2A-03), later Redis/JDBC. */
public interface A2ATaskStore {

    void save(Task task);

    Optional<Task> find(String taskId);

    void updateState(String taskId, TaskState state);
}
```

```java
package io.github.khezyapp.a2a.core.store;

import java.util.function.Consumer;
import org.a2aproject.sdk.spec.EventKind;

/** Per-context event stream port. */
public interface A2AEventQueue {

    void publish(String contextId, EventKind event);

    Subscription subscribe(String contextId, Consumer<EventKind> listener);
}
```

```java
package io.github.khezyapp.a2a.core.store;

/** Handle for an active event subscription. */
public interface Subscription extends AutoCloseable {

    void unsubscribe();

    @Override
    default void close() {
        unsubscribe();
    }
}
```

```java
package io.github.khezyapp.a2a.core.store;

import org.a2aproject.sdk.spec.Task;

/** Port for sending push notifications when a task reaches a noteworthy state. */
public interface PushNotificationSender {

    void send(Task task);
}
```

### 4.4 `io.github.khezyapp.a2a.core.dispatch` — transport-independent dispatch

```java
package io.github.khezyapp.a2a.core.dispatch;

import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import java.util.Map;
import java.util.Optional;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;

/**
 * Behavioral re-implementation of the SDK's request handler, owned by us: adds identity
 * and cancellation hooks, and is testable without any transport.
 */
public interface A2ARequestDispatcher {

    EventKind onMessageSend(MessageSendParams params, CallContext ctx);

    void onMessageStream(MessageSendParams params, AgentEventSink sink, CallContext ctx);

    Optional<Task> onGetTask(TaskQueryParams query, CallContext ctx);

    Task onCancelTask(TaskIdParams params, CallContext ctx);

    AgentCard agentCard(CallContext ctx);
}
```

```java
package io.github.khezyapp.a2a.core.dispatch;

import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import java.util.Map;

/** Transport-level call context: who is calling and with what attributes. */
public interface CallContext {

    CallerIdentity caller();

    Map<String, Object> attributes();
}
```

### 4.5 `io.github.khezyapp.a2a.core.client` — client-side ports

```java
package io.github.khezyapp.a2a.core.client;

import java.net.URI;
import java.time.Duration;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;

/** Client port for calling remote A2A agents. Shared by host agents and subagent bridges. */
public interface A2ARemoteAgentClient extends AutoCloseable {

    AgentCard discover(URI baseUrl);

    EventKind sendMessage(Message message, Duration timeout);
}
```

```java
package io.github.khezyapp.a2a.core.client;

import java.util.function.Consumer;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;

/** Streaming variant of the remote client port. */
public interface A2AStreamingRemoteAgentClient {

    void streamMessage(Message message, Consumer<EventKind> listener);
}
```

### 4.6 `io.github.khezyapp.a2a.core.identity` — identity resolution port

```java
package io.github.khezyapp.a2a.core.identity;

import io.github.khezyapp.a2a.core.executor.CallerIdentity;

/**
 * Resolves the caller identity for the current call. Framework-neutral: the starter's
 * implementation reads Spring Security's {@code SecurityContextHolder}; custom
 * implementations may use thread-locals or other contextual state.
 */
public interface CallerIdentityResolver {

    CallerIdentity resolve();
}
```

## 5. Tests (`src/test/java`)

No Spring context. Straight JUnit 5 assertions.

1. `CallerIdentityTest` — `anonymous()` returns empty subject, empty scopes/claims; null components rejected (assert `NullPointerException`).
2. `ErrorTaxonomyTest` — each exception carries the right taskId/message; `TaskNotCancelableException` is an `A2ACoreException`.
3. `AbstractA2AAgentExecutorTest` — subclass implementing only `execute`:
   - streaming fallback emits exactly one `completed` event and no `failed`;
   - when `execute` throws `A2AError`, `stream` emits `failed` and nothing else.
   (Implement a tiny in-test `AgentEventSink` recording calls; use `Arrays.asList(...)` etc. — remember: no `stream()` on arrays, use `Arrays.stream`.)

## 6. Verification

```bash
./gradlew :ai:a2a:a2a-core:test
./gradlew :ai:a2a:a2a-core:checkstyleMain :ai:a2a:a2a-core:checkstyleTest
./gradlew :ai:a2a:a2a-core:check
```

## 7. Handoff note for the next task

> Record this handoff by appending an entry to `99-handoff-log.md` (template inside;
> protocol in `00` §6.7). Chat is NOT the handoff channel.

List every interface/record created (package + name). Flag anything that had to differ
from §4 (e.g. SDK signature surprises) so downstream tasks can rely on the real shapes.
Then run `graphify update .`.

