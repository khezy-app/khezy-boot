# A2A-03 — `a2a-core-default`: dispatcher + in-memory stores

**Prerequisite task files:** `00-plan-overview.md`, `02-a2a-core-ports.md` (contracts exist — **do not change their signatures**).

**Do NOT modify** other modules. All code below lives in `ai/a2a/a2a-core-default/src/main/java` (package `io.github.khezyapp.a2a.core.default.*`).

---

## 1. Goal

Implement the default behavior layer: `DefaultA2ARequestDispatcher` (task lifecycle
orchestration), `InMemoryTaskStore`, `InMemoryEventQueue`, `NoopPushNotificationSender`,
and `DefaultAgentExecutionContext`. This makes `a2a-core-default` a usable, fully
tested, transport-free core.

## 2. Context digest

- Design doc §4.2/§4.3 + §8 Phase 1 (dispatcher lifecycle) and Phase 2 (in-memory defaults).
- Dispatcher owns: validate → resolve/create task → invoke executor → persist events → return. It does NOT talk to any transport.
- Verified SDK shapes you will build with (`00` §3.2):
  - `Task.builder()` / `Task.builder(Task)` — immutable records; use `builder(existing).status(...).build()` to transition state.
  - `TaskStatus(TaskState)` — one-arg convenience constructor.
  - `TaskState` constants: `TASK_STATE_SUBMITTED`, `_WORKING`, `_COMPLETED`, `_CANCELED`, `_FAILED`.
  - `MessageSendParams.message()`, `.metadata()`, `.tenant()`, `.configuration()`.
  - `Message.taskId()` / `.contextId()` — the message carries task identity.
  - `TaskStatusUpdateEvent(taskId, TaskStatus, contextId, metadata)` is the natural final event (implements `EventKind`).
  - `A2AError` for failures.
- The `A2AEventQueue` is keyed by **contextId** (per design §4.2).

## 3. Classes to create

### 3.1 `io.github.khezyapp.a2a.core.default.store.InMemoryTaskStore` implements `A2ATaskStore`

- Field: `final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();`
- `save(Task)` → `tasks.put(task.id(), task)` (requireNonNull both).
- `find(String)` → `Optional.ofNullable(tasks.get(id))`.
- `updateState(String, TaskState)` → look up; if absent throw `TaskStoreException`; else `tasks.put(id, Task.builder(existing).status(new TaskStatus(state)).build())`.
- Thread-safe by construction.

### 3.2 `io.github.khezyapp.a2a.core.default.store.InMemoryEventQueue` implements `A2AEventQueue`

- Field: `final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<EventKind>>> subscribers`.
- `publish(contextId, event)` → notify each listener (guard listener exceptions — never let one subscriber break others).
- `subscribe(contextId, listener)` → add to list; return an anonymous `Subscription` whose `unsubscribe()` removes it.

### 3.3 `io.github.khezyapp.a2a.core.default.store.NoopPushNotificationSender` implements `PushNotificationSender`

- `send(Task)` → no-op. Document: replace with a real implementation in applications that need push.

### 3.4 `io.github.khezyapp.a2a.core.default.executor.DefaultAgentExecutionContext` implements `AgentExecutionContext`

- Immutable class holding: `Message incoming`, `Optional<Task> currentTask`, `CallerIdentity caller`, `Map<String, Object> attributes`, `CancellationToken cancellationToken` — all `requireNonNull`, exposed via getters. (Do NOT expose SDK transport types here; that wrapping is done where the context is built.)
- Also implement `SimpleCancellationToken` (package `...executor`): atomic boolean; `requestCancellation()` sets it; `isCancellationRequested()` reads it.

### 3.5 `io.github.khezyapp.a2a.core.default.dispatch.DefaultA2ARequestDispatcher` implements `A2ARequestDispatcher`

Constructor:
```java
public DefaultA2ARequestDispatcher(
        final A2AAgentExecutor executor,
        final A2ATaskStore taskStore,
        final A2AEventQueue eventQueue,
        final PushNotificationSender pushNotificationSender,
        final Supplier<AgentCard> agentCardSupplier)
```

Behavior spec:

**`onMessageSend(MessageSendParams params, CallContext ctx)`**
1. Resolve task id: `taskId = params.message().taskId()`; if blank, generate `UUID.randomUUID().toString()`, and use it as contextId too (fall back to `params.message().contextId()` when present).
2. `taskStore.save(buildTask(taskId, contextId, TASK_STATE_SUBMITTED, params.message()))` where the task's `history` starts as `List.of(params.message())`.
3. Build `DefaultAgentExecutionContext(incoming=params.message(), currentTask=store.find(taskId), caller=ctx.caller(), attributes=ctx.attributes(), cancellationToken=new SimpleCancellationToken())`.
4. Publish a `TaskStatusUpdateEvent(taskId, new TaskStatus(TASK_STATE_SUBMITTED), contextId, params.metadata())` to the queue under contextId.
5. Invoke `executor.execute(context)`.
6. On success: `taskStore.updateState(taskId, TASK_STATE_COMPLETED)`; publish a final `TaskStatusUpdateEvent(taskId, new TaskStatus(TASK_STATE_COMPLETED), contextId, metadata)`; return the event returned by the executor.
7. On `TaskCancelledException`: `updateState(taskId, TASK_STATE_CANCELED)`; publish; return/throw a `TaskStatusUpdateEvent` with `TASK_STATE_CANCELED`.
8. On `A2AError e`: `updateState(taskId, TASK_STATE_FAILED)`; publish `e` (it is an `Event`); rethrow `e`.
9. On any other `RuntimeException e`: `updateState(taskId, TASK_STATE_FAILED)`; publish `new A2AError(-32000, "Internal error", Map.of())`; rethrow (wrap in `A2ACoreException` if preferred).

**`onMessageStream(MessageSendParams params, AgentEventSink sink, CallContext ctx)`**
- Same task creation as above, but instead of `execute`:
  - Resolve streaming capability: `final A2AStreamingAgentExecutor streaming = (executor instanceof A2AStreamingAgentExecutor s) ? s : new StreamingFallback(executor);` where `StreamingFallback extends AbstractA2AAgentExecutor` delegating `execute` to the wrapped executor (use the bridge from `a2a-core`).
  - Build a **persisting sink** adapter: implements `AgentEventSink`, forwards every call to the caller-provided `sink` AND persists events to the store/queue (statusChanged → updateState + publish; completed → updateState(TASK_STATE_COMPLETED) + publish; failed → updateState(TASK_STATE_FAILED) + publish).
  - `streaming.stream(context, persistingSink)`.

**`onGetTask(TaskQueryParams query, CallContext ctx)`**
- `return taskStore.find(query.id());`

**`onCancelTask(TaskIdParams params, CallContext ctx)`**
- `taskStore.find(params.id())` — absent → throw `TaskNotFoundException`.
- If the stored task's state is final (`TaskState.isFinal()`), throw `TaskNotCancelableException(taskId)` (per design: "cancel of completed task rejected").
- `executor.cancel(params.id())` (may throw `TaskNotCancelableException` — let it propagate).
- `taskStore.updateState(params.id(), TASK_STATE_CANCELED)`; publish canceled event; return the updated task.

**`agentCard(CallContext ctx)`**
- `return agentCardSupplier.get();`

Helper: a private `buildTask(...)` method constructing `Task.builder().id(...).contextId(...).status(new TaskStatus(state)).history(List.of(message)).build()`.

## 4. Tests (`src/test/java`, package mirroring main)

Use a recording `AgentEventSink` helper (in-test class) and a stub `A2AAgentExecutor`.

1. `DefaultA2ARequestDispatcherTest`:
   - **submitted → working → completed**: stub executor returns a `TaskStatusUpdateEvent`; after `onMessageSend`, `taskStore.find(taskId)` has `TASK_STATE_COMPLETED`; queue received a submitted + completed event.
   - **cancel of completed task rejected**: mark task completed first, then `onCancelTask` throws `TaskNotCancelableException`.
   - **cancel of active task**: stub executor whose `cancel()` does nothing; assert task becomes `TASK_STATE_CANCELED` and a canceled event was published.
   - **get task absent** → `onGetTask` returns `Optional.empty()`.
   - **unknown cancel** → `TaskNotFoundException`.
   - **executor failure** → `A2AError` propagates and task is `TASK_STATE_FAILED`.
2. `DefaultA2ARequestDispatcherStreamingTest`:
   - **streaming fallback via bridge**: blocking-only executor → `onMessageStream` emits exactly one `completed` event; task final state completed.
   - **native streaming executor**: an executor implementing `stream()` directly emitting `chunk` + `completed`; assert sink received both and store final state completed.
3. `InMemoryTaskStoreTest` — save/find/updateState; updateState on missing id throws `TaskStoreException`.
4. `InMemoryEventQueueTest` — publish delivered to subscriber; unsubscribe stops delivery; one subscriber's exception does not break others.
5. `InMemoryTaskStoreConcurrencyTest` — N threads saving/finding unique ids; no lost updates (all present at end).

## 5. Verification

```bash
./gradlew :ai:a2a:a2a-core-default:test
./gradlew :ai:a2a:a2a-core-default:checkstyleMain :ai:a2a:a2a-core-default:checkstyleTest
./gradlew :ai:a2a:a2a-core-default:check
```

## 6. Handoff note for the next task

> Record this handoff by appending an entry to `99-handoff-log.md` (template inside;
> protocol in `00` §6.7). Chat is NOT the handoff channel.

Confirm the exact `Task` builder components used (id/contextId/status/history) and any
SDK surprises (`TaskStatusUpdateEvent` metadata map semantics). Note the dispatcher
constructor signature — the starter (A2A-05) wires it with Spring beans.
Then run `graphify update .`.

