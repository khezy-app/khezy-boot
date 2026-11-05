# KHEZY A2A — Action Plan (Master Context)

> **Read this file first.** Every task file in `scrum/a2a/v2-actions/` references it.
> The design is `scrum/a2a/a2a-design-v2.md`.
> SDK 1.4 adoption decisions are in `../sdk-1.4-adoption.md`.

## 1. Goal

Replace the v1 "contract layer that re-implements the protocol" with a **thin,
Spring-native layer around the official A2A Java SDK**:

- the SDK keeps the wire model, task lifecycle, event bus, JSON-RPC routing, and
  authorization contract;
- KHEZY adds a server business-logic seam, Spring assembly, JSON-RPC/card transport,
  tenant isolation, JPA persistence **with encryption at rest**, client ergonomics
  (card cache, registry, multi-turn), and Spring AI tooling.

Consumer promise: **one `AgentCard` + one `AgentExecutor` is a working agent server**;
business logic is the only thing they write.

## 2. Verified facts (checked 2026-10-19)

### 2.1 SDK

- Group/artifact: `org.a2aproject.sdk:*`, baseline **`1.4.0.Final`** (latest release;
  the reference implementation uses 1.3.1 — upgrade). Do not pin the
  `1.4.1.Final-SNAPSHOT`; move to `1.4.1.Final` when released.
- Prefer the SDK BOM (`org.a2aproject.sdk:a2a-java-sdk-bom:1.4.0.Final`) via Gradle
  `platform(...)` so spec/server/client/transport versions stay aligned.
- Server runtime artifact: `a2a-java-sdk-server-common`.
- JSON-RPC transport artifact: `a2a-java-sdk-transport-jsonrpc` (contains
  `org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler`).
- Client artifacts: `a2a-java-sdk-client`, `a2a-java-sdk-client-transport-jsonrpc`,
  optionally `a2a-java-sdk-client-transport-rest`.
- Proto-JSON helpers: `a2a-java-sdk-spec-grpc` (`ProtoUtils`, `JSONRPCUtils`).

Key signatures in 1.4.0 (verified with `javap` / sources):

```text
AgentExecutor.execute(RequestContext, AgentEmitter) throws A2AError
AgentExecutor.cancel(RequestContext, AgentEmitter) throws A2AError      // default
RequestContext: getTaskId, getContextId, getTask, getMessage,
                getConfiguration, getMetadata, getCallContext, getTenant, getUserInput
AgentEmitter: updateStatus, addArtifact, complete/fail/cancel/reject/submit/
              startWork/requiresInput/requiresAuth, sendMessage, addTask,
              taskBuilder, messageBuilder
TaskStore: save(Task, boolean), get(String), delete(String), list(ListTasksParams, ServerCallContext)
TaskStateProvider: isTaskActive(String), isTaskFinalized(String)
PushNotificationConfigStore: setInfo, getInfo, deleteInfo, getProtocolVersion(s)
TaskAuthorizationProvider: checkRead/checkWrite/checkCreate/isTaskRecorded/recordOwnership
ServerCallContext: (User, Map<String,Object> state, Set<String> extensions[, version])
DefaultRequestHandler.Builder: agentExecutor, taskStore, queueManager, pushConfigStore,
    mainEventBusProcessor, executor, eventConsumerExecutor, authorizationProvider,
    agentExecutorRouter, authorizationRequired, populateReferredTasks,
    pushNotificationsEnabled
JSONRPCHandler: onMessageSend, onMessageSendStream, onCancelTask, onSubscribeToTask,
    onGetTask, onListTasks, push-config CRUD, onGetExtendedCardRequest,
    getAgentCard([tenant]), authorizeTaskAccess
AgentExecutorRouter.resolve(tenant)               // server-common multitenancy
AgentCardRouter.resolvePublicCard/resolveExtendedCard(tenant)
RequestContext.getTenant()
MainEventBusProcessor.start()                     // public + idempotent since 1.4
TaskStreamLifecycleHook                          // InMemoryQueueManager ctor
A2AHttpClientProvider / A2AHttpClientFactory.createWithSseConfig(SSEParserConfig)
A2ACardResolver                                  // tenant path, custom path, fallback URL
TaskPushNotificationConfig.id                     // nullable since 1.4
```

### 2.2 SDK lifecycle gotchas (binding)

1. `DefaultRequestHandler` has `@PostConstruct initConfig()` reading
   `A2AConfigProvider` and overwriting the builder's hardcoded timeouts
   (5s agent / 2s consumption / 1s reconciliation). When the handler is a Spring bean,
   Spring runs it — so inject `configProvider` **before** returning the bean.
   Expose `a2a.server.blocking.*` via `A2AConfigProvider`, not reflection.
2. `MainEventBusProcessor` has `@PostConstruct start()`. It must run or persisted
   tasks never update. **1.4.0** makes `start()` public/idempotent and the request
   handler constructor starts it; Spring also calls `@PostConstruct`. No manual start.
   Do not let the processor bean bypass `@PreDestroy stop()`.
3. `DefaultRequestHandler` builder requires `agentExecutor`, `taskStore`,
   `queueManager`, `mainEventBusProcessor`, `executor`, `eventConsumerExecutor`.
   `pushConfigStore` is mandatory when push notifications are enabled.
4. The builder still does **not** set `configProvider`; `@PostConstruct initConfig()`
   needs it. Inject it reflectively in the bean factory (`SdkA2AConfigInjector`) or the
   Spring context NPEs. Do not use reflection for timeouts.
5. `JSONRPCHandler` programmatic constructor is `(AgentCard, RequestHandler, Executor)`.
   It has **no** `AgentCardRouter` argument (only the CDI constructor does); set the
   `agentCardRouter` field reflectively for tenant-aware cards.
6. Agent card must be serialized with SDK **proto-JSON** (`JsonFormat` +
   `ProtoUtils.ToProto.agentCard`) — Jackson serialization produces a shape the SDK
   client rejects.
7. The SDK client posts JSON-RPC to the agent **base URL root** (`POST /a2a`), not to
   `/message/send`. Accept `application/json` and `application/a2a+json`.
8. `a2a.authorization.required` defaults to `true` in SDK config (fail-closed). The
   starter maps `io.github.khezyapp.a2a.server.authorization.required`; default it to
   `true` when Spring Security is present, otherwise `false` with a startup warning.
9. SDK tenant values are validated to `[a-zA-Z0-9_.-]`; a composite scope such as
   `prod:agent` must ride in the app-owned state key `A2AContextKeys.AGENT_SCOPE`.
   Tenant (`RequestContext.getTenant()`) is routing; `agent_scope` is persistence
   isolation.
10. Push config: `TaskPushNotificationConfig.id` is nullable. Omitted id = create-only
    with `taskId` as default; if it exists, reject. Never blindly upsert default configs.
11. `MainEventBusProcessor` excludes the current status message from task history since
    1.4; adjust assertions that expected it in history.
12. Blank `taskId`/`contextId` in `SendMessage` is treated as absent since 1.4. Keep
    sending nulls, not empty strings.

### 2.3 Build environment

- Java 17 target, JDK 21 toolchain, Checkstyle 13.1.0.
- Composite build: each module has its own `settings.gradle` with
  `includeBuild("../../build-logic")` (path depth depends on module location).
- Register every module in root `settings.gradle` via `includeBuild(...)`.
- No `java-library` directly; use `khezy.java-library` / `khezy.springboot-library`.

## 3. Module layout (target)

| Path | rootProject.name | Plugin | Key deps |
|---|---|---|---|
| `ai/a2a/a2a-core` | `a2a-core` | `khezy.java-library` | `org.a2aproject.sdk:a2a-java-sdk-server-common:1.4.0.Final` (api), `a2a-java-sdk-client` (api) |
| `ai/a2a/a2a-core-default` | `a2a-core-default` | `khezy.java-library` | `a2a-core`, `sdk-client-transport-jsonrpc`, `sdk-client-transport-rest` |
| `ai/a2a/a2a-store-jpa` **(new)** | `a2a-store-jpa` | `khezy.java-library` | `a2a-core`, `spring-boot-starter-data-jpa` |
| `ai/a2a/a2a-spring-ai` | `a2a-spring-ai` | `khezy.java-library` | `a2a-core`, `spring-ai-client-chat` |
| `ai/a2a/a2a-spring-boot-starter` | `a2a-spring-boot-starter` | `khezy.springboot-library` | `a2a-core-default`, `sdk-transport-jsonrpc`, `sdk-spec-grpc`, webmvc, security (compileOnly), optional jpa/spring-ai |
| `ai/a2a/samples/a2a-server-sample` | `a2a-server-sample` | `khezy.springboot` | starter |
| `ai/a2a/samples/a2a-client-sample` | `a2a-client-sample` | `khezy.springboot` | starter (client side) |

Keep the existing directory names to minimize churn. Rename contents, not paths.

## 4. Task graph

| # | File | Task | Prerequisites |
|---|---|---|---|
| 1 | `01-module-reset.md` | Delete v1 reimplementation, upgrade SDK, add `a2a-store-jpa`, update root/README | `00` |
| 2 | `02-core-server-seam.md` | `AbstractA2AAgentExecutor` + `A2ARequest`, `A2ACallContextFactory`, `A2AStoreProvider`, `TaskScopeLocator`, `TenantTaskAuthorizationProvider`, `A2AServerCustomizer` | `00`, `01` |
| 3 | `03-core-client-contracts.md` | `RemoteAgent`, `RemoteSkill`, `SendRequest/Options/Outcome`, `AgentCardResolver`, `A2AInvoker`, `A2AConnector`, `A2AConversationStore`, `A2AClientOperations` | `00`, `01` |
| 4 | `04-core-default.md` | SDK invoker, connector, in-memory conversation store, card source/cache, client factory, registry | `00`, `03` |
| 5 | `05-store-jpa-encryption.md` | JPA stores + `A2ACipher` + AES-GCM + keyring + auto-config + tests | `00`, `02` |
| 6 | `06-server-runtime-and-starter.md` | SDK graph, `A2AJsonRpcController`, card controller, call-context factory, config provider, properties, client auto-config | `00`, `02`, `04`, `05` |
| 7 | `07-spring-ai-tools.md` | Skill/agent tools, prompt builder, result formatter | `00`, `03`, `04` |
| 8 | `08-samples-and-release.md` | Rewrite samples, E2E, READMEs, release notes, full verify | all |
| 9 | `09-optional-sdk-features.md` | OpenTelemetry, TCK/ITK, compat-0.3 evaluation, replicated queue hook | `06`, `08` |

### 4.1 SDK 1.4 features adopted in v2.0

| Feature | Where |
|---|---|
| Multi-tenancy (`AgentExecutorRouter`, `AgentCardRouter`, tenant card path) | `06` |
| HTTP client bean + `SSEParserConfig` | `04`, `06` |
| Manual event-bus start fix | `06` |
| Push config nullable id / create-only default | `05` |
| `TaskStreamLifecycleHook` bean | `06` |
| Spring `TaskFinalizedEvent` from JPA store | `05` |
| `application/a2a+json` | `06` |
| Fail-closed auth default | `06` |
| Functional `AgentExecutor` / lambda support | `02` |
| OpenTelemetry, TCK/ITK, compat-0.3, replicated queue | `09` (optional) |

## 5. Cleanup manifest (execute in `01`, verify in `08`)

Delete (v1 reimplementation):

```
a2a-core/src/main/java/.../core/client/*                 (old client ports)
a2a-core/src/main/java/.../core/dispatch/*
a2a-core/src/main/java/.../core/error/*
a2a-core/src/main/java/.../core/executor/*               (old executor ports)
a2a-core/src/main/java/.../core/identity/*
a2a-core/src/main/java/.../core/store/*
a2a-core/src/test/java/.../AbstractA2AAgentExecutorTest.java, ErrorTaxonomyTest.java
a2a-core-default/src/main/java/.../coredef/dispatch/*
a2a-core-default/src/main/java/.../coredef/executor/*
a2a-core-default/src/main/java/.../coredef/store/*
a2a-core-default/src/main/java/.../coredef/client/*
a2a-core-default/src/test/java/.../coredef/dispatch/*, store/*
a2a-spring-ai/src/main/java/.../springai/ChatClientExecutors.java, handlers
a2a-spring-boot-starter/src/main/java/.../starter/A2AAutoConfiguration.java
a2a-spring-boot-starter/src/main/java/.../starter/security/*
a2a-spring-boot-starter/src/main/java/.../starter/web/*
```

Keep/rewrite:

- `a2a-spring-boot-starter/src/main/resources/META-INF/spring/...AutoConfiguration.imports`
  (rewrite FQCNs; never `@Import` conditionally-gated classes).
- `A2AProperties` → split into `A2AServerProperties`, `A2AClientProperties`,
  `A2AStoreProperties`.
- Sample apps → rewrite against the new SDK-based server/client.

Do **not** delete `CallerIdentityTest` until the replacement identity tests exist; port
its intent to `A2ACallContextFactoryTest`.

## 6. Shared conventions

1. **Never re-model A2A types.** Method signatures use `org.a2aproject.sdk.spec.*`,
   `org.a2aproject.sdk.server.*`, or KHEZY behavior records. No duplicate `Message`,
   `Task`, `AgentCard`, `Part`, or error hierarchy.
2. **Business seam is optional.** Raw SDK `AgentExecutor` must always work.
3. **Every auto-configured bean is `@ConditionalOnMissingBean`.**
4. **Auto-config registration** only in
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   (one FQCN per line). Never `spring.factories` for auto-config; never `@Import` a
   conditionally-gated config.
5. **Java style:** `final` on all params/locals (`final var x`), 4 spaces, 120 cols,
   Egyptian braces, braces on all blocks, no star imports,
   `Objects.requireNonNull` for validation.
6. **Spring Boot 4:** Jackson 3 (`tools.jackson.*`) if serializing ourselves; web tests
   use `spring-boot-starter-webmvc-test`; `@MockitoBean` (not `@MockBean`).
7. **Tests:** unit tests for behavior; one full-context integration test for the server
   graph; reflection/annotation tests only where a full context would need multi-level
   SDK mocks.
8. **After every task:** run tests + checkstyle, update docs if shapes changed, run
   `graphify update .`.

## 7. Verification commands

```bash
./gradlew :a2a-core:check
./gradlew :a2a-core-default:check
./gradlew :a2a-store-jpa:check
./gradlew :a2a-spring-ai:check
./gradlew :a2a-spring-boot-starter:check
./gradlew unittest
./gradlew checkstyleMains checkstyleTests
./gradlew -p ai/a2a/samples/a2a-server-sample bootRun
```

Note: the v1 task files claimed `./gradlew :ai:a2a:<module>:check`; the working forms
are `:a2a-<module>:check` from root or `-p ai/a2a/<module> <task>`.

## 8. Definition of Done

1. v1 reimplementation classes deleted; no compile references remain.
2. Server: one `AgentExecutor` + one `AgentCard` boots a working JSON-RPC endpoint.
3. Client: discover a card, send a message, resume an `INPUT_REQUIRED` task, all
   through KHEZY contracts.
4. JPA store round-trips tasks and push configs; payload columns are ciphertext when a
   key is configured; legacy plaintext still readable.
5. `unittest` and `checkstyleMains checkstyleTests` green.
6. README + release notes updated; `graphify update .` run.
