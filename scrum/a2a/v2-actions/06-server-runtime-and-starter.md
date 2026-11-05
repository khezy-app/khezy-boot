# A2A2-06 — Starter: SDK server runtime, HTTP transport, properties

**Prerequisites:** `00`, `02`, `04`, `05`.
**Design:** §5, §6.2.
**Reference:** `/mnt/data/refs-proj/kflowllmlap/a2a-common` (server transport adapters),
`.../nodes-base/.../a2a/server/A2AServerInstanceFactory` (SDK graph),
`examples/a2a-client-example` (Spring wiring).

## 1. Goal

Assemble the SDK server graph as overridable Spring beans, expose the two HTTP endpoints
(JSON-RPC + agent card), map Spring Security into the SDK call context, and wire the
client graph. **One `AgentCard` + one `AgentExecutor` must be enough to boot.**

Package root `io.github.khezyapp.a2a.starter.*`.

## 2. Server runtime

### 2.1 `server/A2AServerRuntime` / `A2AServerAutoConfiguration`

Beans, all `@ConditionalOnMissingBean` unless stated:

1. `AgentCard` — consumer bean wins; otherwise built from `A2AServerProperties.agentCard`
   (name, description, version, url, provider, skills, capabilities, supportedInterfaces
   JSONRPC + card path).
2. `AgentExecutor` — **consumer-provided**, required. If absent, the server auto-config
   fails fast with a clear message (or is `@ConditionalOnBean(AgentExecutor.class)` and
   stays quiet; choose fail-fast for DX).
3. `A2AStoreProvider` — from existing `TaskStore`/`TaskStateProvider`/
   `PushNotificationConfigStore` beans; if none, `A2AStoreProvider.inMemory()`.
4. `MainEventBus`.
5. `QueueManager` — `new InMemoryQueueManager(taskStateProvider, mainEventBus,
   taskStreamLifecycleHook)` where `TaskStreamLifecycleHook` is an optional bean
   (default SDK `DefaultTaskStreamLifecycleHook`). Consumers use it to close streams on
   timeout or subscriber limits (SDK 1.4 feature).
6. `MainEventBusProcessor` — `new MainEventBusProcessor(mainEventBus, taskStore,
   pushNotificationSender, queueManager)`; return as bean so `@PostConstruct start()`
   runs. **1.4.0** also starts it from the request-handler constructor and `start()` is
   idempotent; no manual start workaround is needed. Expose it as a bean so
   `@PreDestroy stop()` runs on shutdown.
7. `PushNotificationSender` — SDK `BasePushNotificationSender` (or no-op if push is
   disabled). Reuse the SDK default wherever possible.
8. `Executor a2aAgentExecutor` — virtual threads by default (`Executors
   .newVirtualThreadPerTaskExecutor()`); platform pool by property.
9. `Executor a2aEventConsumerExecutor` — cached daemon pool.
10. `A2AConfigProvider` — `SpringA2AConfigProvider(Environment, DefaultValuesConfigProvider)`:
    environment wins, SDK defaults fallback.
11. `TaskAuthorizationProvider` — `TenantTaskAuthorizationProvider` when
    `authorization.required=true` and/or a `TaskScopeLocator` bean exists.
12. `DefaultRequestHandler`:
    - build with the beans above;
    - optional `AgentExecutorRouter` bean → `builder.agentExecutorRouter(...)` (SDK
      multitenancy);
    - **before returning**, inject `configProvider` reflectively via
      `SdkA2AConfigInjector.setConfigProvider(handler, provider)` so the `@PostConstruct
      initConfig()` does not NPE and reads `a2a.server.blocking.*`;
    - call all `A2AServerCustomizer` beans after build;
    - `authorizationRequired`, `pushNotificationsEnabled`, `populateReferredTasks` from
      properties. Authorization default: `true` when Spring Security is present, else
      `false` + warning (SDK is fail-closed).
13. `JSONRPCHandler(agentCard, requestHandler, a2aAgentExecutor)`.
    - When an `AgentCardRouter` bean exists, set its `agentCardRouter` field via
      `SdkA2AConfigInjector` (the programmatic ctor has no router argument).
14. `A2AServerJsonRpcHandler` transport adapter (see §2.3).

Reflection is confined to `SdkA2AConfigInjector` (and possibly the push URL validator
config, if the SDK requires it). Fail fast with a clear message if the SDK field is
missing. Add a unit test that asserts the field exists in the pinned SDK.

### 2.2 Properties (`A2AServerProperties`, prefix `io.github.khezyapp.a2a.server`)

Per design §5.7: `enabled`, `path` (`/a2a`), `agentCard.*` (nested),
`authorization.required` (`auto` = true when Spring Security present, else false +
warning), `blocking.agentTimeout` (600s), `blocking.consumptionTimeout` (60s),
`blocking.reconciliationTimeout` (5s), `pushNotifications.enabled` (false),
`populateReferredTasks` (false), `multitenancy.enabled` (false),
`executor.virtualThreads` (true), `cardCache.maxAge` (60s).

Client-side properties (`io.github.khezyapp.a2a.client`): add `sse.max-line-length`
(default 1048576), `sse.max-buffer-lines` (1000), `sse.max-buffer-chars` (1048576),
`transport-parameters` (Map). Create one `A2AHttpClient` bean with
`A2AHttpClientFactory.createWithSseConfig(SSEParserConfig)` (honour a user-provided
`A2AHttpClient` bean), pass it to the card source and all transport registrars, and close
it on shutdown if `AutoCloseable`.

Server-side properties: add `multitenancy.enabled` and tenant card path support. Map
blocking timeouts into `SpringA2AConfigProvider` overrides under the exact SDK
property names (verified in 1.4.0 `DefaultRequestHandler`):

- `a2a.blocking.agent.timeout.seconds`
- `a2a.blocking.consumption.timeout.seconds`
- `a2a.blocking.reconciliation.timeout.seconds`
- `a2a.request-context.populate-referred-tasks`
- `a2a.authorization.required`
- `a2a.push-notification.enabled`

### 2.3 HTTP transport

Port the reference's transport-neutral adapter:

- `web/A2AServerJsonRpcHandler<T, R>`:
  - `handle(String body, R response, ServerCallContext ctx)`.
  - parse with `JSONRPCUtils.parseRequestBody(body, tenant)`.
  - dispatch every method to `JSONRPCHandler` (all methods, including push config and
    extended card; streaming via `Flow.Publisher`).
  - request-level errors: convert JSON mapping exceptions to `A2AErrorResponse`, report
    via optional `Consumer<A2AError>` after the response is written.
  - abstract `writeJson(R, String)` and `writeSse(R, Flow.Publisher<...>)`.
  - `ResponseFormatter` equivalent for result serialization (`JSONRPCUtils` +
    `ProtoUtils`); copy from the reference but re-package.
- `web/ServletA2AServerJsonRpcHandler` — writes `HttpServletResponse` JSON / SSE
  (`CountDownLatch`, request(1) flow control).
- `web/A2AJsonRpcController`:
  - `POST {path}/` (or `{path}`) accepts raw body, builds `ServerCallContext` via
    `A2ACallContextFactory<HttpServletRequest>`, delegates.
  - no `@RequestMapping` per method; the SDK routes.
  - accept `application/json` **and** `application/a2a+json` (SDK 1.4).
- `web/AgentCardController`:
  - `GET {path}/.well-known/agent-card.json` and
    `GET {path}/.well-known/{tenant}/agent-card.json`;
  - proto-JSON via `JsonFormat.printer().alwaysPrintFieldsWithNoPresence()
    .omittingInsignificantWhitespace().print(ProtoUtils.ToProto.agentCard(card))`;
  - ETag (hash of JSON) + `Cache-Control: max-age` + `304` on `If-None-Match`;
  - tenant-aware card via `AgentCardRouter.resolvePublicCard(tenant)`; unknown tenant →
    `TenantNotFoundException`/404.

### 2.4 Call context

- `web/ServletA2ACallContextFactory extends A2ACallContextFactory<HttpServletRequest>`:
  - user from Spring Security `SecurityContextHolder` (`AuthenticatedUser` with name +
    attributes) or `UnauthenticatedUser`;
  - headers map;
  - tenant from configurable header (default none);
  - scope from configurable header/path (default none).
- Provide `A2ACallContextFactory` as a `@ConditionalOnMissingBean` bean so consumers
  can override.
- No `CallerIdentityResolver`.

## 3. Client wiring

`client/A2AClientAutoConfiguration` (only when `io.github.khezyapp.a2a.client.enabled=true`):

- `A2AClientProperties` prefix `io.github.khezyapp.a2a.client`:
  `enabled` (true), `urls` (List<String>), `agentCardPath`, `authHeaders`, `callTimeout`
  (60s), `cardCacheTtl` (5m), `streaming`, `useClientPreference`,
  `conversationStateTtl` (1h), `maxConversations` (1000), `acceptedOutputModes`.
- Beans: `AgentCardSource`, `AgentCardResolver` (caching), `A2AClientFactory`,
  `RemoteAgentRegistry` (`destroyMethod = "close"`), `A2AInvoker`, `A2AConversationStore`
  (in-memory), `A2AConnector`, `A2AClientOperations`.
- No auto-registration of SDK `ClientCallInterceptor`s unless explicitly documented;
  expose a `List<ClientCallInterceptor>` injection point in `ClientBuildOptions`.

## 4. Auto-configuration files

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
io.github.khezyapp.a2a.starter.server.A2AServerAutoConfiguration
io.github.khezyapp.a2a.starter.web.A2AWebAutoConfiguration
io.github.khezyapp.a2a.starter.client.A2AClientAutoConfiguration
```

Store/JPA and Spring AI auto-configs live in their own modules and are picked up when
present. Never `@Import` conditionally-gated configs.

## 5. Tests

- `SdkA2AConfigInjectorTest` — pinned SDK fields exist (`configProvider`,
  `agentCardRouter`); provider/router injected; fail-fast path tested by a fake class?
  At minimum assert the fields are present.
- `A2AMultitenancyTest` — `AgentExecutorRouter` selects tenant executors and falls back
  to default; `RequestContext.getTenant()` is visible; tenant card path served; unknown
  tenant returns 404.
- `A2AServerAutoConfigurationTest` — reflection/annotation checks: every bean method
  has `@ConditionalOnMissingBean`; auto-config annotated; imports file contains the
  FQCNs. Avoid `ApplicationContextRunner` for the full graph.
- `SpringA2AConfigProviderTest` — environment wins, defaults fallback.
- `ServletA2ACallContextFactoryTest` — Spring Security auth → `AuthenticatedUser`,
  headers/tenant/scope/extensions/version.
- `AgentCardControllerTest` (`@WebMvcTest`) — ETag/304, proto-JSON shape, path.
- `A2AJsonRpcControllerTest` (`@WebMvcTest`) — `SendMessage`, `GetTask`, unknown method,
  malformed body; assert envelope shape and error codes.
- `A2AServerIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`) — echo `AgentExecutor`,
  in-memory stores:
  - card fetch;
  - raw SDK client `SendMessage` completes;
  - `GetTask` sees the task;
  - `SendStreamingMessage` yields at least one event;
  - `tasks/get` of another scope is denied as TaskNotFound (authorization on);
  - `application/a2a+json` content type accepted;
  - `TaskStreamLifecycleHook` bean is invoked on subscribe/unsubscribe.
  - Exclude Boot security auto-config or use `@AutoConfigureMockMvc(addFilters=false)`
    where appropriate (v1 gotcha still applies).

## 6. Verification

```bash
./gradlew :a2a-spring-boot-starter:check
```

## 7. Handoff

Record exact SDK config property keys, the reflective field name, controller mappings,
and any Boot 4/Jackson 3 friction. Task 08 rewrites samples against these endpoints and
beans. `graphify update .`.
