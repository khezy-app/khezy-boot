# A2A2-02 — `a2a-core`: server seam and isolation contracts

**Prerequisites:** `00`, `01`.
**Design:** §5.3–§5.5.

## 1. Goal

Give consumers a business-logic seam over the SDK `AgentExecutor` without replacing it,
plus the Spring-independent contracts the starter needs: call-context factory, store
provider, tenant scope locator/authorization, server customizer.

Package root: `io.github.khezyapp.a2a.core.server.*`.

## 2. Files

### 2.1 `server/A2ARequest`

Final helper class wrapping `RequestContext` + `AgentEmitter`. Not a wire model.

```java
public final class A2ARequest {

    private final RequestContext context;
    private final AgentEmitter emitter;

    public A2ARequest(final RequestContext context, final AgentEmitter emitter);

    public Message incoming();
    public Optional<Task> currentTask();
    public String taskId();
    public String contextId();
    public Optional<User> caller();
    public Optional<String> tenant();
    public Map<String, Object> attributes();   // metadata + call-context state (immutable copy)
    public List<Task> relatedTasks();          // context.getRelatedTasks(), immutable copy
    public String userInput();

    public void reply(String text);
    public void reply(String text, Map<String, Object> metadata);
    public void artifact(String name, String text);
    public void artifact(String name, String description, String text);
    public void complete();
    public void complete(String text);
    public void requireInput(String question);
    public void requireAuth(String instruction);
    public void fail(String reason);
    public void cancel();
    public RequestContext context();           // escape hatch
    public AgentEmitter emitter();             // escape hatch
}
```

Behavior:

- `reply`/`artifact`/`requireInput`/`requireAuth`/`complete`/`fail`/`cancel` create an
  agent `Message` via `emitter.messageBuilder()` / `emitter.newAgentMessage(...)` when
  text is present, otherwise call the no-arg emitter method.
- `artifact` uses `emitter.addArtifact(List.of(new TextPart(text)), name, description, Map.of())`.
- `caller()` returns `Optional.ofNullable(context.getCallContext())` mapped to `getUser()`,
  filtering `UnauthenticatedUser`.
- `attributes()` merges `context.getMetadata()` (nullable) and call-context state into an
  immutable map; later wins: call-context state over metadata.
- `relatedTasks()` returns `context.getRelatedTasks()` as an immutable copy (empty when
  `populate-referred-tasks` is off).
- `tenant()` returns `context.getTenant()` when present, otherwise the SDK tenant in the
  call-context state.
- `taskId`/`contextId` fall back to `context.getTask()` when the direct getters are null.
- Validate non-null constructor args.

### 2.2 `server/AbstractA2AAgentExecutor`

Implements SDK `AgentExecutor`.

- `execute`:
  1. if `context.getTask() == null`, seed:
     `emitter.addTask(emitter.taskBuilder().status(new TaskStatus(TASK_STATE_SUBMITTED)).history(List.of(context.getMessage())).build())`.
  2. `emitter.startWork()`.
  3. call `onMessage(new A2ARequest(context, emitter))`.
  4. Never catch business exceptions: let `A2AError` propagate; wrap unexpected
     `RuntimeException` in `new InternalError(message)`? **No** — let the SDK's handler
     map it. Document that consumers should throw `A2AError` for protocol-visible errors.
- `protected abstract void onMessage(A2ARequest request);`
- `cancel` default: `emitter.cancel()`; override hook `protected void onCancel(A2ARequest request)`.
- Also expose `protected void onMessage(...)` only; no extra lifecycle methods.
- The SDK `AgentExecutor` is a functional interface with a default `cancel`; document
  that simple agents may use a lambda `(context, emitter) -> ...` and that
  `AbstractA2AAgentExecutor` is a convenience, not a requirement.
- `RequestContext.getTenant()` is available to consumers (SDK 1.3+); `A2ARequest` should
  expose it as `Optional<String> tenant()` (see task 02's `A2ARequest` API).

### 2.3 `server/A2ACallContextFactory<T>`

Template method as in design §5.4.

- `public final ServerCallContext build(T request)` collects:
  - headers under `JSONRPCContextKeys.HEADERS_KEY` (SDK key),
  - tenant under `JSONRPCContextKeys.TENANT_KEY` (only if non-blank),
  - scope under `A2AContextKeys.AGENT_SCOPE`,
  - `ServerCallContext.TRANSPORT_KEY` = `TransportProtocol.JSONRPC`,
  - requested extensions from `A2AHeaders.A2A_EXTENSIONS`,
  - requested version from `A2AHeaders.A2A_VERSION`.
- abstract `resolveUser(T request)` returns `Optional<User>` (empty → `UnauthenticatedUser.INSTANCE`).
- hooks `resolveTenant`, `resolveScope`, `extractHeaders`, `extractHeader`, `extractHeaderValues`
  default to empty/null; implementers override what they support.
- `A2AContextKeys` (public final constants) with `AGENT_SCOPE`.

### 2.4 `server/A2AStoreProvider`

Mirrors the reference but uses only SDK types:

```java
public interface A2AStoreProvider {
    TaskStore taskStore();
    TaskStateProvider taskStateProvider();
    PushNotificationConfigStore pushNotificationConfigStore();

    static A2AStoreProvider inMemory();
    static A2AStoreProvider of(TaskStore, TaskStateProvider, PushNotificationConfigStore);
}
```

`inMemory()` creates `InMemoryTaskStore` twice (same instance for store and state
provider) and `InMemoryPushNotificationConfigStore`.

### 2.5 `server/TaskScopeLocator` and `server/TenantTaskAuthorizationProvider`

- `TaskScopeLocator`:
  ```java
  @Nullable String findScope(String taskId);
  static TaskScopeLocator none();
  static TaskScopeLocator fromTaskStore(TaskStore store); // reads A2AContextKeys.AGENT_SCOPE from task metadata
  ```
- `TenantTaskAuthorizationProvider implements TaskAuthorizationProvider`:
  - `checkRead`: allow `LIST_TASKS`; otherwise same scope.
  - `checkWrite`: same scope.
  - `checkCreate`: `context != null`.
  - `isTaskRecorded`: `true`.
  - `recordOwnership`: no-op.
  - `isSameScope` reads `AGENT_SCOPE` from call-context state and compares to
    `scopeLocator.findScope(taskId)`. Missing/blank scope → deny.
- Denial must be surfaced by the SDK as TaskNotFound; do not throw a custom error.

### 2.6 `server/A2AServerCustomizer`

```java
@FunctionalInterface
public interface A2AServerCustomizer {
    void customize(DefaultRequestHandler.Builder builder);   // or a context record
}
```

Because the builder cannot express timeouts, expose:
```java
public interface A2AServerCustomizer {
    default void customize(DefaultRequestHandler.Builder builder) { }
    default void customize(DefaultRequestHandler handler) { }
}
```
Start with `customize(DefaultRequestHandler)` only (timeouts/config are handled by the
starter via `A2AConfigProvider`). Document ordering: called after `build()`.

Also add `server/A2AConfigValueProvider`? No — use SDK `A2AConfigProvider` directly in
the starter; core stays Spring-free.

## 3. Tests (`a2a-core/src/test/java`)

- `A2ARequestTest` — with a Mockito `RequestContext` + `AgentEmitter`: reply/artifact/
  complete/requireInput/fail/cancel call the right emitter methods; id fallbacks;
  `attributes()` merge; `caller()` empty for unauthenticated.
- `AbstractA2AAgentExecutorTest` — new task seeds history + startWork + delegates;
  existing task does not re-seed; `onCancel` default cancels.
- `TenantTaskAuthorizationProviderTest` — same scope allows, different/blank denies,
  `LIST_TASKS` allows, create allows.
- `A2ACallContextFactoryTest` — headers/tenant/scope/extensions/version land in state;
  empty user becomes `UnauthenticatedUser`.
- `A2AStoreProviderTest` — in-memory provider returns the same store/state provider.

Use `khezy.java-use-mockito` in `a2a-core/build.gradle`.

## 4. Verification

```bash
./gradlew :a2a-core:check
```

## 5. Handoff

Record exact emitter method names used, `RequestContext` nullability surprises, and the
final `A2AServerCustomizer` shape (task 06 depends on it). `graphify update .`.
