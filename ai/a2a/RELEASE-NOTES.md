# KHEZY A2A 1.0 — Release Notes

**Baseline:** official A2A Java SDK `1.4.0.Final` (BOM), Spring Boot 4.1, Java 17
bytecode. All modules are `1.0.0`.

This is the first KHEZY A2A release: a thin Spring-native layer **on top of the SDK** —
the SDK owns the wire model, task lifecycle, event bus, JSON-RPC routing and
authorization contract.

## New: `a2a-store-jpa`

- JPA `TaskStore` / `TaskStateProvider` / `PushNotificationConfigStore` plus a
  denormalized `TaskScopeLocator`.
- Payload encryption at rest through `khezy-crypto` (AES-256-GCM, `kzy:v1` prefix,
  per-row AAD). Legacy plaintext rows still read.
- Fail-closed activation: no `StringEncryptor`, no stores.
- SDK 1.4 push-config semantics: nullable id means create-only, per-task limit,
  pagination and protocol versions.

## SDK 1.4 features adopted

- **Multi-tenancy:** `AgentExecutorRouter` / `AgentCardRouter` beans, tenant card path
  (`/.well-known/{tenant}/agent-card.json`), `RequestContext.getTenant()`.
- **HTTP client + `SSEParserConfig`:** one shared client with configurable SSE limits.
- **Manual event-bus start fix:** `MainEventBusProcessor` is a Spring bean and starts via
  `@PostConstruct`; no manual start.
- **`TaskStreamLifecycleHook`:** replaceable bean (default is the SDK hook).
- **Push config create-only semantics:** omitted id never upserts a default config.
- **`application/a2a+json`:** accepted alongside `application/json`.
- **Fail-closed authorization default:** `auto` enables it when Spring Security is on the
  classpath, otherwise warns and disables.
- **Functional `AgentExecutor`:** raw SDK lambdas still work; the KHEZY seam is optional.
- **`TaskFinalizedEvent`** published by the JPA store as a plain record.

## Optional features

- **`a2a-store-jpa:1.0.0` conversation store:** `springA2AConversationStore` replaces the
  in-memory `A2AConversationStore`, enabling multi-node multi-turn resumption. Entries
  hold routing identifiers only (no message payload) and expire via `store.conversation.ttl`.
- **`a2a-observability-otel:1.0.0`:** optional OpenTelemetry tracing. Active only with a
  `Tracer` bean and the SDK `a2a-java-sdk-opentelemetry-*` artifacts on the classpath.
  Server tracing wraps the SDK `RequestHandler`; client tracing adds the SDK wrapper
  parameters (`OTEL_TRACER_KEY`, `OTEL_OPEN_TELEMETRY_KEY`). Payload capture is off by
  default.
- **Replicated queues:** a consumer `QueueManager` bean overrides `InMemoryQueueManager`;
  the starter ships no broker dependency. Give it the same `TaskStateProvider` as the
  task store (`A2AStoreProvider.taskStateProvider()`).
- **Interoperability test:** `A2AJsonRpcConformanceTest` drives the SDK client against the
  Spring server for every JSON-RPC method.
- **compat-0.3:** evaluated, not wired. The SDK compat modules are usable programmatically
  (`JSONRPCHandler_v0_3` + `Convert_v0_3_To10RequestHandler` + `JsonUtil_v0_3`), but the
  reference module is Quarkus/Vert.x; this servlet transport does not route
  `A2A-Version: 0.3`. Deploy legacy agents side by side.

## Known gaps

- gRPC and REST **server** transports are not wired (JSON-RPC over HTTP only).
- Extended cards support public/tenant scope, not per-identity scoping.
- OpenTelemetry is opt-in via a separate module; the starter has no tracing by default.
- v0.3 compatibility and upstream TCK scenarios are not wired into CI.
- Replicated queue managers are consumer-provided (no broker dependency).
