# A2A2-09 — Optional SDK features (post-v2.0)

**Prerequisites:** `00`, `06`, `08`.
**Design:** §6.4, §8.1, §12.
**Review:** `../sdk-1.4-adoption.md` §2.2.

This task is **optional / v2.1** and must not block the v2.0 release. Implement only
after v2.0 is green and demanded by a real use case.

## 1. OpenTelemetry auto-config (recommended first)

Create an optional module `ai/a2a/a2a-observability-otel` (or conditional auto-config in
the starter) with:

- dependencies: `a2a-extras-opentelemetry-server`, `-client`, `-client-propagation`
  (all optional/provided).
- `@ConditionalOnClass(name = "io.opentelemetry.api.trace.Tracer")`.
- Server: wrap the `RequestHandler` bean in a small Spring adapter around
  `OpenTelemetryRequestHandlerDecorator` (its `(RequestHandler, Tracer)` constructor is
  public; the class is abstract, so use a tiny no-op subclass).
- Client: put `Tracer` and `OpenTelemetry` into
  `ClientBuildOptions.transportParameters` under the SDK wrapper keys
  (`OTEL_TRACER_KEY`, `OTEL_OPEN_TELEMETRY_KEY`). Our `A2AClientTransports` already
  applies `ClientTransportConfig.setParameters(...)`.
- Do **not** enable request/response extraction by default (PII risk).
- Property: `io.github.khezyapp.a2a.observability.otel.enabled` (default true when
  `Tracer` bean exists).

Tests: context loads without OTel; with a test `Tracer`, one span per request and client
propagation headers present.

## 2. TCK / interoperability test

- Add an integration test profile that runs the SDK client against the Spring server for
  every JSON-RPC method (send, stream, get, list, cancel, subscribe, push-config CRUD,
  extended card).
- Evaluate running the upstream `tck` scenarios / ITK ACTS behaviors against the Spring
  server in CI. Document any scenario that needs a transport we do not expose.
- Do not vendor the SDK TCK; depend on the published test artifacts if available.

## 3. compat-0.3 evaluation

- Determine whether our JSON-RPC controller can route `A2A-Version: 0.3` to the SDK
  compat handlers without re-implementing the protocol.
- If it requires the Quarkus/JAX-RS reference modules, document it as unsupported and
  point consumers at a side-by-side deployment.
- Only add if a real consumer needs v0.3 agents.

## 4. Replicated queue manager hook

- Verify a consumer-provided `QueueManager` bean overrides our `InMemoryQueueManager`.
- Document `a2a-java-extras-queue-manager-replicated-core` as the building block; do not
  add a broker dependency or a Spring-specific replication strategy.
- Ensure `TaskStore` and `QueueManager` share the same `TaskStateProvider` when replaced.

## 5. Durable conversation store

- Add a JPA `A2AConversationStore` implementation (conversation id, agent, skill,
  contextId, taskId, updatedAt) to `a2a-store-jpa`.
- This completes multi-node multi-turn resumption; in-memory remains the default.

## 6. Verification

```bash
./gradlew :a2a-observability-otel:check   # if created
./gradlew :a2a-store-jpa:check
./gradlew :a2a-spring-boot-starter:check
./gradlew unittest
```

## 7. Handoff

Record which optional items were implemented, the OTel wrapper keys, TCK results, and
any compat-0.3 limitation. `graphify update .`.
