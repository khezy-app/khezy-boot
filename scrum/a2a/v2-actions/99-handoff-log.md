# A2A v2 — Task Handoff Log

> **Protocol (binding for every agent working on this plan).**
> Context is handed off **in this file, never in chat**. Chat history is re-sent on every
> turn and dies with the session; this file is what the next session actually reads.
>
> **When you START a task:** read (in this order, and nothing more):
> 1. `00-plan-overview.md` (master context),
> 2. your own task file in this directory,
> 3. only the design sections your task file cites from `../a2a-design-v2.md`,
> 4. the **last entry** in this log for each prerequisite task.
>
> Then inspect only the source files your task tells you to touch/create. Do not
> re-explore the repository.
>
> **When you FINISH a task:** append one entry below using the template, run the task's
> verification commands, and run `graphify update .`. Do **not** paste the entry into
> chat; reply only with the task id, status, and verification result.
>
> **If you are BLOCKED:** append a `partial` entry explaining the blocker and exact next
> action, then stop. Do not guess or silently deviate.

## Entry template

```markdown
## A2A2-XX — <task title>

- **Status:** completed | partial (explain)
- **Date:** YYYY-MM-DD
- **Verification:** <commands run + result, e.g. `./gradlew :a2a-core:check` PASS>
- **What was built:** <1–3 bullets: files/packages created, decisions taken>
- **Deviations from task spec:** <anything that differs from the task file — downstream
  tasks rely on REAL shapes, not planned ones>
- **Gotchas for next tasks:** <non-obvious constraints, pitfalls, things Checkstyle or
  tests forced>
```

## Progress

| Task | Status |
|---|---|
| A2A2-01 module reset | completed |
| A2A2-02 core server seam | completed |
| A2A2-03 core client contracts | completed |
| A2A2-04 core default | completed |
| A2A2-05 store JPA + encryption | completed |
| A2A2-06 server runtime + starter | completed |
| A2A2-07 Spring AI tools | completed |
| A2A2-08 samples + release | completed |
| A2A2-09 optional SDK features | completed |

---

<!-- Entries below. Append only; never rewrite a completed entry. -->

## A2A2-01 — Module reset, SDK upgrade, store module

- **Status:** completed
- **Date:** 2026-10-19
- **Verification:** `./gradlew :a2a-core:compileJava :a2a-core-default:compileJava :a2a-spring-ai:compileJava` PASS; `./gradlew :a2a-store-jpa:compileJava` PASS (`NO-SOURCE`, expected — no classes yet). Extra sanity checks: `:a2a-spring-boot-starter:compileJava` PASS, `:a2a-spring-boot-starter:test` PASS (1 test), `checkstyleMain` PASS on all five modules. Dependency report confirms SDK `1.4.0.Final` and JPA `4.1.0` resolve.
- **What was built:**
  - Executed the full v1 cleanup manifest (all `core/{client,dispatch,error,identity,store,executor}`, `coredef/{dispatch,executor,store,client}`, Spring AI executors/handlers, starter `security/`, `web/`, the two auto-config classes and their tests/import tests). `A2AProperties`, `A2APropertiesTest` and `TestBootConfiguration` kept per the task file.
  - All four existing library builds bumped to `2.0.0`; SDK baseline set to `1.4.0.Final` through `platform("org.a2aproject.sdk:a2a-java-sdk-bom:1.4.0.Final")` (per-artifact versions dropped). `a2a-core` now declares `server-common` + `client` as `api`; `a2a-core-default` adds `client-transport-jsonrpc` + `client-transport-rest`.
  - New `ai/a2a/a2a-store-jpa` build (`khezy.java-library`, `2.0.0`, `a2a-core` + `spring-boot-starter-data-jpa`), registered in root `settings.gradle`; package dirs `.../a2a/store/jpa/{task,push,scope,crypto}` exist (each carries a `.gitkeep` so the empty packages survive git).
  - Docs: `ai/a2a/README.md` reduced to a WIP pointer (full README in A2A2-08); new `scrum/a2a/README.md` index marks `_archive-v1/` superseded.
  - `AutoConfiguration.imports` now contains only a comment — all three v1 configs are gone; A2A2-06 repopulates it.
- **Deviations from task spec:**
  - `a2a-store-jpa` applies `io.spring.dependency-management` 1.1.7 and imports `spring-boot-dependencies:4.1.0`. `khezy.java-library` does not provide a Boot BOM, so the unversioned `spring-boot-starter-data-jpa` coordinate could not resolve otherwise (same pattern as `db-query/query-jpa-spec`).
  - Starter keeps `compileOnly "org.springframework.ai:spring-ai-client-chat:2.0.1"` (explicit version; no Spring AI BOM in the starter). `testImplementation spring-boot-starter-security` was dropped because no remaining starter test needs it.
  - `CallerIdentityTest` was intentionally **not** deleted (design v2 §10 says keep until the replacement identity test exists). It still imports the deleted `core.executor.CallerIdentity`, so `:a2a-core:compileTestJava` and therefore `:a2a-core:check` are RED until A2A2-02 ports its intent to `A2ACallContextFactoryTest` and removes it.
- **Gotchas for next tasks:**
  - SDK `1.4.0.Final` resolves from Maven Central; BOM is on the `api` configuration in `a2a-core`, `a2a-core-default` and the starter, so downstream modules can add SDK artifacts without versions.
  - Both sample apps still import deleted v1 APIs (`MathAgentExecutor`/`AnonymousIdentityConfiguration` on `core.executor.*`, `HostAgentRunner` on `coredef.client.SdkA2ARemoteAgentClient`). They do **not** compile on purpose; A2A2-08 rewrites them. Do not run root `check`/`unittest` before then.
  - Sample builds declare dependencies as `io.github.khezyapp:a2a-core` with no version; composite substitution still resolves them after the 2.0.0 bump. A2A2-08 should decide versions when rewriting.
  - Starter `build.gradle` now has `compileOnly` security, JPA and Spring AI; tests added in A2A2-06 that touch those APIs must add matching `testImplementation` entries (compileOnly is not on the test classpath).
  - `a2a-spring-ai` keeps `khezy.java-use-mockito` (no classes yet) for A2A2-07.

## A2A2-02 — `a2a-core`: server seam and isolation contracts

- **Status:** completed
- **Date:** 2026-10-19
- **Verification:** `./gradlew :a2a-core:check` PASS (37 tests, `checkstyleMain` + `checkstyleTest` clean).
- **What was built:**
  - Added `io.github.khezyapp.a2a.core.server`: `A2ARequest`, `AbstractA2AAgentExecutor`, `A2ACallContextFactory<T>`, `A2AStoreProvider`, `TaskScopeLocator`, `TenantTaskAuthorizationProvider`, `A2AServerCustomizer`, `A2AContextKeys.AGENT_SCOPE`.
  - `A2ARequest` delegates every response to `AgentEmitter` (`sendMessage`, `addArtifact`, `complete`, `requiresInput`, `requiresAuth`, `fail`, `cancel`) and exposes SDK `RequestContext`/`AgentEmitter` escape hatches.
  - Tests: `A2ARequestTest`, `AbstractA2AAgentExecutorTest`, `TenantTaskAuthorizationProviderTest`, `A2ACallContextFactoryTest`, `A2AStoreProviderTest`. Ported the old `CallerIdentityTest` intent (empty user -> `UnauthenticatedUser`) into `A2ACallContextFactoryTest` and deleted `CallerIdentityTest.java`; `a2a-core` test compilation is no longer red.
  - `a2a-core` now applies `khezy.java-use-mockito`.
- **Deviations from task spec:**
  - `A2AServerCustomizer` is **handler-only**: `void customize(DefaultRequestHandler handler)` (the final instruction in §2.6 wins). Task 06 should call customizers after `Builder.build()`, ordered by Spring `@Order`.
  - `A2ARequest.artifact(name, description, text)` cannot use `AgentEmitter.addArtifact` to carry a description: the real 1.4 signature is `addArtifact(List<Part<?>>, @Nullable String artifactId, @Nullable String name, @Nullable Map<String,Object> metadata)` and has no description argument. The 2-arg overload uses `addArtifact(parts, null, name, Map.of())`; the 3-arg overload emits a full `TaskArtifactUpdateEvent` (generated artifact id) via `emitter.emitEvent(...)` so the description is preserved.
  - `a2a-core/build.gradle` adds `implementation "org.a2aproject.sdk:a2a-java-sdk-transport-jsonrpc"`. `JSONRPCContextKeys` lives only in the server transport artifact (not `server-common`), and the task requires the SDK key rather than a copied literal. It is `implementation` because only string constants are inlined at runtime.
  - `A2ACallContextFactory.extractHeaderValues` defaults to splitting `extractHeader(request, name)` on commas (empty when absent) instead of a bare `List.of()`. This keeps the two header hooks coherent for subclasses that only implement raw header lookup.
  - `A2ARequest.caller()` filters on `user.isAuthenticated()` (which covers `UnauthenticatedUser`), so custom `User` implementations that report unauthenticated are also excluded.
- **Gotchas for next tasks:**
  - `RequestContext.getTaskId()`/`getContextId()` are non-null in normal SDK construction, but `getMessage()`/`getTask()`/`getMetadata()`/`getTenant()`/`getCallContext()` are nullable. `A2ARequest` handles the nulls; code in task 06 must not assume `attributes()` is non-empty.
  - `AbstractA2AAgentExecutor.execute` seeds history with `List.of(context.getMessage())` exactly as specified. A message-less `execute` would NPE; message/send always supplies one.
  - `TenantTaskAuthorizationProvider` requires **both** the call-state `A2AContextKeys.AGENT_SCOPE` and the task metadata scope. Missing/blank/different scopes all deny; the SDK then surfaces `TaskNotFoundError`. `checkRead(LIST_TASKS)` always allows (the store must still filter per task).
  - `TaskScopeLocator.fromTaskStore(...)` returns `null` when the task is absent or metadata has no string scope; task 05's JPA locator should override this with a denormalized lookup.
  - `A2AStoreProvider.inMemory()` shares one `InMemoryTaskStore` between `taskStore()` and `taskStateProvider()`; do not construct two stores in task 06 when falling back to memory.
  - `A2ARequest` and the SDK JSON-RPC wrapper `org.a2aproject.sdk.jsonrpc.common.wrappers.A2ARequest` share a simple name. In task 06, import the KHEZY one explicitly or fully qualify the wrapper.

## A2A2-03 — `a2a-core`: client contracts

- **Status:** completed
- **Date:** 2026-10-19
- **Verification:** `./gradlew :a2a-core:check` PASS (87 tests, `checkstyleMain` + `checkstyleTest` clean).
- **What was built:**
  - `io.github.khezyapp.a2a.core.client`: `RemoteAgent`, `RemoteSkill`, `RemoteArtifact`, `TaskRef`, `SendOutcome`, `RemotePartExtractor`, `AgentCardResolver`, `A2AInvoker`, `A2AConversationStore`, `A2AConnector`, `A2AClientOperations`, `ClientBuildOptions`.
  - `io.github.khezyapp.a2a.core.client.invoke`: `SendOptions` (`Strategy {BLOCKING, POLL, STREAM}`, `DEFAULT_TIMEOUT = 60s`, builder + `toBuilder`) and `SendRequest` (exact 12-component shape from the task, defensive copies, builder + `toBuilder`, `hasExplicitTaskRef`).
  - `A2AConnector` is concrete in `a2a-core`; it injects stored ids only when the builder has no explicit ref, serializes per conversation through a bounded (512) access-ordered lock map, and records interrupted/pending/settled outcomes. Error outcomes leave the store untouched.
  - Tests: `SendOutcomeTest`, `SendRequestTest`, `SendOptionsTest`, `RemotePartExtractorTest`, `A2AConnectorTest` (incl. two-thread serialization), plus a small `RemoteAgentTest`.
- **Deviations from task spec:**
  - `A2ACardScope` was **not** added. It is optional in A2A2-03; the design reserves scoped extended cards for v2.1 and nothing in tasks 04/06/07 consumes it yet.
  - `RemoteAgent.selectedInterface()` returns `Optional<AgentInterface>` (preferred transport match, else first interface) and `tenant()` returns `Optional<String>` derived from that interface. Both are `Optional`, not nullable values.
  - `A2AClientOperations` is an instantiable, stateless instance class (not a static utility) so task 06 can expose it as a bean; methods take SDK params objects and pass a `null` `ClientCallContext`.
  - `A2AConversationStore` concrete signature is `recordInterrupted/recordPending(conversationId, agentName, skillId, contextId, taskId)` and `recordSettled(conversationId, agentName, skillId, contextId)`. `ref()` must return `TaskRef.empty()` (never `null`); the connector also tolerates `null`.
  - `ClientBuildOptions` has exactly two components: `A2AHttpClient httpClient`, `Map<String,Object> transportParameters`, plus `of(httpClient)`.
  - Added `RemoteAgentTest` (not in the task's test list) to cover name/skills/interface/tenant selection.
- **Gotchas for next tasks:**
  - `SendOutcome.isFinal()` is also `true` for a message-only outcome (`state == null`, no error), because a standalone `Message` ends the turn. `isPending()` is only `true` for a non-final, non-interrupted `TaskState`.
  - `SendRequest` keeps `agent` (ergonomics); `A2AConnector.invoke(RemoteAgent, Builder, ...)` always overwrites the builder's agent with the method argument.
  - `SendRequest.Builder.contextId/taskId` normalize blank values to `null`; blank values do **not** set `hasExplicitTaskRef()`. `toBuilder()` preserves whether the original request had explicit ids, so stored ids are not re-injected into an explicit continuation.
  - The connector normalizes a null `skillId` to `""` for store keys. Implementations of `A2AConversationStore` must treat `""` (not `null`) as “no skill”.
  - `RemotePartExtractor` returns file **name** for `FilePart` (falling back to MIME); SDK `FileContent` is sealed and requires a name, so the MIME fallback is defensive only. `DataPart` uses `String.valueOf(data)` — structured values are not JSON-serialized.
  - `A2AClientOperations` deliberately passes `null` call context; per-call headers/state are currently only available through `SendOptions` on the invoker path. Task 06 can add call-context overloads if needed.
  - `ClientBuildOptions` copies `transportParameters` into an unmodifiable `LinkedHashMap` (values may be null); `A2AHttpClient` lives in `org.a2aproject.sdk.client.http` and is available transitively from `a2a-java-sdk-client`.

## A2A2-04 — `a2a-core-default`: SDK client defaults

- **Status:** completed
- **Date:** 2026-10-20
- **Verification:** `./gradlew :a2a-core-default:check` PASS (34 tests; `checkstyleMain` + `checkstyleTest` clean). Also re-ran `./gradlew :a2a-core:check` PASS after extending `ClientBuildOptions`; `graphify update .` PASS.
- **What was built:**
  - `io.github.khezyapp.a2a.coredef.client.card`: `SdkAgentCardSource` (SDK `Utils` URL rules incl. tenant/custom path/well-known stripping/404 fallback; conditional GET; proto-JSON via `JSONRPCUtils.parseJsonString` + `ProtoUtils.FromProto.agentCard`; `ETag`/`Last-Modified`/`Cache-Control: max-age`), `CachingAgentCardResolver` (5-minute default TTL, conditional revalidation, stale-if-error, `invalidate`/`clear`), `AgentCardSource`, `CardCacheHeader`, `CardFetch`, `CardFetchStatus`, `AgentCardCacheEntry`.
  - `...coredef.client`: `A2AClientFactory` (request → card → `["text"]` output modes, streaming gated on card capability, every discovered transport, post-build `setParameters`, optional `AuthInterceptor` from `CredentialService`), `RemoteAgentRegistry` (fail-soft URL registration, case-insensitive name fallback, skill lookup, refresh, unregister, idempotent close).
  - `...coredef.client.transport`: `A2AClientTransport` SPI, `A2AClientTransports.discover()` (JSON-RPC + REST built in, ServiceLoader additions), `JsonRpcClientTransport`, `RestClientTransport`.
  - `...coredef.client.invoke.SdkA2AInvoker`: SDK `Message`/`MessageSendParams`/`ClientCallContext` construction, skill in message metadata, tenant fallback, first settled event, timeout/interrupt/error → `SendOutcome.error`, never closes the shared client.
  - `...coredef.client.store.InMemoryA2AConversationStore`: 1h default TTL, 1000-entry LRU, `agent|skill` keying, settled drops task id.
  - Tests: 5 test classes (34 tests) + `TestFixtures` + test-only ServiceLoader transport.
- **Deviations from task spec:**
  - `ClientBuildOptions` in `a2a-core` was expanded from 2 to 8 components (`acceptedOutputModes`, `streaming`, `polling`, `useClientPreference`, `interceptors`, `credentialService`, `httpClient`, `transportParameters`) plus `builder()`; the old 2-arg constructor and `of(httpClient)` are retained so A2A2-03 call sites keep compiling. Documented because A2A2-03 handoff said “exactly two components”.
  - Added `AgentCardSource` (public functional interface) so `CachingAgentCardResolverTest` can use a fake source; `SdkAgentCardSource` implements it.
  - `SdkA2AInvoker` calls the SDK `Client.sendMessage(MessageSendParams, consumers, errorHandler, ClientCallContext)` overload and decides whether to wait for streaming events from `agent.card().capabilities().streaming()`.
  - `A2AClientTransportsTest` contributes a test-only `ServiceLoadedTestTransport` via `src/test/resources/META-INF/services/...` (it returns `ClientTransport.class` as a unique key) to verify ServiceLoader additions without a real gRPC dependency.
  - `SdkA2AInvokerTest` uses `TaskEvent` and `TaskUpdateEvent` to cover terminal/interrupted behaviour; `returnImmediately` is covered with a WORKING `TaskEvent`.
- **Gotchas for next tasks:**
  - SDK event types observed/used: `MessageEvent`, `TaskEvent`, `TaskUpdateEvent`; all normalize through `SendOutcome.ofMessage`/`ofTask`. A standalone `Message` is final (`state == null`).
  - Card parsing requires `a2a-java-sdk-spec-grpc`; added as `implementation` in `a2a-core-default`. Use `JSONRPCUtils.parseJsonString(body, org.a2aproject.sdk.grpc.AgentCard.newBuilder(), "", true)` + `ProtoUtils.FromProto.agentCard(...)` — plain Jackson/Gson will not produce a valid card.
  - `SdkAgentCardSource` mirrors `A2ACardResolver` URL rules with public `Utils` helpers; raw `.json` URLs rely on the 404 fallback to the original base URL, not a `.json` special case.
  - `CachingAgentCardResolver` treats `Cache-Control: no-cache`/`no-store` as TTL zero; `max-age` overrides the 5-minute default. `invalidate`/`clear` are the only eviction APIs; there is no per-URL refresh.
  - `InMemoryA2AConversationStore` treats `""` as “no skill” (matching A2A2-03); `recordSettled` with a null context removes the entry. `size()` includes not-yet-purged expired entries.
  - `RemoteAgentRegistry` is keyed by base URL; duplicate names are possible, `find` returns the first match. `refresh` keeps the old agent if resolution/client creation fails. `close` clears the map and is idempotent.
  - `A2AClientFactory` applies the same interceptor list to every transport and registers every discovered transport; task 06 must supply an `A2AHttpClient` (with `SSEParserConfig` where relevant) in `ClientBuildOptions`.
  - `A2AClientTransports.discover()` loads `A2AClientTransport` via `ServiceLoader`; task 06/09 can add gRPC by shipping an implementation + service file.
  - `SdkA2AInvoker` does not close the client (registry owns it); timeout errors do not cancel the SDK request, only the local wait.
  - If task 06 builds a non-streaming client (`ClientBuildOptions.streaming(false)`) for a streaming-capable card and a task is interrupted with `stopOnInterrupt(false)`, the blocking SDK call returns and the invoker returns the last observed pending/interrupted outcome instead of waiting for more events.

## A2A2-05 — `a2a-store-jpa`: persistence + encryption at rest

- **Status:** completed
- **Date:** 2026-10-20
- **Verification:** `./gradlew :a2a-store-jpa:check :khezy-crypto:check` PASS (`a2a-store-jpa` 26 tests: 3 cipher-adapter, 4 encryption-at-rest, 5 auto-config, 1 rotation, 13 store; `khezy-crypto` 39 tests; `checkstyleMain` + `checkstyleTest` clean on both).
- **What was built:**
  - `io.github.khezyapp.a2a.store.jpa`: `A2AJpaStoreAutoConfiguration` (additive `EntityScanPackages` BFPP + `@EnableJpaRepositories`, all beans `@ConditionalOnMissingBean`, registered in `AutoConfiguration.imports`) and `A2AStoreProperties` (`io.github.khezyapp.a2a.store`).
  - `...store.jpa.task`: `A2ATaskEntity` (payload ciphertext + denormalized `context_id/state/status_timestamp/agent_scope/finalized_at`), `A2ATaskRepository` (spec executor + scalar `findAgentScopeByTaskId`), `SpringA2ATaskStore` (keyset list, scope filter, 15s grace, event publish, SDK exception translation), `JpaTaskScopeLocator`, `A2ATaskFinalizedEvent`.
  - `...store.jpa.push`: `A2APushConfigId`, `A2APushNotificationConfigEntity`, `A2APushNotificationConfigRepository`, `SpringA2APushNotificationConfigStore` (SDK 1.4 omitted-id create-only, per-task limit, `PageToken` pagination, protocol versions).
  - `...store.jpa.crypto`: `A2ACipher`, `StringEncryptorA2ACipher` (delegates to `StringEncryptor`), `A2ACipherContexts` owning the AAD strings. No crypto code, keyring, or envelope classes in this module.
  - Payloads use SDK `JsonUtil`; AAD: task `CipherContext.of("a2a-task", "a2a:task:" + taskId)`, push `CipherContext.of("a2a-push-config", "a2a:push:" + taskId + ":" + configId)`. Legacy plaintext (no `kzy:` prefix) is returned unchanged on read.
  - Tests: `A2ASpringJpaStoreTest`, `A2AEncryptionAtRestTest`, `A2ACipherAdapterTest`, `A2ARotationMigrationTest`, plus `A2AJpaStoreAutoConfigurationTest`; test app in `io.github.khezyapp.a2a.testapp` carries its own entity/repository to prove the additive scan.
- **Deviations from task spec:**
  - `A2AStoreProperties.type` defaults to `JPA`, not the design table's `memory`: this module is only active when JPA is present, which is exactly the design's “auto-detected if JPA on classpath” case.
  - The required 2-arg constructors are kept, but production auto-config uses richer overloads: `SpringA2ATaskStore(repo, encryptor, Duration gracePeriod, ApplicationEventPublisher)` and `SpringA2APushNotificationConfigStore(repo, encryptor, int maxPerTask)`. The 2-arg store defaults to 15s/no-op publisher, the 2-arg push store to the SDK default limit.
  - Entities are deliberately dumb: `A2ATaskEntity.fromTask(task, encryptedPayload)`, `updateFrom(task, encryptedPayload)`, `toTask(plaintextPayload)` and the push equivalent. Serialization/encryption stays in the store, as required; the entity API differs from a no-arg `fromTask(Task)` reading.
  - `status_timestamp` and `created_at` are truncated to millis before persisting. SDK `PageToken` serializes millis, so without truncation a keyset cursor would skip rows created in the same millisecond with sub-millisecond nanos.
  - Push `getInfo` returns the stored config unchanged (including its stored tenant) instead of rebuilding it with `params.tenant()`; this preserves fields the caller did not supply.
  - `JpaTaskScopeLocator` lives in `task/` (as §3.2 lists it); the empty `scope/` placeholder package from task 01 was removed.
  - Auto-config is fail-closed at class level with `@ConditionalOnBean(StringEncryptor.class)` and `afterName = "io.github.khezyapp.crypto.spring.CryptoAutoConfiguration"`. No `StringEncryptor` → no store beans and no store repositories (nothing is stored as plaintext). There is no compile dependency on the crypto starter.
  - Added `A2AJpaStoreAutoConfigurationTest` (reflection/resource assertions) and a legacy-plaintext read test beyond the four named test classes.
  - Test infrastructure adds `spring-boot-starter-test` + H2 to `a2a-store-jpa`; tests define a `StringEncryptor` bean with `khezy-crypto` defaults rather than pulling the crypto starter.
- **Gotchas for next tasks:**
  - Task 06 auto-config FQCN: `io.github.khezyapp.a2a.store.jpa.A2AJpaStoreAutoConfiguration`. Bean names/types: `springA2ATaskStore` (`SpringA2ATaskStore`, also a `TaskStore` + `TaskStateProvider`), `jpaTaskScopeLocator` (`TaskScopeLocator`), `springA2APushNotificationConfigStore` (`PushNotificationConfigStore`). Because the bean types are the concrete classes, a starter `@ConditionalOnMissingBean(TaskStore.class)` / `PushNotificationConfigStore.class` will correctly back off when this module is present.
  - The store auto-config appears only when a `StringEncryptor` bean exists (crypto starter bean name `cryptoStringEncryptor`, or any user bean). Task 06 must not assume JPA stores exist just because `a2a-store-jpa` is on the classpath; check bean presence / `A2AStoreProvider`.
  - `A2ATaskFinalizedEvent` is a plain record, not a Spring `ApplicationEvent`. `ApplicationListener<A2ATaskFinalizedEvent>` does **not** compile (`E extends ApplicationEvent`); consumers must use `@EventListener` (or `@TransactionalEventListener`). The event is published after `repository.save` but inside the store transaction.
  - `ReEncryptionExecutor.run(...)` takes **one** `CipherContext` for the whole batch, while the A2A AAD is per row. Run it once per row with that row's `A2ACipherContexts.task(...)` / `pushConfig(...)`, or call `DefaultReEncryptor.reEncrypt(value, rowContext)` directly. `A2ARotationMigrationTest` demonstrates the per-row pattern; do not batch task rows under one context.
  - AAD values are fixed and must not change: `a2a:task:<taskId>` (purpose `a2a-task`), `a2a:push:<taskId>:<configId>` (purpose `a2a-push-config`). The push config id used in AAD is always the resolved one (task id when omitted).
  - `list` scope comes from `ServerCallContext.getState().get(A2AContextKeys.AGENT_SCOPE)`: `null` context = unscoped (internal/event-bus call), context without the key = blank = fail-closed (no rows), non-blank = equality filter. Tenant (`params.tenant()`) is not used for persistence isolation.
  - `@DataJpaTest` does **not** pick up this auto-configuration automatically: `@DataJpaTest` only imports Boot's JPA slice auto-configs. Import `A2AJpaStoreAutoConfiguration` (and provide/import a `StringEncryptor` bean) or use `@SpringBootTest`; the additive entity scan runs once the auto-config is actually loaded.
  - `A2AStoreProperties` fields: `type` (`MEMORY|JPA`, default `JPA`), `task.grace-period` (`Duration`, default 15s), `push.max-per-task` (default 100). The push store honors non-positive values by falling back to the SDK default.
  - `getProtocolVersion` falls back to SDK `AgentInterface.CURRENT_PROTOCOL_VERSION`, which is `1.0` in SDK 1.4 — the value is `1.0`, not `1.4`.
  - Auto-config and H2 tests run against H2 `MODE=PostgreSQL`; `task_data` uses `columnDefinition = "TEXT"`.

## A2A2-06 — Starter: SDK server runtime, HTTP transport, properties

- **Status:** completed
- **Date:** 2026-10-20
- **Verification:** `./gradlew :a2a-spring-boot-starter:check` PASS (39 tests: 5 injector, 4 config-provider, 2 multitenancy, 8 auto-config, 5 call-context, 4 card MVC, 5 JSON-RPC MVC, 6 full-context integration; `checkstyleMain` + `checkstyleTest` clean). `graphify update .` PASS.
- **What was built:**
  - `io.github.khezyapp.a2a.starter`: `A2AServerProperties` (prefix `io.github.khezyapp.a2a.server`) and `A2AClientProperties` (prefix `io.github.khezyapp.a2a.client`). Deleted the v1 `A2AProperties` + test.
  - `...starter.server`: `A2AServerAutoConfiguration` (card from properties, in-memory/JPA store provider, event bus/queue/processor, push sender, virtual-thread + event-consumer executors, `SpringA2AConfigProvider`, tenant authorization, tenant routers, `DefaultRequestHandler`, `JSONRPCHandler`), `SdkA2AConfigInjector`, `SpringA2AConfigProvider`, `MapAgentExecutorRouter`, `MapAgentCardRouter`, `A2AExecutors`.
  - `...starter.web`: `A2AServerJsonRpcHandler` (transport-neutral, all SDK methods incl. push config + extended card), `ServletA2AServerJsonRpcHandler` (JSON + SSE), `A2AJsonRpcController` (`POST /a2a` and `/a2a/`), `AgentCardController` (proto-JSON, ETag/304, tenant path), `ServletA2ACallContextFactory`, `SpringSecurityUserResolver`, `ResponseFormatter`, `FlowSupport`, `JsonRpcTransportMetadata` + `META-INF/services/org.a2aproject.sdk.server.TransportMetadata`.
  - `...starter.client.A2AClientAutoConfiguration`: one `A2AHttpClient` (SSE limits), card source/resolver, `ClientBuildOptions`, client factory, registry (`destroyMethod=close`), invoker, conversation store, connector, operations. HTTP client is closed via a `DisposableBean` (the SDK JDK client is not `AutoCloseable`).
  - `AutoConfiguration.imports` registers the three configs; server auto-config is ordered `afterName` the JPA store auto-config. Starter README rewritten for the v2 surface.
- **Deviations from task spec:**
  - `A2AServerProperties.authorization.required` is a `String` (`auto|true|false`, default `auto`), not a Boolean, so both the design's `auto` literal and explicit booleans bind. Invalid values fail fast.
  - `SpringA2AConfigProvider` takes an `A2AConfigProvider defaults` (not the concrete `DefaultValuesConfigProvider`) so it is unit-testable; the bean is still fed the SDK `DefaultValuesConfigProvider`. Precedence: explicit `a2a.*` environment key > values mapped from starter properties > SDK defaults.
  - Reflection is broader than the two spec'd fields. `SdkA2AConfigInjector` also sets `authorizationProviderInstance`/`agentExecutorRouterInstance` (and the resolved fields) because `initConfig()` overwrites builder values from CDI `Instance` fields. Fields: `configProvider`, `authorizationProviderInstance`, `authorizationProvider`, `agentExecutorRouterInstance`, `agentExecutorRouter` (DefaultRequestHandler) and `agentCardRouter` (JSONRPCHandler).
  - Added `call-context.tenant-header` / `call-context.scope-header` properties (needed by §2.4 and used by the integration test). Added `executor.platform-threads` / `platform-queue-capacity`.
  - Added `MapAgentExecutorRouter` / `MapAgentCardRouter` (design §5.8 "simple map configuration"); default router beans are created only when `multitenancy.enabled=true`.
  - `A2AServerAutoConfiguration` is `@ConditionalOnWebApplication(SERVLET)` (v2.0 transports are servlet). A missing `AgentExecutor` fails when `DefaultRequestHandler` is created with an explicit message; multiple executors without a router also fail with guidance to use a router or `@Primary`.
  - `DefaultValuesConfigProvider` is registered `@Bean(autowireCandidate = false)` and looked up by name before building `SpringA2AConfigProvider`; the SDK handler's `configProvider` is a `jakarta.inject.Inject` field that Spring autowires, so two candidates would break startup. The `a2aConfigProvider` bean is `@ConditionalOnMissingBean(A2AConfigProvider.class)` for consumer overrides.
  - `ServletA2ACallContextFactory` delegates identity to a `Function<HttpServletRequest, Optional<User>>`; `SpringSecurityUserResolver` is the security-backed implementation and is only instantiated by a nested `@ConditionalOnClass` config, so the factory loads without Spring Security.
  - Both controllers are `@RestController` (Spring 7 `RequestMappingHandlerMapping.isHandler` only accepts `@Controller`; `@RequestMapping` alone is no longer enough). Auto-config beans remain `@ConditionalOnMissingBean`, so a component-scanned duplicate backs off.
  - `RemoteAgentRegistry` is registered as the concrete `RemoteAgentRegistry` bean (so `destroyMethod="close"` is valid); consumers should inject the concrete type or `ObjectProvider`.
  - Added an integration-only `spring-security-core` test dependency; no Boot security auto-config runs in tests. `A2AClientProperties.call-timeout` is currently informational: per-call `SendOptions` win and `a2a-core` has no default-options hook.
- **Gotchas for next tasks:**
  - **`@PostConstruct initConfig()` overwrites the builder's `authorizationProvider` and `agentExecutorRouter`** (it resolves them from CDI `Instance` fields). The injector sets the `*Instance` fields to `FixedInstance` values before Spring runs init. Never rely on `Builder.authorizationProvider(...)`/`agentExecutorRouter(...)` alone.
  - `A2AServerCustomizer` beans run **after `build()` but before `initConfig()`**; use beans (not customizers) for provider/router and timeouts (timeouts only come from `A2AConfigProvider`).
  - SDK 1.4 **method names are PascalCase** (`SendMessage`, `SendStreamingMessage`, `GetTask`, `ListTasks`, `CancelTask`, `SubscribeToTask`, `CreateTaskPushNotificationConfig`, ...), not `message/send`/`tasks/get`.
  - `SdkA2AConfigProvider` SDK keys: `a2a.blocking.agent.timeout.seconds`, `a2a.blocking.consumption.timeout.seconds`, `a2a.blocking.reconciliation.timeout.seconds`, `a2a.request-context.populate-referred-tasks`, `a2a.authorization.required`, `a2a.push-notification.enabled` (note the singular `push-notification`).
  - Raw `application/a2a+json` requests must send `A2A-Version: 1.0`; without it the SDK rejects with `VERSION_NOT_SUPPORTED` for 0.3. SDK clients add it automatically.
  - Card proto-JSON **omits the top-level `url`** (only `supportedInterfaces[].url` is printed). Clients discover the endpoint from `supportedInterfaces`; set `agent-card.url` to the full JSON-RPC URL (base + path).
  - `JsonRpcTransportMetadata` must stay registered in `META-INF/services/...TransportMetadata`, otherwise `AgentCardValidator` rejects a card that advertises JSONRPC.
  - Agent-scope isolation relies on task metadata `agent_scope`: the consumer executor must seed it (the integration test does via `emitter.taskBuilder().metadata(...)`); the JPA store denormalizes it. Without it `TenantTaskAuthorizationProvider` denies every read.
  - Bean names to reuse in samples: `a2aConfigProvider`, `a2aAgentExecutor`, `a2aEventConsumerExecutor`, `a2aDefaultValuesConfigProvider`. Stores come from `a2a-store-jpa` (`springA2ATaskStore`, `jpaTaskScopeLocator`, `springA2APushNotificationConfigStore`) and are picked up automatically when a `StringEncryptor` bean exists.
  - Endpoints for task 08: `POST /a2a` (and `/a2a/`), `GET /a2a/.well-known/agent-card.json`, `GET /a2a/.well-known/{tenant}/agent-card.json`. The path is configurable via `io.github.khezyapp.a2a.server.path`.
  - Virtual threads are created reflectively (the module compiles with `--release 17`), falling back to a platform pool with a warning. `destroyMethod="shutdown"` applies to both executor beans.
  - The SSE servlet hook blocks the request thread until the stream ends and writes `request(1)` back-pressure; client-side streaming is asynchronous in the SDK, so tests must wait for a settled event.
  - Starter `check` currently covers the whole module; samples still do not compile (A2A2-08 owns them). Client-only apps should set `io.github.khezyapp.a2a.server.enabled=false`.

## A2A2-07 — `a2a-spring-ai`: client tooling for LLMs

- **Status:** completed
- **Date:** 2026-10-20
- **Verification:** `./gradlew :a2a-spring-ai:check` PASS (30 tests: 8 formatter, 10 skill-tool factory, 5 agent tools, 7 prompt builder; `checkstyleMain` + `checkstyleTest` clean). `./gradlew :a2a-spring-boot-starter:check` PASS (46 tests = previous 39 + 7 tooling auto-config; `checkstyleMain` + `checkstyleTest` clean). `graphify update .` PASS.
- **What was built:**
  - `io.github.khezyapp.a2a.springai`: `A2AToolContextKeys` (`CONVERSATION_ID = "conversationId"`), `SkillInput` (`record SkillInput(String task)`), `A2AResultFormatter` (manual JSON, per-text-field truncation, default 8000), `A2ASkillToolFactory` (`toolCallbacks()` cached once, `toolCallbacks(List<RemoteSkill>)` uncached), `A2AAgentTools` (`@Tool sendToAgent(agentName, task, ToolContext)` + `listRemoteAgents()`), `A2APromptProvider`, `A2APromptBuilder` (defaults + builder overrides), plus package-private `A2AToolErrors` for structured JSON errors.
  - `io.github.khezyapp.a2a.starter.springai.A2AToolingAutoConfiguration`: `@ConditionalOnClass({ChatClient.class, A2ASkillToolFactory.class})`, `@ConditionalOnProperty(io.github.khezyapp.a2a.client.enabled, matchIfMissing=true)`, all five beans `@ConditionalOnMissingBean`, optional `ChatClientBuilderCustomizer` only with `attach-tools-to-chat-client=true`. Registered in `AutoConfiguration.imports`; `A2AClientProperties.attachToolsToChatClient` added.
  - Docs: `a2a-spring-ai/README.md` rewritten for the new tooling surface (the old one documented the deleted `ChatClient`-as-executor classes); starter README gained a Spring AI section and the new property.
- **Deviations from task spec:**
  - `a2a-spring-ai` now depends on **`a2a-core-default`** (not just `a2a-core`), because `A2ASkillToolFactory`/`A2AAgentTools` take `RemoteAgentRegistry`. `spring-ai-client-chat` was promoted `implementation` → `api` (it appears in public signatures) and `settings.gradle` adds `includeBuild("../a2a-core-default")`.
  - The starter declares `a2a-spring-ai` as **`compileOnly`** (+ `testImplementation` for tests), so consumers opt in by adding the module. The config's `@ConditionalOnClass` therefore lists `A2ASkillToolFactory` in addition to `ChatClient`, so a Spring AI app without the module is skipped instead of failing with `NoClassDefFoundError`.
  - Missing `ToolContext.CONVERSATION_ID` returns a structured JSON error instead of a fallback conversation id. `A2AConnector` rejects blank ids, and a shared fallback would leak one chat's task into another.
  - `A2APromptBuilder` renders its `{agent}/{id}/{name}/{description}/{tags}/{examples}` placeholders with a local single-pass replace instead of Spring AI `StTemplateRenderer`; card text containing braces must never be re-scanned.
  - `A2AResultFormatter` truncates each free-text field (message, artifact text) to `maxChars`, not the whole JSON document (truncating the document would produce invalid JSON).
  - `A2AToolErrors` is an extra package-private class (unknown agent / missing conversation id) so both tool classes share the same JSON error shape.
  - Added `A2AToolingAutoConfigurationTest` (the task's Spring AI auth-config test) in the starter package `...starter.springai`.
- **Gotchas for next tasks:**
  - **Spring AI 2.0.1 API used:** `org.springframework.ai.tool.ToolCallback`, `org.springframework.ai.tool.function.FunctionToolCallback.builder(name, BiFunction<I, ToolContext, O>)` + `.description/.inputType/.inputSchema`, `org.springframework.ai.chat.model.ToolContext` (final, `getContext()`), `org.springframework.ai.tool.annotation.Tool`/`ToolParam`, `org.springframework.ai.chat.client.ChatClient` + `ChatClientBuilderCustomizer`. `ToolCallback.call(String json, ToolContext)` is what the tests drive.
  - `A2ASkillToolFactory.toolCallbacks()` caches the registry snapshot; agents registered later are not picked up until the factory is recreated or `toolCallbacks(List)` is used.
  - Tool names are `<sanitize(agent)>__<sanitize(skillId)>`, capped at 64 chars, collision suffix `_2`, `_3`, …; empty names become `unnamed`.
  - Conversation id is mandatory for both tool paths; the host must pass `A2AToolContextKeys.CONVERSATION_ID` in `ToolContext` (the model never supplies it).
  - The starter test classpath now contains Spring AI (testImplementation), so `A2AServerIntegrationTest` also loads the tooling auto-config. The "no Spring AI" path is guarded only by `@ConditionalOnClass`; A2A2-08 can sanity-check it in the sample by omitting `a2a-spring-ai`.
  - `a2a-spring-ai` tests use JUnit Jupiter assertions (no AssertJ available from `khezy.java-library`); the starter keeps AssertJ. A shared `TestAgents` fixture builds real card-backed `RemoteAgent`s (SDK `AgentCard.builder()` requires `capabilities`, default input/output modes, skills and `supportedInterfaces`).
  - `A2AClientProperties.attachToolsToChatClient` defaults to `false`; do not flip it in the sample unless the sample really wants every `ChatClient` to see the tools.

## A2A2-08 — Samples, E2E, docs, release

- **Status:** completed
- **Date:** 2026-10-20
- **Verification:**
  - `./gradlew :a2a-core:check :a2a-core-default:check :a2a-store-jpa:check :a2a-spring-ai:check :a2a-spring-boot-starter:check` PASS.
  - `./gradlew unittest` PASS (includes both samples: `a2a-server-sample` 7 tests, client has no tests).
  - `./gradlew checkstyleMains checkstyleTests` PASS.
  - `./gradlew -p ai/a2a/samples/a2a-server-sample check` PASS; `-p ai/a2a/samples/a2a-client-sample check` PASS.
  - Live E2E: server `bootRun` on `:8080`; `GET /a2a/.well-known/agent-card.json` returned the Math Agent card; `POST /a2a` returned `TASK_STATE_COMPLETED "5"` with the `result` artifact; client `bootRun` discovered the agent, sent `2 + 3`, then resumed an `INPUT_REQUIRED` `2 +` task with `3` and completed at `5`.
  - Tenant profile: `--spring.profiles.active=tenant` — `/a2a/.well-known/acme/agent-card.json` returned the tenant card, unknown tenant returned `404`, tenant `SendMessage` completed.
  - H2 encryption profile: `--spring.profiles.active=h2` persisted one task; `select task_data from a2a_tasks` showed a `kzy:v1:AES-256-GCM:...` ciphertext and `task_data like '%sup3r-s3cret%'` counted 0 leaks.
  - `graphify update .` PASS.
- **What was built:**
  - Rewrote `a2a-server-sample`: `MathAgentExecutor extends AbstractA2AAgentExecutor` (`<int> <op> <int>`, `requireInput`, history-based multi-turn, `result` artifact + completion), `MathAgentCardConfiguration` with four skills, optional `StreamLifecycleLoggingConfiguration`, opt-in `TenantSampleConfiguration` (`MapAgentExecutorRouter` + `MapAgentCardRouter`), default in-memory profile and `application-h2.properties` (JPA + H2 + crypto key).
  - Rewrote `a2a-client-sample`: `HostAgentRunner` uses `RemoteAgentRegistry` + `A2AConnector` for discovery, a normal send and an `INPUT_REQUIRED` resume; fail-soft; small SSE line limit.
  - Docs: rewrote `ai/a2a/README.md`, `a2a-core`, `a2a-core-default`, `a2a-spring-ai` (unchanged content verified), `a2a-spring-boot-starter` (added raw JSON-RPC wire note), new `a2a-store-jpa/README.md`, rewrote `samples/README.md`, `scrum/a2a/README.md`, `_archive-v1/README.md`, and added `ai/a2a/RELEASE-NOTES-v2.md` (breaking changes, SDK 1.4 features, migration map, gaps).
  - Final coordinates: all library modules `io.github.khezyapp:*:2.0.0`; samples `0.0.1-SNAPSHOT`; SDK `1.4.0.Final`; Spring Boot `4.1.0`; crypto starter coordinate is `io.github.khezyapp:crypto-spring-boot-starter:1.0.0` (project name, not the `khezy-` prefixed name some docs use).
- **Deviations from task spec:**
  - The task's curl payload does not work against SDK 1.4. Working form (recorded in `samples/README.md` and starter README): `A2A-Version: 1.0` is required for **both** `application/json` and `application/a2a+json`, a text part is `{"text":"..."}` (not `{"kind":"text",...}`), and `messageId` is required. The method is `SendMessage` at `POST /a2a`.
  - Tenant routing is an opt-in `tenant` profile rather than always-on, so the default server still has exactly one executor bean as §2.1 requires.
  - The H2 profile adds `a2a-store-jpa`, `crypto-spring-boot-starter`, H2 and an explicit `runtimeOnly 'net.bytebuddy:byte-buddy'` (Hibernate 7's runtime bytecode provider was not resolved transitively in the sample's `khezy.springboot` build).
  - The server sample uses an explicit `AgentCard` bean (task §2.1) built from `A2AServerProperties`; card name/description/version/URL stay in properties.
  - Added a regression test for the multi-turn history case (the SDK puts the current message in task history before the executor runs; the executor must not append it twice).
- **Gotchas for next tasks:**
  - `application/json` requests are treated as protocol 0.3 unless `A2A-Version: 1.0` is present; SDK Java clients add it automatically. Any new raw-wire docs/tests must include it.
  - Proto-JSON `Part` uses the oneof field name (`{"text":...}`, `{"data":...}`, `{"file":...}`), not the Java `kind`.
  - Sample composites must include every KHEZY build they touch: the server sample includes `a2a-core`, `a2a-core-default`, `a2a-spring-ai`, `a2a-store-jpa`, `a2a-spring-boot-starter`, `khezy-crypto` and `crypto-spring-boot-starter`; otherwise `compileOnly`/`api` coordinates do not substitute.
  - The default sample profile disables crypto and excludes `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration` and `DataJpaRepositoriesAutoConfiguration`; `application-h2.properties` clears the exclusion list with an empty value.
  - The H2 profile uses `jdbc:h2:file:/tmp/khezy-a2a-sample;MODE=PostgreSQL;AUTO_SERVER=TRUE`, so an external H2 shell can read `a2a_tasks` while the app runs.
  - Known gaps unchanged: no gRPC/REST server transport, no per-identity extended-card scoping, no durable client conversation store, no OpenTelemetry auto-config/TCK/v0.3/replicated queue (A2A2-09 scope).

## A2A2-09 — Optional SDK features (v2.1)

- **Status:** completed
- **Date:** 2026-10-20
- **Verification:**
  - `./gradlew :a2a-observability-otel:check` PASS (12 tests; `checkstyleMain` + `checkstyleTest` clean).
  - `./gradlew :a2a-store-jpa:check` PASS (32 tests incl. 6 conversation-store tests).
  - `./gradlew :a2a-spring-boot-starter:check` PASS (56 tests incl. 8 conformance + 1 queue-override; checkstyle clean).
  - `./gradlew unittest` PASS (all included builds, including both samples).
  - `./gradlew checkstyleMains checkstyleTests` PASS.
  - `./gradlew :a2a-server-sample:check` PASS (version bump sanity).
  - `graphify update .` PASS (5337 nodes / 15398 edges).
- **What was built:**
  - **New optional module `ai/a2a/a2a-observability-otel` (`2.1.0`, `khezy.springboot-library`).** Package `io.github.khezyapp.a2a.observability.otel`:
    `A2AObservabilityProperties` (`io.github.khezyapp.a2a.observability.otel.*`; `enabled=true`,
    `capture-request=false`, `capture-response=false`), `server/OpenTelemetryRequestHandler`
    (no-op subclass of the SDK CDI decorator), `client/OpenTelemetryTransportParameters`
    (merges the two SDK wrapper keys), `client/OpenTelemetryClientBuildOptionsPostProcessor`,
    and `A2AObservabilityAutoConfiguration` + `AutoConfiguration.imports`.
  - **Server tracing.** The OTel auto-config registers a `@Primary RequestHandler` around the
    starter's `DefaultRequestHandler`. To make that possible, `A2AServerAutoConfiguration.jsonRpcHandler`
    now accepts `RequestHandler` instead of `DefaultRequestHandler` (the bean graph is otherwise unchanged).
  - **Client tracing.** A `BeanPostProcessor` rewrites the `ClientBuildOptions` bean, adding
    `OpenTelemetryClientTransportWrapper.OTEL_TRACER_KEY` and
    `OpenTelemetryClientPropagatorTransportWrapper.OTEL_OPEN_TELEMETRY_KEY`; the SDK ServiceLoader
    wrappers then trace calls and inject W3C `traceparent`. No starter compile dependency.
  - **Durable conversation store.** In `a2a-store-jpa` (bumped to `2.1.0`): `conversation/A2AConversationId`
    (`@Embeddable`), `A2AConversationEntity` (`a2a_conversations`, `context_id`, `task_id`,
    `updated_at`, `expires_at`), `A2AConversationRepository`, `SpringA2AConversationStore`
    (interface implementation, TTL on read, `purgeExpired()`), `store.conversation.ttl` (default `1h`),
    and bean `springA2AConversationStore`. `A2AClientAutoConfiguration` is now
    `afterName = A2AJpaStoreAutoConfiguration` so the JPA store replaces the in-memory one.
  - **Interoperability conformance.** `A2AJsonRpcConformanceTest` drives the SDK JSON-RPC client
    against the Spring server for `SendMessage`, `SendStreamingMessage`, `GetTask`, `ListTasks`,
    `CancelTask`, `SubscribeToTask`, push-config CRUD and `GetExtendedAgentCard` (raw HTTP for the
    last one, see gotchas), plus card discovery.
  - **Replicated queue hook.** `A2AQueueManagerOverrideTest` boots the server auto-config with a
    consumer `QueueManager` and asserts it is the single bean injected into `DefaultRequestHandler`.
  - Docs: new `a2a-observability-otel/README.md`; updated `ai/a2a/README.md`,
    `a2a-spring-boot-starter/README.md`, `a2a-store-jpa/README.md`, `ai/a2a/RELEASE-NOTES-v2.md`.
- **Deviations from task spec:**
  - The SDK artifact names in the task file are wrong: the real coordinates are
    `org.a2aproject.sdk:a2a-java-sdk-opentelemetry-server`, `-client`,
    `-client-propagation` (not `a2a-extras-opentelemetry-*`). They are `compileOnly` in the
    module; consumers add them (and an OTel SDK) explicitly. `opentelemetry-api` is `api`.
  - OTel client wiring uses a `BeanPostProcessor`, not a new `ClientBuildOptions` bean, so the
    module depends only on `a2a-core` (no starter dependency) and keeps the starter's property
    binding/interceptors intact. The task's mention of `A2AClientTransports` applying
    `setParameters` is implemented per transport (`JsonRpcClientTransport`, `RestClientTransport`).
  - `a2a-store-jpa` version bumped `2.0.0` → `2.1.0` (new public store); server sample coordinate
    updated. Other modules stay `2.0.0`.
  - Conversation entries are stored in plaintext (routing ids only, no payload). The in-memory
    LRU bound (`max-conversations`) is not reproduced in JPA; expiry is enforced on read and by
    `purgeExpired()`. `store.conversation.ttl` is independent of the client's
    `conversation-state-ttl`.
  - No request/response payload capture by default; enabling the properties sets the SDK
    JVM-wide system properties (`org.a2aproject.sdk.server.extract.request|response`).
  - **compat-0.3 not implemented** (evaluated only): the SDK ships `JSONRPCHandler_v0_3`,
    `Convert_v0_3_To10RequestHandler` and `JsonUtil_v0_3`, so a programmatic adapter is
    technically possible, but the reference transport is Quarkus/Vert.x and this servlet
    controller only speaks v1.0. `A2A-Version: 0.3` is rejected by the v1.0 parser. Documented
    as unsupported; deploy v0.3 agents side by side.
  - **Upstream TCK/ITK not vendored.** `a2a-java-sdk-itk` only publishes `1.0.0`/`1.1.0`
    (not aligned with SDK 1.4), and `a2a-java-sdk-tck-sut` is a system-under-test, not a test
    harness. The in-repo conformance test covers the JSON-RPC methods our transport exposes.
- **Gotchas for next tasks:**
  - OTel auto-config is ordered `afterName` `io.github.khezyapp.a2a.starter.server.A2AServerAutoConfiguration`;
    keep that ordering or the `@ConditionalOnBean(DefaultRequestHandler.class)` (bean-method level)
    may be evaluated before the handler exists. The traced handler is `@Primary` because
    `DefaultRequestHandler` also implements `RequestHandler`.
  - The OTel server wrapper requires the SDK decorator class to load; the module guards it with
    `@ConditionalOnClass(name = ...)` so the classpath can omit the extras.
  - `a2a-java-sdk-opentelemetry-client` / `-client-propagation` activate only via their
    `META-INF/services/...ClientTransportWrapper`; both must be on the runtime classpath or
    propagation silently does nothing.
  - SDK 1.4 client bug: `JSONRPCUtils.parseError` has no branch for `GetExtendedAgentCard`, so any
    error response for that method surfaces as `MethodNotFoundJsonMappingException` on the client.
    Test extended-card routing over raw HTTP; do not assert the SDK client's error text.
  - SDK 1.4 client bug: `Client.listTasks` throws NPE when `ListTasksParams.tenant()` is null
    (`ListTasksParamsMapperImpl` calls proto `setTenant(null)`). Set `tenant("")` (or a tenant).
  - `A2AConversationRepository` custom `@Modifying @Query` methods need `@Transactional` (added)
    or manual store instances outside a proxy will fail with `TransactionRequiredException`.
  - `springA2AConversationStore` only exists when `a2a-store-jpa` is active (JPA + a
    `StringEncryptor` bean). The client auto-config falls back to `InMemoryA2AConversationStore`
    otherwise.
  - When a consumer replaces `QueueManager`, it must pass `A2AStoreProvider.taskStateProvider()`;
    the starter cannot enforce this for a consumer-created bean. Verified override shape:
    `A2AQueueManagerOverrideTest`.
  - Conformance test leaves an open SDK subscription briefly; it cancels the task and closes the
    client. Keep timeouts generous if the SDK changes stream teardown.
