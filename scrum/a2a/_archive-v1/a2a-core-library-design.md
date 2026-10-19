# A2A Core Library — Design Plan

> Goal: define an **A2A contract layer** (port interfaces + default implementations) built **on top of the official A2A Java SDK (`io.github.a2asdk`)**. The SDK's spec types (`io.a2a.spec.*`) are our canonical domain model — every A2A-compatible stack implements the same spec, so depending on the SDK types keeps us wire-compatible by construction. Our contracts add the missing seams: execution, dispatch, persistence, client, and identity — without re-inventing the protocol model.

Status: draft v3 (base package + streaming pattern updated)
Date: 2026-08-22

> **Context for implementers (self-contained note):** this design was produced by analyzing the community baseline `spring-ai-community/spring-ai-a2a` 0.4.0-SNAPSHOT (source available at https://github.com/spring-ai-community/spring-ai-a2a). All references below to "community" classes (`AgentCardController`, `MessageController`, `TaskController`, `DefaultAgentExecutor`, `ChatClientExecutorHandler`, `spring-ai-a2a-server-autoconfigure`, `A2AClientServerIntegrationTests`) point to that upstream project — consult it directly when porting logic. References to local sample apps `a2a-server` (math agent) and `a2a-client` (host agent) in Phase 4 are throwaway validation apps from the analysis workspace; recreate equivalent minimal sample apps inside this repository when validating. This document is self-contained: no file outside this repo is required to start implementation.

### Design decisions (agreed)

1. **Depend on `io.github.a2asdk`, do not clone the spec model.** Other stacks implement the same A2A spec; the SDK types are the shared language. We define contract *interfaces* around these types, not replacement types for them. What we abstract is behavior (execution, storage, dispatch, client calls), never the wire model.
2. **Contribution upstream is deferred.** We validate the design against our own sample apps first (math agent server, host agent client). Once proven in real use, well-scoped pieces (e.g. auth-context support, streaming executor) can be contributed back to spring-ai-community as PRs. Until then we optimize for our own iteration speed, not upstream acceptance.

---

## 1. Analysis of the baseline (what community gives us today)

The community server is ~430 LOC of Spring code on top of `io.github.a2asdk:a2a-java-sdk-server-common`:

| Community class | Role | Coupling problem |
|---|---|---|
| `AgentCardController` | serves `/.well-known/agent-card.json` | depends on SDK `AgentCard` record directly |
| `MessageController` | JSON-RPC `message/send` endpoint | depends on SDK `RequestHandler`, `SendMessageRequest/Response`, builds `ServerCallContext` with **auth = null** (their TODO) |
| `TaskController` | task get/cancel endpoints | same as above |
| `DefaultAgentExecutor` | task lifecycle wrapper | hard-wired to `ChatClient` + blocking `String → String` handler |
| `ChatClientExecutorHandler` | user hook `(ChatClient, RequestContext) -> String` | leaks SDK `RequestContext` into user code |
| Auto-configuration | wires InMemory TaskStore / QueueManager / PushConfigStore into SDK `DefaultRequestHandler` | all beans are SDK types; switching stores/transports means replacing their beans |

Key limitations we want to fix:

1. **No contract seam** — controllers, executor, and autoconfig speak only SDK types. Swapping the A2A Java SDK (or supporting the gRPC/REST bindings of the same SDK) touches every class.
2. **Auth context TODO** — `ServerCallContext(null, Map.of(), Set.of())`; no way to propagate authenticated caller identity from HTTP layer to agent execution.
3. **Blocking, text-only executor** — no streaming (`message/stream`), no structured artifacts, no cancellation-aware cooperative execution.
4. **No client side contract** — subagent callers (e.g. `spring-ai-agent-utils`'s `A2ASubagentExecutor`) also bind directly to SDK types.

## 2. Architecture: contracts around the SDK, adapters for behavior

```
                        ┌────────────────────────────────────────────────┐
                        │              a2a-core (API)                    │
   application code ───▶│  port interfaces + defaults, typed on          │
                        │  io.a2a.spec.* (canonical model from the SDK)  │
                        │  NO deps on spring-ai / spring-boot            │
                        └───────┬───────────────────────┬────────────────┘
                                │ implements            │ implements
                 ┌──────────────▼──────┐     ┌──────────▼──────────────┐
                 │  a2a-core-default   │     │  a2a-spring-boot-starter│
                 │  in-memory TaskStore│     │  controllers, security  │
                 │  event queue,       │     │  identity, autoconfig   │
                 │  dispatcher         │     └─────────────────────────┘
                 └─────────────────────┘
                                 │
                 ┌───────────────▼───────────────┐
                 │  a2a-spring-ai (optional)     │
                 │  ChatClient executor adapter  │
                 └───────────────────────────────┘
```

Because the spec types come from the SDK, there is **no translation layer** — the old `a2a-spi-a2asdk` mapper module is dropped. The abstraction is over *behavior* only: any implementation of our ports (including ones not built on the Java SDK, e.g. a Kotlin or Quarkus-based remote agent behind the same spec) still speaks identical wire types.

### Module layout

| Module | Contents | Depends on |
|---|---|---|
| `a2a-core` | port interfaces + error taxonomy, all signatures use `io.a2a.spec.*` types | `a2a-java-sdk-spec` |
| `a2a-core-default` | `DefaultA2ARequestDispatcher`, `InMemoryTaskStore`, `InMemoryEventQueue` | `a2a-core` |
| `a2a-spring-ai` (optional) | `ChatClientExecutors.from(...)` adapter | `a2a-core`, spring-ai |
| `a2a-spring-boot-starter` | REST/JSON-RPC endpoints calling only our dispatcher, `CallerIdentityResolver`, autoconfiguration | `a2a-core`, spring-web/boot |

Future stacks plug in unchanged: e.g. an alternate `A2ATaskStore` backed by Redis/JDBC, or an `A2ARemoteAgentClient` using a different transport binding of the same spec.

## 3. Domain model — adopted from the SDK, not redefined

We do **not** define our own `AgentMessage`/`Part`/`Task` records. The canonical model is the SDK's:

- `io.a2a.spec.Message`, `TextPart`, `FilePart`, `DataPart`
- `io.a2a.spec.Task`, `TaskState`, `Artifact`
- `io.a2a.spec.AgentCard`, `AgentSkill`, `AgentCapabilities`

Rationale: any other A2A-compatible stack (regardless of language/transport) produces/consumes these exact shapes per spec. Re-modeling them would create a permanent translation tax and drift risk. Spec-version upgrades are handled by bumping the SDK version in one place.

## 4. Contract interfaces (the ports)

### 4.1 Server-side execution ports (replaces `AgentExecutor` + `ChatClientExecutorHandler`)

We follow **Spring AI's model split**: separate interfaces for normal (blocking) and streaming operations — mirroring how Spring AI defines `ChatModel#call(...)` and `StreamingChatModel#stream(...)`. Implementations that support both simply implement both interfaces (Spring AI's own `ChatModel` does exactly this); a default base class bridges streaming onto blocking so simple agents get streaming for free.

```java
package io.github.khezyapp.a2a.core.executor;

/**
 * Blocking agent execution contract (analogous to ChatModel#call).
 * Framework-neutral: implementations may be backed by ChatClient, Agent,
 * plain service, anything.
 */
public interface A2AAgentExecutor {

    /** Handle a message send synchronously and return the final event/result. */
    EventKind execute(AgentExecutionContext context);

    /**
     * Cooperatively cancel. Default: unsupported -> core cancels the task itself.
     */
    default void cancel(String taskId) {
        throw new TaskNotCancelableException(taskId);
    }
}

/**
 * Streaming agent execution contract (analogous to StreamingChatModel#stream).
 * Emits incremental events (status changes, partial chunks, artifacts) onto the
 * sink as they are produced.
 */
public interface A2AStreamingAgentExecutor {

    void stream(AgentExecutionContext context, AgentEventSink sink);
}
```

Convenience bridge so blocking-only agents don't reimplement streaming:

```java
/**
 * Default composition point: subclasses only implement {@link #execute};
 * {@link #stream} runs the blocking call and emits its result as one final
 * event — exactly how a simple agent needs it. Full streaming agents override
 * {@link #stream} directly.
 */
public abstract class AbstractA2AAgentExecutor
        implements A2AAgentExecutor, A2AStreamingAgentExecutor {

    @Override
    public void stream(AgentExecutionContext context, AgentEventSink sink) {
        try {
            sink.completed(execute(context));   // wrap blocking result as single event
        }
        catch (JSONRPCError e) {
            sink.failed(e);
        }
    }
}
```

Key differences vs. community's `String execute(ChatClient, RequestContext)`:

- **no ChatClient** — Spring AI integration becomes its own small adapter below;
- **no SDK RequestContext leak into user logic** — replaced by our `AgentExecutionContext` (which internally wraps `io.a2a.server.RequestContext`);
- **two-operation model like Spring AI** — blocking callers use `execute`; `message/stream` transport calls route to `stream`; no callback-mandated API on the blocking path.

Supporting types:

```java
public interface AgentEventSink {
    void statusChanged(TaskState state, String message);   // io.a2a.spec.TaskState
    void artifactAdded(Artifact artifact);                 // io.a2a.spec.Artifact
    void chunk(Part delta);                                // io.a2a.spec.Part
    void completed(EventKind result);
    void failed(JSONRPCError error);                       // io.a2a.spec.JSONRPCError
}

/** Execution context handed to agents — everything they are allowed to know. */
public interface AgentExecutionContext {
    Message incoming();                    // io.a2a.spec.Message
    Optional<Task> currentTask();          // io.a2a.spec.Task
    CallerIdentity caller();               // fixes community TODO #1
    Map<String, Object> attributes();      // transport-level extras
    CancellationToken cancellationToken(); // cooperative cancel support
}

public record CallerIdentity(Optional<String> subject, Set<String> scopes,
                             Map<String, Object> claims) {}
```

Spring AI convenience layer (kept thin, optional dependency) — also follows the same dual shape:

```java
/** Adapter for ChatClient users — equivalent ergonomics to community's handler. */
public interface ChatClientMessageHandler {
    String handle(ChatClient chatClient, AgentExecutionContext context);
}

/** Optional streaming variant backed by ChatClient#stream. */
public interface StreamingChatClientMessageHandler {
    void handle(ChatClient chatClient, AgentExecutionContext context, AgentEventSink sink);
}

public final class ChatClientExecutors {
    public static AbstractA2AAgentExecutor from(ChatClient chatClient,
                                                ChatClientMessageHandler handler) { ... }
    public static AbstractA2AAgentExecutor from(ChatClient chatClient,
                                                ChatClientMessageHandler blocking,
                                                StreamingChatClientMessageHandler streaming) { ... }
}
```

### 4.2 Persistence & infrastructure ports

Mirror what community autoconfigure wires as SDK types, but as ours:

```java
public interface A2ATaskStore {
    void save(Task task);                                        // io.a2a.spec.Task
    Optional<Task> find(String taskId);
    void updateState(String taskId, TaskState state);
}

public interface A2AEventQueue {                 // per-context event stream
    void publish(String contextId, EventKind event);             // io.a2a.spec.EventKind
    Subscription subscribe(String contextId, Consumer<EventKind> listener);
}

public interface PushNotificationSender { void send(Task task); }
```

`a2a-core-default` provides `InMemoryTaskStore`, `InMemoryEventQueue` (single-JVM defaults, matching community behavior). Redis/JDBC stores become ordinary implementations later.

### 4.3 Request dispatch port (transport-independent)

This is the seam that lets us swap transports entirely:

```java
public interface A2ARequestDispatcher {
    EventKind onMessageSend(MessageSendParams params, CallContext ctx);
    void      onMessageStream(MessageSendParams params, AgentEventSink sink, CallContext ctx);
    Optional<Task> onGetTask(TaskQueryParams query, CallContext ctx);
    Task     onCancelTask(TaskIdParams params, CallContext ctx);
    AgentCard agentCard(CallContext ctx);
}
```

All parameter/result types are SDK spec types (`io.a2a.spec.*`) — the dispatcher is a behavioral re-implementation of what the SDK's `DefaultRequestHandler` does, but owned by us, testable without any transport, and with hooks the SDK doesn't expose (identity, cancellation tokens).

`DefaultA2ARequestDispatcher` (in `a2a-core-default`) orchestrates: validate → resolve/create task → invoke `A2AAgentExecutor` → persist events → return.

### 4.4 Client-side ports (missing upstream entirely)

Same dual-interface pattern on the client side:

```java
public interface A2ARemoteAgentClient extends AutoCloseable {
    AgentCard discover(URI baseUrl);
    EventKind sendMessage(Message message, Duration timeout);
}

public interface A2AStreamingRemoteAgentClient {
    void streamMessage(Message message, Consumer<EventKind> listener);
}
```

Default implementation wraps `a2a-java-sdk-client` (which already implements the spec's client side) and implements both interfaces. Our host agent and `spring-ai-agent-utils`'s subagent bridge then depend only on these interfaces.

## 5. Responsibility split: what we own vs. what the SDK owns

| Concern | Owner | Notes |
|---|---|---|
| Wire model (Message, Part, Task, AgentCard, errors) | **SDK** (`io.a2a.spec`) | canonical; upgrades = version bump |
| JSON-RPC request parsing / response envelopes | **SDK** | spec-defined behavior |
| Task lifecycle orchestration (submit → work → complete/cancel) | **ours** (`DefaultA2ARequestDispatcher`) | re-owned so we can add identity & cancellation hooks; can delegate to SDK `DefaultRequestHandler` initially and replace incrementally |
| Agent execution contract | **ours** (`A2AAgentExecutor`, `AgentEventSink`) | framework-neutral, streaming-first |
| Persistence / event distribution | **ours** (ports) + SDK's in-memory impls as fallback | swap backends without touching protocol code |
| Caller identity propagation | **ours** (`CallerIdentityResolver`) | fixes community auth TODO |
| Client-side calls to remote agents | **ours** (`A2ARemoteAgentClient` port) over SDK client | shared by host agent & subagent bridges |

Migration shortcut: in Phase 3 we may wrap the SDK's `RequestHandler` inside our `Dispatcher` first (thin delegation), then peel responsibilities inward — this keeps a working system at every step instead of a big-bang rewrite.

## 6. Spring Boot starter

Replaces `spring-ai-a2a-server-autoconfigure` with our beans:

- `@ConditionalOnMissingBean` for every port: `A2AAgentExecutor`, `A2ATaskStore`, `A2ARequestDispatcher`, `A2ARemoteAgentClient`.
- Controllers (`/message`, `/tasks/*`, `/.well-known/agent-card.json`) call **only** `A2ARequestDispatcher` — never SDK types.
- `CallerIdentityResolver` strategy bean (default: reads Spring Security `Authentication` if present → fixes the community auth TODO properly).
- Properties under `io.github.khezyapp.a2a.*` (task store type, streaming enabled, card location).

Base package for all our modules: **`io.github.khezyapp.a2a.*`** (`io.github.khezyapp.a2a.core`, `.core.executor`, `.core.store`, `.starter`, `.springai`, ...).

## 7. What we deliberately improve over the baseline

| Baseline gap | Our answer |
|---|---|
| `ServerCallContext(null, …)` auth TODO | `CallerIdentityResolver` port + `AgentExecutionContext.caller()` |
| Blocking `String→String` only | Dual-interface pattern (`A2AAgentExecutor#execute` + `A2AStreamingAgentExecutor#stream`) with `AbstractA2AAgentExecutor` bridge; blocking handler kept as convenience adapter |
| No cooperative cancellation | `CancellationToken` threaded through execution context |
| Executor hook leaks SDK internals into user logic | `AgentExecutionContext` exposes only what agents need; SDK types limited to the spec model |
| No client abstraction | `A2ARemoteAgentClient` port shared by host agent & subagent bridges |
| In-memory-only infra | `A2ATaskStore` / `A2AEventQueue` ports with pluggable backends |

## 8. Implementation plan

Phase 0 — skeleton
- [ ] Create Gradle modules: `a2a-core`, `a2a-core-default` (Java 17; only dep: `io.github.a2asdk:a2a-java-sdk-spec`).

Phase 1 — core contracts
- [ ] Ports typed on SDK spec types: `A2AAgentExecutor`, `A2AStreamingAgentExecutor`, `AbstractA2AAgentExecutor`, `AgentEventSink`, `AgentExecutionContext`, `A2ATaskStore`, `A2AEventQueue`, `A2ARequestDispatcher`, `A2ARemoteAgentClient`, `A2AStreamingRemoteAgentClient`, `CallerIdentityResolver` (base package `io.github.khezyapp.a2a.core.*`).
- [ ] `DefaultA2ARequestDispatcher`: submit/startWork/artifact/complete lifecycle, cancellation guard (port of community `DefaultAgentExecutor.execute/cancel` logic); routes `message/send` → `execute`, `message/stream` → `stream`.
- [ ] Unit tests for dispatcher lifecycle (submitted → working → completed; cancel of completed task rejected; streaming fallback via bridge).

Phase 2 — defaults
- [ ] `InMemoryTaskStore`, `InMemoryEventQueue` (+ thread-safety tests).
- [ ] `ChatClientExecutors.from(...)` adapter in optional module `a2a-spring-ai`.

Phase 3 — wire-up with the SDK server runtime
- [ ] Bridge `A2AAgentExecutor` onto the SDK's `AgentExecutor`/`TaskUpdater` so the existing SDK `RequestHandler` keeps handling protocol plumbing; our dispatcher owns identity + event interception.
- [ ] Integration test replicating community's `A2AClientServerIntegrationTests` scenario through our stack (see the upstream repo for the original test).

Phase 4 — starter + local validation (our own apps first)
- [ ] Starter autoconfiguration + security-based `CallerIdentityResolver`.
- [ ] Create minimal sample apps in this repository: an `a2a-server` math-agent app and an `a2a-client` host-agent app using the new library; run both directions (host → remote math agent) end-to-end.

Phase 5 — upstream contribution (deferred, optional)
- [ ] Only after Phase 4 proves the design in daily use, extract well-scoped, generally-useful pieces and PR them to spring-ai-community — candidates: auth-context support for controllers (`MessageController` TODO), streaming executor, cooperative cancellation. Keep PRs small and independent of our internal port names.

## 9. Risks / open questions

1. **Spec velocity**: A2A is heading toward Linux Foundation governance with possible wire changes. Since we depend on the SDK types, upgrades are a version bump — but breaking SDK changes may ripple into our port signatures. Mitigation: keep ports thin; wrap rather than extend SDK classes where practical.
2. **SDK lock-in vs. multi-stack goal**: we depend on the Java SDK's *types*, not its runtime. Other stacks remain reachable because they speak the same spec on the wire — our client port can be implemented over any of them. The abstraction is behavioral, so this is an accepted, bounded trade-off.
3. **Streaming**: resolved — follow Spring AI's dual-interface pattern (`A2AAgentExecutor#execute` / `A2AStreamingAgentExecutor#stream`, client-side equivalents). No Reactor dependency in core; `AgentEventSink` is callback-based. Reactive (`Flux`-returning) wrappers can be added later in an add-on module if needed.
4. **Dispatcher vs. SDK RequestHandler overlap**: Phase 3 wraps the SDK handler to avoid duplicating protocol logic. Risk: double-handling of events. Mitigation: integration tests modeled on the community repo run against both paths before peeling responsibilities inward.
5. **Upstream contribution**: deferred by design. When ready (post-Phase 4), contribute small independent pieces first — e.g. the auth-context fix is valuable upstream regardless of whether our full port design is adopted.
