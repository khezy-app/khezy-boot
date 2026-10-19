# A2A Java SDK 1.4 — Adoption Review

> Research target: `/mnt/data/opensource/a2a-java` (checked 2026-10-19).
> Latest release tag: **`v1.4.0.Final`** (2026-09-28). `HEAD` is
> **`1.4.1.Final-SNAPSHOT`** with 31 commits after the release.
> Our design v2 was pinned to `1.3.1.Final`; this review decides what to adopt.

## 1. Repo state

| Item | Finding |
|---|---|
| Released baseline | `v1.4.0.Final` |
| In-progress | `1.4.1.Final-SNAPSHOT` (bug fixes only) |
| Last feature release before it | `v1.3.0.Final` (multi-tenancy, fail-closed auth, security hardening) |
| `v1.3.1` / `v1.3.2` | Patch fixes; `1.3.1` is what our design/reference use |
| Recommended pin | **`1.4.0.Final`** now; move to `1.4.1.Final` when released |

`1.4.1-SNAPSHOT` contains a relevant robustness fix: JSON-RPC now handles omitted
`params` and non-object request bodies. It is not a release yet; do not pin a snapshot in
library metadata. Note it for the next patch upgrade.

## 2. New functionality since 1.3.1

### 2.1 Definitely adopt (small, high value)

| Feature | What changed | Impact on our design |
|---|---|---|
| **Multi-tenancy routing** | `AgentExecutorRouter` (builder hook) routes per tenant; `RequestContext.getTenant()`; tenant-aware public cards at `/.well-known/{tenant}/agent-card.json`; `AgentCardRouter` for tenant public/extended cards. `@Tenant` moved from extras to `server-common` (breaking in 1.4). | Add tenant resolution to `A2ACallContextFactory`; expose an `AgentExecutorRouter` bean; expose an `AgentCardRouter` bean and inject it into `JSONRPCHandler` (programmatic ctor lacks it); serve tenant card paths. Removes "multi-tenant is future". |
| **Manual wiring starts the event bus** | `DefaultRequestHandler` constructor now calls `mainEventBusProcessor.start()`; `start()` is public and idempotent. | Drop the "call start manually / use runtime factory" workaround. Keep the Spring bean; double-start is safe. |
| **`@PostConstruct initConfig` still needs config** | Builder still does not set `configProvider`; `initConfig()` reads it for timeouts/auth/push. | Keep `SdkA2AConfigInjector` to set `configProvider` before returning the `@Bean`; this remains the only reason for reflection on the request handler. |
| **SSE parser configuration** | `SSEParserConfig` (`maxLineLength`, `maxBufferLines`, `maxBufferChars`), default 1 MB line/event, `BoundedLineAccumulator`; `A2AHttpClientFactory.createWithSseConfig(...)`. | Create one `A2AHttpClient` bean with the configured limits and pass it to card fetch + client transports. Expose properties. |
| **Pluggable HTTP client provider** (1.3.0; not adopted yet) | `A2AHttpClientProvider` SPI with priorities (CDI 200, Android 110, Vert.x 100, JDK 0). JSON-RPC/REST transport builders accept a custom `A2AHttpClient`. | Provide an `A2AHttpClient` bean (`@ConditionalOnMissingBean`) and pass it to every transport registrar. This is the Spring analogue of the CDI provider. |
| **`application/a2a+json`** | JSON-RPC and REST servers accept it alongside `application/json`. | Do not restrict the Spring controller with `consumes = application/json`; accept both. |
| **Push config ID semantics** | `TaskPushNotificationConfig.id` is now nullable. Omitting it is a **create-only** shorthand: default id = taskId; if it already exists, reject instead of replace. Updates must supply the id. | Update our JPA store: absent id → resolve taskId; if default exists → reject; do not blindly upsert. Update entity nullability handling. |
| **Blank ids treated as absent** | `taskId`/`contextId` `""` in `MessageSend` is normalized to absent. | Client already only sends non-blank ids; server behavior is now safe. Keep the client guard. |
| **Push notification ordering** | Per-task delivery chain preserves order. | Free upgrade; no design change. |
| **Current status message no longer in history** | Task history semantics fixed. | Our `AbstractA2AAgentExecutor` history seeding remains correct; adjust tests that assumed status messages in history. |
| **`A2ACardResolver` improvements** | Tenant path, custom path, fallback URL, well-known suffix stripping. | Our `SdkAgentCardSource` should mirror the resolver's URL rules (and keep its own HTTP GET for ETag/304). Do not re-invent URL logic. |
| **`AgentExecutor` is a functional interface** with default `cancel` (1.3.0). | Simple agents can be lambdas. | Keep `AbstractA2AAgentExecutor`, but document lambda usage; do not force the base class. |
| **Referred tasks** | `populateReferredTasks` resolves `referenceTaskIds` into `RequestContext.getRelatedTasks()`. | Expose property (default false) and `A2ARequest.relatedTasks()`. |
| **Fail-closed authorization default** (1.3.0). | No `TaskAuthorizationProvider` → task operations denied. | Revisit our `authorization.required=false` default: default `true` when Spring Security is present, otherwise `false` with a startup warning. |
| **Protocol compliance + security hardening** (1.3.0/1.4.0). | Version/extension validation on all operations, part-key validation, streaming capability error, SSRF-safe push URLs, redirects disabled, CR/LF header validation, error sanitization. | Free upgrade; keep our server controller faithful to the SDK handler. |
| **Task history and state-machine fixes** | Strict transitions, cancel serialization, terminal enqueue recovery, queue close races. | Free upgrade; remove any v1 code that re-implemented state transitions. |

### 2.2 Adopt as extension points

| Feature | Why | Recommendation |
|---|---|---|
| **`TaskStreamLifecycleHook`** | Observe subscribe/unsubscribe/events; close streams on timeout or subscriber limit. `InMemoryQueueManager` accepts it. | Expose an optional `TaskStreamLifecycleHook` bean; default no-op. |
| **`EventEnqueueHook` / replicated `QueueManager`** | Multi-instance replication (Kafka etc.). | Allow a consumer `QueueManager` bean to override; do not ship a broker dependency. Document `a2a-java-extras-queue-manager-replicated-core`. |
| **`TaskFinalizedEvent` (extras/common)** | CDI event after a final task is durably persisted. | Publish a Spring `ApplicationEvent` from our JPA store after save; let consumers react. |
| **OpenTelemetry extras** | Server `RequestHandler` decorator + client transport wrappers; GenAI span attributes. | Optional v2.1 module or conditional auto-config: wrap `RequestHandler` in `OpenTelemetryRequestHandlerDecorator` and pass `Tracer`/`OpenTelemetry` via `ClientBuildOptions.transportParameters`. Do not couple core to OTel. |
| **JPA extras** | SDK now denormalizes `context_id`, `state`, `status_timestamp`, `finalized_at` (same schema we planned). | Keep our Spring-native JPA module; align schema and semantics. Do not depend on the CDI `EntityManager` store. |
| **TCK / ITK** | SDK provides a TCK SUT and interoperability kit (ACTS behaviors). | Add a starter integration test that exercises our server with the SDK client; evaluate running the upstream TCK scenarios against the Spring server in CI. |
| **compat-0.3** | Serve v0.3 and v1.0 simultaneously; `A2A-Version` routing. | Out of scope for v2.0; document as optional when consumers need legacy agents. |
| **`@Tenant` / CDI multitenancy extras** | CDI qualifiers/routers. | Do not depend on CDI. Implement `AgentExecutorRouter`/`AgentCardRouter` as Spring beans. |

### 2.3 Do not adopt

- CDI qualifiers/decorators as our integration mechanism (Spring has its own).
- Quarkus reference server modules (JAX-RS) as our runtime.
- `a2a-java-extras-queue-manager-replicated` reactive-messaging strategy (broker-specific).
- Pinning `1.4.1.Final-SNAPSHOT`.

## 3. Required design changes

1. **Baseline** → `1.4.0.Final` (design, plan, build files).
2. **Manual wiring** → remove the manual `MainEventBusProcessor.start()` workaround;
   keep the config-provider injection.
3. **Multi-tenancy** → add tenant resolution + `AgentExecutorRouter` + `AgentCardRouter`
   (reflective injection into `JSONRPCHandler`) + tenant card path.
4. **HTTP/SSE** → add `A2AHttpClient` bean, `SSEParserConfig` properties, pass the client
   to card fetch and all transport registrars.
5. **Push configs** → create-only default id semantics; nullable id in entities.
6. **Extension hooks** → `TaskStreamLifecycleHook` bean, Spring `TaskFinalizedEvent`.
7. **Security default** → authorization on when Spring Security is present; explicit
   opt-out otherwise.
8. **Testing** → add SDK-client E2E, TCK/ITK evaluation, `application/a2a+json` case.
9. **Optional v2.1** → OpenTelemetry auto-config, OTel client parameters, compat-0.3,
   replicated queue manager, tenant scoping of the JPA store.

## 4. Open questions

- **`AgentCardRouter` injection**: the programmatic `JSONRPCHandler` constructor does not
  accept it (-CDI-only constructor does). Options: (a) reflectively set the
  `agentCardRouter` field (consistent with `SdkA2AConfigInjector`), (b) serve public cards
  ourselves and accept extended-card routing limited to the default card. Choose (a) for
  parity; add a pinned-SDK field test.
- **Tenant storage isolation**: SDK multi-tenancy shares `TaskStore` and
  `QueueManager`; tasks are UUID-keyed. Our `agent_scope` column can still enforce
  isolation via `TaskAuthorizationProvider`. Confirm whether the tenant (`RequestContext
  .getTenant()`) or our scope should be the persisted key; keep both (`tenant` for SDK
  routing, `agent_scope` for our authorization) and document the distinction.
- **Authorization default**: fail-closed is SDK behavior. A starter that defaults to
  `false` is convenient but insecure. Proposed: `true` if Spring Security is on the
  classpath, `false` + warning otherwise.

## 5. Suggested task deltas

| Task | Delta |
|---|---|
| `00` | Baseline 1.4.0.Final; add the verified-facts and gotchas above. |
| `02` | Document functional-interface usage; no tenant code (starter handles it). |
| `03`/`04` | `A2ACardSource` mirrors resolver URL rules; `ClientBuildOptions.transportParameters`; pass `A2AHttpClient`. |
| `05` | Push-config create-only semantics; nullable id; Spring `TaskFinalizedEvent`. |
| `06` | HTTP client + SSE properties; `TaskStreamLifecycleHook`; tenant routers; `a2a+json`; security default; manual-wiring note. |
| `07` | No API change. |
| `08` | TCK/ITK evaluation; docs; baseline 1.4.0. |
| `09` | Optional: OTel auto-config, TCK/ITK, compat-0.3, replicated queue, durable conversation store. |
