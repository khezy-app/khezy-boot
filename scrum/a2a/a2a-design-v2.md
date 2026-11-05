# KHEZY A2A — Design

> Build **around** the official A2A Java SDK, not around a re-implementation of the
> protocol. The SDK already owns the wire model, task lifecycle, event bus, JSON-RPC
> handling, authorization contract, and client transports. KHEZY adds the Spring-native
> glue, the business-logic seam, encryption-at-rest, client ergonomics, and LLM tooling.

Status: approved direction
Date: 2026-10-19
Baseline: `org.a2aproject.sdk:*:1.4.0.Final`, Spring Boot 4.1.0, Spring AI 2.0.1, Java 17
Adoption review: `sdk-1.4-adoption.md` (what changed since 1.3.1 and why we take it)

---

## 1. Why this design

The SDK is a runtime, not just a wire-model library. Re-implementing the server around
it duplicates work and creates drift risk:

- a custom dispatcher would duplicate `DefaultRequestHandler` (task creation, state
  transitions, event publishing, cancellation);
- custom task/event/push stores would duplicate the SDK interfaces;
- custom controllers would re-parse JSON-RPC and re-serialize responses;
- a custom executor contract would redefine what the SDK already defines.

That is protocol redesign in disguise. It creates drift risk on every SDK upgrade,
loses the SDK's fixes (streaming, replication, extended cards, push notifications,
multi-tenancy, authorization), and forces consumers to learn *our* model **and** the SDK.

The reference implementation at `/mnt/data/refs-proj/kflowllmlap/a2a-*` validates the
opposite approach: keep the SDK runtime intact, wrap it with small Spring-friendly
seams. This design adopts that model and adds the two key differentiators:

1. **Encryption at rest** for A2A store payloads (the SDK stores JSON as plain text).
2. **Abstracted business-logic seam** so consumers implement only their agent logic.

## 2. Principles

| # | Principle | Consequence |
|---|---|---|
| P1 | **The SDK owns the protocol** | No custom `RequestDispatcher`, task lifecycle, event queue, wire envelopes, or spec types. |
| P2 | **Add seams, not replacements** | Every KHEZY type is either (a) a Spring integration point, (b) a business-logic convenience, (c) an optional persistence/tooling add-on. |
| P3 | **Consumer writes business logic only** | A working server needs: one `AgentCard`, one `AgentExecutor`, optional identity/tenant resolution. |
| P4 | **Everything overridable** | Every auto-configured bean is `@ConditionalOnMissingBean`; customizer beans adjust SDK construction. |
| P5 | **Optional dependencies stay optional** | JPA, Spring Security, Spring AI are isolated modules/conditions; core is usable without them. |
| P6 | **Secure defaults, explicit opt-out** | Encryption enabled when a key is configured; plaintext fallback is explicit and logged. |
| P7 | **No reflection in consumer code** | SDK seams that require reflection (config injection, card-router injection) live once in the starter, documented and fail-fast. |
| P8 | **Tenant-aware by hook, not by fork** | Use the SDK's `AgentExecutorRouter`/`AgentCardRouter`; never duplicate request routing. |

## 3. Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Consumer application                                                         │
│   AgentCard · AgentExecutor (business logic) · TenantResolver (optional)      │
└───────────────┬──────────────────────────────────────────────────────────────┘
                │ implements / provides
┌───────────────▼──────────────────────────────────────────────────────────────┐
│ a2a-spring-boot-starter                                                       │
│   A2AServerAutoConfiguration · A2AClientAutoConfiguration · A2AStoreAutoConfig│
│   A2AJsonRpcController · AgentCardController · SpringA2ACallContextFactory    │
│   SpringA2AConfigProvider · A2AServerProperties · A2AClientProperties         │
└───────┬───────────────────────────┬───────────────────────────┬───────────────┘
        │                           │                           │
┌───────▼────────────┐   ┌──────────▼───────────┐   ┌───────────▼──────────────┐
│ a2a-core           │   │ a2a-core-default     │   │ a2a-store-jpa (optional) │
│ server SPI:        │   │ client defaults:     │   │ TaskStore + TaskState    │
│  AbstractA2AAgent  │   │  SdkA2AInvoker       │   │ PushConfigStore          │
│  A2ARequest helper │   │  A2AConnector        │   │ TaskScopeLocator         │
│  CallContextFactory│   │  InMemory convo store│   │ A2ACipher + AES-GCM      │
│  TenantAuth        │   │  CachingCardResolver │   │ Encrypted entities       │
│ client contracts   │   │  RemoteAgentRegistry │   │                          │
└───────┬────────────┘   └──────────┬───────────┘   └──────────────────────────┘
        │                           │
┌───────▼───────────────────────────▼──────────────────────────────────────────┐
│ Official A2A Java SDK (org.a2aproject.sdk)                                    │
│  spec · server-common (DefaultRequestHandler, TaskStore, AgentEmitter, bus)   │
│  transport-jsonrpc (JSONRPCHandler) · client + transports                     │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Dependency direction

`a2a-core` → SDK (api)
`a2a-core-default` → `a2a-core`
`a2a-store-jpa` → `a2a-core` + `khezy-crypto` + Spring Data JPA (optional at app level)
`a2a-spring-ai` → `a2a-core` + Spring AI (optional)
`a2a-spring-boot-starter` → `a2a-core-default` (+ optional `a2a-store-jpa`, `a2a-spring-ai` via classpath conditions)

No module depends on `a2a-spring-boot-starter`.

## 4. Module map (v2)

| Module | Responsibility | Not responsible for |
|---|---|---|
| `a2a-core` | Server business-logic seam (`AbstractA2AAgentExecutor`, `A2ARequest`), `A2ACallContextFactory`, `A2AStoreProvider`, `TaskScopeLocator`, `TenantTaskAuthorizationProvider`, client contracts (`AgentCardResolver`, `A2AInvoker`, `A2AConnector`, `A2AConversationStore`, `RemoteAgent`, `RemoteSkill`, `SendRequest/Options/Outcome`, `A2AClientOperations`), error normalization | Protocol, HTTP, Spring |
| `a2a-core-default` | `SdkA2AInvoker`, `A2AConnector`, in-memory conversation store, `SdkAgentCardSource` + `CachingAgentCardResolver`, `A2AClientFactory`, `RemoteAgentRegistry`, transport discovery, no-op/identity defaults | Stores, controllers |
| `a2a-store-jpa` (new) | JPA `TaskStore`/`TaskStateProvider`/`PushNotificationConfigStore`, `TaskScopeLocator`, `StringEncryptor` (from `khezy-crypto`) with row AAD, encrypted-at-rest entities, additive entity/repository scanning | Business logic, key management |
| `a2a-spring-ai` | Skill-as-tool, agent-as-tool, prompt provider/builder, result formatter, tool context keys | Server execution |
| `a2a-spring-boot-starter` | SDK server graph assembly, JSON-RPC + agent-card endpoints, servlet/security call-context factory, Spring `A2AConfigProvider`, client graph assembly, properties, conditional store/tooling wiring | Reimplementing SDK behavior |
| `samples/` | Runnable server + client (E2E proof) | — |

Package root stays `io.github.khezyapp.a2a.*`.

## 5. Server design

### 5.1 What the SDK already gives us (do not rebuild)

- `DefaultRequestHandler` — task creation, state machine, blocking call coordination,
  event consumption, push notification dispatch, authorization hooks, multi-tenancy
  router, replication.
- `TaskStore`, `TaskStateProvider`, `PushNotificationConfigStore`, `QueueManager`,
  `MainEventBus` / `MainEventBusProcessor`, `AgentEmitter`, `AgentExecutor`,
  `TaskAuthorizationProvider`, `ServerCallContext`.
- `JSONRPCHandler` — every JSON-RPC method (`SendMessage`, `SendStreamingMessage`,
  `GetTask`, `ListTasks`, `CancelTask`, `SubscribeToTask`, push-config CRUD,
  extended card).
- `A2AConfigProvider` — configuration lookup used by `@PostConstruct` initialization.

The starter assembles these into Spring beans and does **not** wrap their semantics.

### 5.2 Bean graph owned by the starter

```
AgentCard (consumer or properties)
AgentExecutor (consumer, required)
AgentExecutorRouter (optional, tenant routing)
AgentCardRouter (optional, tenant cards)
TaskStore / TaskStateProvider (InMemory default, JPA override)
PushNotificationConfigStore (InMemory default, JPA override)
TaskStreamLifecycleHook (optional; InMemoryQueueManager)
MainEventBus
QueueManager (InMemoryQueueManager)
MainEventBusProcessor
Executor agentExecutor            (virtual threads)
Executor eventConsumerExecutor    (cached platform threads)
A2AConfigProvider (Spring-backed, falls back to SDK defaults)
TaskAuthorizationProvider (tenant-based default, or consumer)
DefaultRequestHandler (builder + A2AServerCustomizer hooks)
JSONRPCHandler
A2AServerJsonRpcHandler (transport adapter)
A2AJsonRpcController + AgentCardController
```

Important SDK lifecycle facts (verified against 1.4.0):

- `DefaultRequestHandler` and `MainEventBusProcessor` use `@PostConstruct`. When
  created as Spring beans, Spring runs those callbacks; the handler's `configProvider`
  field must be injected first. Provide one `SdkA2AConfigInjector` helper in the starter
  that sets it reflectively **once**, fail-fast if the SDK field disappears.
- The builder hardcodes blocking timeouts (5s / 2s / 1s); `initConfig()` overwrites
  them from `A2AConfigProvider`. The starter exposes `a2a.server.blocking.*` properties
  and ensures `initConfig()` sees them. Do not use reflection for timeouts.
- 1.4.0 makes `MainEventBusProcessor.start()` public and idempotent and the
  `DefaultRequestHandler` constructor calls it. Spring's `@PostConstruct` also calls it;
  double-start is safe. **No manual start workaround is needed.**
- 1.4.0 starts the event processor for manual/builder wiring; still expose the processor
  as a bean so `@PreDestroy stop()` runs on shutdown.

### 5.3 Business-logic seam (the key consumer API)

The SDK `AgentExecutor` is powerful but ceremonial: every turn must seed history,
start work, and use `AgentEmitter`. v2 offers a convenience base class while keeping the
raw interface fully supported.

```java
public abstract class AbstractA2AAgentExecutor implements AgentExecutor {

    @Override
    public final void execute(final RequestContext context,
                              final AgentEmitter emitter) throws A2AError {
        if (Objects.isNull(context.getTask())) {
            emitter.addTask(emitter.taskBuilder()
                    .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                    .history(List.of(context.getMessage()))
                    .build());
        }
        emitter.startWork();
        onMessage(new A2ARequest(context, emitter));
    }

    /** Consumer business logic. */
    protected abstract void onMessage(A2ARequest request);

    @Override
    public void cancel(final RequestContext context,
                       final AgentEmitter emitter) throws A2AError {
        emitter.cancel();
    }
}
```

`A2ARequest` (helper, not a wire model) exposes only what an agent needs:

```java
public final class A2ARequest {
    public Message incoming();
    public Optional<Task> currentTask();
    public String taskId();
    public String contextId();
    public Optional<User> caller();
    public Optional<String> tenant();
    public Map<String, Object> attributes();        // request metadata + call-context state
    public List<Task> relatedTasks();               // SDK populateReferredTasks feature
    public String userInput();

    public void reply(String text);                 // send an agent message
    public void artifact(String name, String text); // add a text artifact
    public void complete();
    public void complete(String message);
    public void requireInput(String question);
    public void requireAuth(String instruction);
    public void fail(String reason);
    public void cancel();
}
```

Rules:

- `A2ARequest` never replaces SDK types in signatures consumers need; `incoming()`,
  `currentTask()` return SDK records.
- It does **not** own task state, persistence, or streaming; it delegates to
  `AgentEmitter`.
- Consumers can still implement `AgentExecutor` directly; the starter does not require
  the base class.

### 5.4 Call context and identity

`A2ACallContextFactory<T>` is a template method that builds the SDK
`ServerCallContext` from the transport request:

```java
public abstract class A2ACallContextFactory<T> {

    public final ServerCallContext build(final T request) {
        return new ServerCallContext(
                resolveUser(request).orElse(UnauthenticatedUser.INSTANCE),
                state(request),          // headers, tenant, scope, transport, method
                requestedExtensions(request),
                requestedVersion(request));
    }

    protected abstract User resolveUser(T request);
    protected String resolveTenant(T request) { return null; }
    protected String resolveScope(T request) { return null; }
}
```

The starter provides `SpringSecurityA2ACallContextFactory` (servlet request) that:

- maps `Authentication` → SDK `User` (`AuthenticatedUser`/`UnauthenticatedUser`),
- copies headers into state under the SDK's `JSONRPCContextKeys.HEADERS_KEY`,
- resolves a tenant from a configurable header/path/claim (default: none),
- resolves an application scope under `A2AContextKeys.AGENT_SCOPE` (app-owned key,
  because the SDK validates tenant characters).

Consumers override the factory for custom identity. There is no separate
`CallerIdentity` type anymore; the SDK `User` + state is the contract.

### 5.5 Tenant/task isolation

`TenantTaskAuthorizationProvider` (default when authorization is enabled, or when a
`TaskScopeLocator` bean exists):

- scope of a task is persisted in task metadata (`A2AContextKeys.AGENT_SCOPE`) and
  denormalized by the JPA store.
- `checkRead`/`checkWrite` compare the call's scope to the stored scope.
- denial surfaces as `TaskNotFoundError` (no existence leak).
- `checkCreate` always allowed.
- `LIST_TASKS` is allowed and filtered in the store query.

`TaskScopeLocator` has a metadata-based default and a JPA-backed implementation.

### 5.6 Transport endpoints

- `A2AServerJsonRpcHandler<T, R>` mirrors the reference's transport-neutral adapter:
  parse `JSONRPCUtils.parseRequestBody`, dispatch to `JSONRPCHandler`, write JSON or
  SSE via two abstract hooks. It requests the SDK `authorizeTaskAccess` for streaming
  and subscribe. It does **not** re-map results.
- Spring MVC implementation:
  - `POST {a2a.server.path}/` (default `/a2a`) — JSON-RPC and SSE. Accept both
    `application/json` and `application/a2a+json` (SDK 1.4 supports the latter).
  - `GET {a2a.server.path}/.well-known/agent-card.json` — proto-JSON card
    (`JsonFormat` + `ProtoUtils`), `ETag`, `Cache-Control`, conditional `304`.
  - Optional tenant-aware card at
    `GET {a2a.server.path}/.well-known/{tenant}/agent-card.json` via `AgentCardRouter`.
- Server transport v2.0 is JSON-RPC over HTTP. gRPC/REST server bindings remain SDK
  concerns and are out of scope.

### 5.7 Server configuration

Prefix `io.github.khezyapp.a2a.server`:

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch for the server auto-config |
| `path` | `/a2a` | Base path for JSON-RPC and the card |
| `agent-card.*` | — | name, description, version, url, provider, skills, capabilities |
| `authorization.required` | `auto` | Enable SDK authorization + tenant provider. `auto` = true when Spring Security is present, else false + startup warning (SDK is fail-closed). |
| `blocking.agent-timeout` | `600s` | Blocking `SendMessage` agent wait |
| `blocking.consumption-timeout` | `60s` | Event consumption wait |
| `blocking.reconciliation-timeout` | `5s` | Task store reconciliation wait |
| `push-notifications.enabled` | `false` | SDK push notifications |
| `populate-referred-tasks` | `false` | Resolve `referenceTaskIds` into `RequestContext.getRelatedTasks()` |
| `executor.virtual-threads` | `true` | Agent executor uses virtual threads |
| `card-cache.max-age` | `60s` | Cache-Control max-age for the card |
| `multitenancy.enabled` | `false` | Expose tenant routers and tenant card paths |

### 5.8 Multi-tenancy (SDK 1.3+, adopted in v2)

The SDK already routes per tenant:

- `AgentExecutorRouter.resolve(tenant)` selects the business executor; `null`/blank/
  unknown falls back to the default executor.
- `RequestContext.getTenant()` exposes the resolved tenant to `AgentExecutor`.
- `AgentCardRouter.resolvePublicCard(tenant)` / `resolveExtendedCard(tenant)` select
  cards; tenant identifiers are restricted to `[a-zA-Z0-9_.-]`.
- Tenant cards are served at `/.well-known/{tenant}/agent-card.json`.
- `a2a.authorization.required` is fail-closed when no provider is configured.

Spring wiring:

- Expose `AgentExecutorRouter` bean → `DefaultRequestHandler.Builder.agentExecutorRouter(...)`.
- A `Map<String, AgentExecutor>` (or consumer-defined router) is the simple configuration;
  `@Tenant` is a CDI qualifier and is **not** used by Spring.
- Expose `AgentCardRouter` bean. The programmatic `JSONRPCHandler` constructor does not
  accept a router (only the CDI constructor does), so `SdkA2AConfigInjector` sets the
  `agentCardRouter` field reflectively — same pattern and fail-fast test as
  `configProvider`.
- `A2ACallContextFactory.resolveTenant` supplies the SDK tenant; `resolveScope` supplies
  the app-owned scope used for persistence/authorization. Keep them separate: SDK tenant
  is for routing; app scope is for task isolation.
- `TaskStore` and `QueueManager` remain shared across tenants (SDK behavior). Isolation
  is enforced by `TenantTaskAuthorizationProvider` using the persisted `agent_scope`.

## 6. Client design

The client side is where the SDK leaves the most work. v2 mirrors the proven reference
model, simplified.

### 6.1 Contracts (a2a-core)

```java
@FunctionalInterface
public interface AgentCardResolver {
    Optional<AgentCard> resolve(String baseUrl);
}

public interface A2AInvoker {
    SendOutcome send(SendRequest request);
}

public interface A2AConversationStore {
    TaskRef ref(String conversationId, String agentName, String skillId);
    void recordInterrupted(...);
    void recordPending(...);
    void recordSettled(...);
    void clearConversation(String conversationId);
}

public interface A2AConnector { /* store-aware multi-turn entry point */ }
```

Records:

- `RemoteAgent(String baseUrl, AgentCard card, Client client)` — long-lived.
- `RemoteSkill(agentName, agentUrl, id, name, description, tags, examples, i/o modes)`.
- `SendRequest` — SDK `MessageSendParams` equivalent plus client conventions
  (`skillId`, `conversationId`, `contextId`, `taskId`, `extensions`, `options`).
- `SendOptions` — timeout, strategy (`BLOCKING` now; `POLL`/`STREAM` reserved),
  `stopOnInterrupt`, headers, per-call state.
- `SendOutcome` — normalized (`agent`, `skill`, `TaskState`, ids, text, artifacts,
  error) with `isFinal`/`isInterrupted`/`isPending`/`taskRef()`.
- `TaskRef(contextId, taskId)`.

`A2AClientOperations` is a thin facade over SDK task/push operations
(`getTask`, `listTasks`, `cancelTask`, push-config CRUD, extended card) so hosts never
build SDK requests directly.

### 6.2 Defaults (a2a-core-default)

- `SdkAgentCardSource` — plain HTTP GET against the well-known location using the SDK
  `A2AHttpClient` (needed for status/headers); mirrors SDK `A2ACardResolver` URL rules:
  raw `.json` URL, custom card path, tenant path `/.well-known/{tenant}/agent-card.json`,
  well-known suffix stripping, and the SDK fallback URL. No hand-rolled URL parsing.
- `CachingAgentCardResolver` — spec-compliant HTTP cache: fresh hit, conditional
  revalidation (`ETag`/`Last-Modified`), stale-if-error, fail-soft.
- `A2AHttpClient` is supplied by the starter (one bean) and passed to the card source and
  every client transport. This is the Spring analogue of the SDK's CDI HTTP-client
  provider and makes the JDK/Vert.x choice explicit.
- `A2AClientFactory` + `A2AClientTransports` — register every available transport
  (JSON-RPC + REST built in; gRPC optional via `ServiceLoader`); the SDK selects from
  the card's `supportedInterfaces`. Each transport config receives the shared
  `A2AHttpClient` and optional transport parameters (used by the OpenTelemetry wrappers).
- SSE safety: the starter builds the HTTP client with `SSEParserConfig` from properties
  (`max-line-length`, `max-buffer-lines`, `max-buffer-chars`), defaulting to the SDK's
  1 MB limits. Prevents unbounded-line memory exhaustion from a malicious agent.
- `RemoteAgentRegistry` — fail-soft registration, lookup by name/skill, refresh,
  close lifecycle.
- `SdkA2AInvoker` — blocking send that waits until the task is settled (final **or**
  interrupted), never blocks an interrupted task until timeout.
- `A2AConnector` — injects stored `contextId`/`taskId` (explicit ids win), serializes
  calls per conversation, records interrupted/pending/settled outcomes.
- `InMemoryA2AConversationStore` — TTL + LRU bound.

### 6.3 Multi-turn rule (must stay)

- Interrupted task (`INPUT_REQUIRED`/`AUTH_REQUIRED`) → keep `contextId` + `taskId`.
- Pending task (non-blocking send) → keep both for later poll/continuation.
- Settled task → keep `contextId`, drop `taskId` (next message starts a new task in the
  same context).
- A2A ids never reach the LLM; the host supplies a conversation id out-of-band.

Extended cards / per-identity scopes: keep the contract `A2ACardScope` but ship only
the public scope in v2.0; scoped extended cards are a v2.1 item (the reference
implementation shows the full design when needed).

### 6.4 Client properties

Prefix `io.github.khezyapp.a2a.client`:

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `urls` | empty | Agent base URLs |
| `agent-card-path` | SDK default | Non-standard card path |
| `auth-headers` | empty | Headers for card fetch |
| `call-timeout` | `60s` | Blocking send timeout |
| `card-cache-ttl` | `5m` | Fallback freshness when no `Cache-Control` |
| `streaming` | `false` | Request streaming when the card supports it |
| `use-client-preference` | `false` | Prefer client transport order |
| `conversation-state-ttl` | `1h` | Idle conversation retention |
| `max-conversations` | `1000` | LRU bound |
| `sse.max-line-length` | `1048576` | Bytes per raw SSE line |
| `sse.max-buffer-lines` | `1000` | `data:` lines per event |
| `sse.max-buffer-chars` | `1048576` | Characters per event |
| `transport-parameters` | empty | Extra parameters for SDK transport wrappers (OpenTelemetry) |

## 7. Persistence and encryption (differentiator)

### 7.1 Stores

JPA implementation of the SDK interfaces, schema-compatible with the SDK extras
(`a2a_tasks`, `a2a_push_notification_configs`):

- `A2ATaskEntity` — PK `task_id`; plaintext denormalized `context_id`, `state`,
  `status_timestamp`, `agent_scope`, `finalized_at`; `task_data` holds serialized JSON.
- `SpringA2ATaskStore` implements `TaskStore` + `TaskStateProvider` with keyset
  pagination and scope-filtered listing.
- `A2APushNotificationConfigEntity` — composite key `(task_id, config_id)`; `task_data`
  holds serialized JSON. `config_id` is resolved from the config or defaults to `task_id`
  when omitted (SDK 1.4: `TaskPushNotificationConfig.id` is nullable).
- `SpringA2APushNotificationConfigStore` implements the SDK store with pagination and
  per-task limit. **1.4 semantics:** omitting the id is create-only (default id =
  taskId); if the default config already exists, reject with `InvalidParamsError` instead
  of replacing. Updates must supply the id explicitly.
- `JpaTaskScopeLocator` reads `agent_scope` in one scalar query.
- After a local (non-replicated) task is persisted in a final state, publish a Spring
  `A2ATaskFinalizedEvent(taskId, task)` so consumers can react (cleanup, metrics,
  notifications). Mirrors the SDK extras `TaskFinalizedEvent`, without CDI.

### 7.2 Encryption — use `khezy-crypto`

Encryption is **not implemented in the A2A store**. It uses the shared KHEZY crypto
utility (`scrum/crypto/crypto-library-design.md`):

- `io.github.khezyapp.crypto.contract.StringEncryptor` encrypts/decrypts serialized
  payloads.
- `CipherContext` binds the ciphertext to its row:
  - task: `a2a:task:<task_id>`
  - push config: `a2a:push:<task_id>:<config_id>`
  This prevents swapping ciphertext between rows.
- The shared library owns AES-256-GCM, key ids, key providers, and rotation. The A2A
  store keeps a thin `A2ACipher` adapter for source compatibility only.
- Encryption is applied to the **serialized payload column** by the store, not by a
  JPA `AttributeConverter`, so JSON serialization stays SDK-canonical.
- Never encrypt the denormalized columns used for queries (`task_id`, `context_id`,
  `state`, `status_timestamp`, `agent_scope`). Task content is protected; operational
  metadata is not.
- Encrypted columns cannot be searched by content. That is accepted.
- Rotation: use the crypto library's `ReEncryptionExecutor` over `task_data` /
  `config_data`; old rows stay readable until migrated.

### 7.3 Properties

Prefix `io.github.khezyapp.a2a.store`:

| Property | Default | Meaning |
|---|---|---|
| `type` | `memory` | `memory` or `jpa` (auto-detected if JPA on classpath) |
| `task.grace-period` | `15s` | Keep finalized tasks active for late events |
| `push.max-per-task` | SDK default | Limit push configs |

Key/rotation properties live under `io.github.khezyapp.crypto.*` (crypto starter).

## 8. Spring AI integration (optional)

Same proven model as the reference:

- `A2ASkillToolFactory` — every discovered skill becomes one `ToolCallback`; name and
  description come from the card; input is `{ "task": "..." }`.
- `A2AAgentTools` — coarse `sendToAgent` + `listRemoteAgents`.
- `A2APromptProvider` / `A2APromptBuilder` — routing system prompt from skills.
- `A2AResultFormatter` — compact JSON for the LLM, ids intentionally omitted.
- `A2AToolContextKeys.CONVERSATION_ID` — host supplies the conversation id via
  `ToolContext`; the model never sees A2A ids.

### 8.1 Optional observability (v2.1)

The SDK ships OpenTelemetry extras (`a2a-extras-opentelemetry-server`,
`-client`, `-client-propagation`). They are CDI/ServiceLoader oriented; our design can
adopt them without coupling core:

- server: wrap the `RequestHandler` in `OpenTelemetryRequestHandlerDecorator` (its public
  `(RequestHandler, Tracer)` constructor is usable from a small Spring adapter);
- client: put `Tracer` / `OpenTelemetry` into `ClientBuildOptions.transportParameters`
  so the ServiceLoader wrappers activate;
- keep this in a separate optional module/auto-config (`@ConditionalOnClass(Tracer.class)`),
  never in core.

This module does **not** adapt a `ChatClient` into a server `AgentExecutor` (v1 did).
Server execution is the consumer's business logic; if they want Spring AI there, they
implement `onMessage` and call their own `ChatClient`.

## 9. Responsibility matrix

| Concern | Owner |
|---|---|
| Spec/wire model, JSON-RPC envelopes, SSE framing, proto-JSON card | SDK |
| Task lifecycle, state machine, event bus, push notifications, replication | SDK |
| JSON-RPC method routing, authorization checks | SDK `JSONRPCHandler` / `DefaultRequestHandler` |
| Spring bean assembly, config binding, servlet transport, security mapping | KHEZY starter |
| Business logic (`onMessage`) | Consumer |
| Business-logic convenience (history seeding, emitter helpers) | KHEZY core |
| Tenant routing (`AgentExecutorRouter` / `AgentCardRouter`) | SDK contract + KHEZY Spring beans/consumer maps |
| Tenant/scope isolation policy | KHEZY default + consumer override |
| Stream lifecycle hook policy | SDK `TaskStreamLifecycleHook` + consumer |
| Persistence shape (JPA), pagination, finalized event | KHEZY store module |
| Payload encryption + key rotation | `khezy-crypto` (`StringEncryptor`, AAD, rotation) |
| Card discovery/cache, agent registry, multi-turn correlation | KHEZY client |
| HTTP client + SSE limits | KHEZY starter (SDK provider/transport) |
| LLM tool exposure | KHEZY spring-ai (optional) |
| Tracing | SDK OTel extras + optional KHEZY auto-config (v2.1) |

## 10. What v2 removes (migration map)

| v1 class/package | Disposition |
|---|---|
| `core/dispatch/A2ARequestDispatcher`, `dispatch/CallContext` | **delete** — use SDK `RequestHandler` |
| `core/executor/A2AAgentExecutor`, `A2AStreamingAgentExecutor`, `AgentEventSink`, `AgentExecutionContext`, `CancellationToken` | **delete** — use SDK `AgentExecutor`/`AgentEmitter`; add `AbstractA2AAgentExecutor` + `A2ARequest` |
| `core/store/A2ATaskStore`, `A2AEventQueue`, `PushNotificationSender`, `Subscription` | **delete** — use SDK stores/interfaces |
| `core/error/*` | **delete** — use SDK `A2AError` subclasses; normalize client failures in `SendOutcome` |
| `core/client/A2ARemoteAgentClient`, `A2AStreamingRemoteAgentClient` | **delete** — replaced by `A2AInvoker` + `A2AConnector` + `A2AClientOperations` |
| `core/identity/CallerIdentityResolver`, `executor/CallerIdentity` | **delete** — use SDK `User` via `A2ACallContextFactory` |
| `coredef/dispatch/DefaultA2ARequestDispatcher`, `store/InMemory*`, `store/Noop*`, `executor/*` | **delete** |
| `coredef/client/SdkA2ARemoteAgentClient` | **delete** — replaced by `SdkA2AInvoker` |
| `coredef/client/BearerTokenInterceptor` | **delete** — use SDK `AuthInterceptor`/`ClientCallInterceptor` |
| `starter/A2AAutoConfiguration` (dispatcher wiring) | **replace** — `A2AServerAutoConfiguration` |
| `starter/security/SecurityCallerIdentityResolver` | **replace** — `SpringSecurityA2ACallContextFactory` |
| `starter/web/{MessageController, TaskController, AgentCardController, JsonRpcEnvelope, JsonRpcParamMapper, HttpCallContext, SseAgentEventSink}` | **delete** — one `A2AJsonRpcController` + card controller using SDK `JSONRPCHandler` |
| `starter/web/SdkA2AJsonRpcController`, `SdkSseEventSink` | **rewrite** as `A2AJsonRpcController` + transport-neutral `A2AServerJsonRpcHandler` |
| `springai/ChatClientExecutors`, handlers | **delete** — replaced by skill/agent tools |
| `a2a-core-default` client model classes | **keep/port** — reference already has mature versions |

## 11. Implementation phases

| Phase | Outcome | Tasks |
|---|---|---|
| 0 | Reset modules, archive v1, upgrade SDK, add store module | `01` |
| 1 | Server business seam + call context + tenant auth contracts | `02` |
| 2 | Client contracts + defaults | `03`, `04` |
| 3 | JPA stores + encryption | `05` |
| 4 | Server runtime glue + starter + properties | `06` |
| 5 | Spring AI tooling | `07` |
| 6 | Samples, E2E, docs, release | `08` |

## 12. Non-goals (v2.0)

- Re-implementing any A2A protocol behavior.
- gRPC/REST **server** transports (JSON-RPC HTTP only).
- Arbitrary agent-name routing inside one server process. **Per-tenant** routing is in
  scope via `AgentExecutorRouter` / `AgentCardRouter`.
- Per-identity extended-card scoping (contract reserved, public/tenant scope only).
- Reactive API in core.
- OpenTelemetry auto-config (v2.1; SDK extras are usable manually).
- v0.3 protocol compatibility (optional SDK compat modules; not wired).
- Replicated queue manager / broker integration (consumer-provided `QueueManager`).
- UI.

## 13. Risks

| Risk | Mitigation |
|---|---|
| SDK version drift breaks reflection seams (`configProvider`, `agentCardRouter`) | One `SdkA2AConfigInjector`, fail-fast tests that assert SDK fields exist; pin SDK version; upgrade only on release tags. |
| SDK 1.4 multi-tenancy shares `TaskStore`/`QueueManager` | Persist `agent_scope` and enforce it in `TenantTaskAuthorizationProvider`; document that the SDK tenant is routing, not storage isolation. |
| `@PostConstruct` ordering in Spring (`initConfig` requires configProvider) | Inject config provider before returning the bean; integration test boots the full graph. |
| Encryption breaks queryability | Encrypt only payload columns; keep operational columns plaintext; document. |
| Key loss makes task content unreadable | Keyring + documented ops; never derive keys from task data. |
| Multi-turn ids lost across instances | `A2AConversationStore` SPI; JPA implementation is a natural follow-up. |
| Consumer expects v1 API | v2 is a breaking major release; migration map in §10 and README. |
