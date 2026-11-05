# A2A2-04 — `a2a-core-default`: SDK client defaults

**Prerequisites:** `00`, `03`.
**Design:** §6.2, §6.3.

## 1. Goal

Provide working defaults for the client contracts, ported from the reference
implementation and simplified. Package root `io.github.khezyapp.a2a.coredef.client.*`.

## 2. Files

### 2.1 Card resolution

- `card/SdkAgentCardSource`:
  - accepts an `A2AHttpClient` (created by the starter with `SSEParserConfig`), an
    optional card path, auth headers, and an optional tenant.
  - mirrors SDK 1.4 `A2ACardResolver` URL rules: raw `.json` URL, custom path,
    tenant path `/.well-known/{tenant}/agent-card.json`, well-known suffix stripping,
    fallback URL. Do not hand-roll URL parsing.
  - conditional GET with `If-None-Match`/`If-Modified-Since`; read `ETag`,
    `Last-Modified`, `Cache-Control: max-age` from the response.
  - parse with the SDK's proto-JSON agent-card reader.
  - return a fetch result `(status OK/NOT_MODIFIED/FAILED, card, etag, lastModified, maxAge)`.
  - never throw; preserve interrupt flag.
- `card/CachingAgentCardResolver implements AgentCardResolver`:
  - fresh hit → no I/O;
  - stale → conditional request (`If-None-Match`/`If-Modified-Since`), reuse on 304;
  - failure → serve stale card, else empty.
  - default TTL 5 min; `invalidate`, `clear`.
- `card/CardCacheHeader`/`CardFetch`/`CardFetchStatus`/`AgentCardCacheEntry` supporting records.

### 2.2 SDK client factory and registry

- `A2AClientFactory`:
  - `create(AgentCard, ClientBuildOptions)`.
  - accepted output modes: request → card defaults → `["text"]`.
  - streaming requested only if card capability allows.
  - register every transport from `A2AClientTransports.discover()`.
  - pass the shared `A2AHttpClient` into JSON-RPC/REST transport config builders.
  - apply `transportParameters` via `ClientTransportConfig.setParameters(...)` after
    build (this is how SDK ServiceLoader wrappers such as OpenTelemetry activate).
  - optional `CredentialService` → add SDK `AuthInterceptor`.
- `ClientBuildOptions` record `(acceptedOutputModes, streaming, polling,
  useClientPreference, interceptors, credentialService, A2AHttpClient httpClient,
  Map<String,Object> transportParameters)` + defaults.
- `A2AClientTransport` SPI + `A2AClientTransports.discover()` (JSON-RPC + REST built in,
  `ServiceLoader` for gRPC).
- `transport/JsonRpcClientTransport` and `transport/RestClientTransport` registering the
  SDK transport builders.
- `RemoteAgentRegistry`:
  - fail-soft registration from configured URLs;
  - `find(name)` case-insensitive fallback, `findBySkillId`, `skills`, `agents`,
    `refresh`, `unregister`, `close` (idempotent);
  - extended-card support is **out of scope** in v2.0 (reference code is the template
    when it returns; keep the constructor extensible).
  - `AutoCloseable`, closes SDK clients.

### 2.3 Invoker and conversation store

- `invoke/SdkA2AInvoker implements A2AInvoker`:
  - build SDK `Message` (role USER, uuid messageId, parts, skill id in metadata,
    context/task ids when present, reference tasks, extensions);
  - build `MessageSendParams` (configuration, params metadata, tenant fallback to the
    agent's selected interface tenant);
  - build `ClientCallContext` (headers + state);
  - call `agent.client().sendMessage(...)`; complete on first settled event
    (final **or** interrupted if `stopOnInterrupt`, or immediately when
    `configuration.returnImmediately`).
  - timeout → `SendOutcome.error`; interrupt restores the flag; other exceptions →
    `SendOutcome.error`.
  - never close the shared client.
- `store/InMemoryA2AConversationStore`:
  - TTL (default 1h) + LRU bound (default 1000);
  - same key semantics as the reference (`agent|skill` within conversation);
  - `recordSettled` keeps context, drops task.
- `A2AConversationStore` bounded wrapper? Not needed in v2.0.

### 2.4 Keep out

- No custom HTTP stack: use the SDK client.
- No `BearerTokenInterceptor`; consumers pass `ClientCallInterceptor` beans.

## 3. Tests

- `CachingAgentCardResolverTest` — fresh/stale/304/failure cases with a fake source.
- `A2AClientTransportsTest` — JSON-RPC + REST discovered; ServiceLoader additions.
- `SdkA2AInvokerTest` — with a Mockito `Client`:
  - message/params/call-context construction;
  - terminal event completes;
  - interrupted event completes when `stopOnInterrupt`;
  - timeout/error produce `SendOutcome.error`;
  - shared client not closed.
- `InMemoryA2AConversationStoreTest` — per agent/skill keying, settled drops task,
  TTL/LRU eviction, clear.
- `RemoteAgentRegistryTest` — fail-soft register, name lookup, skill lookup, refresh,
  close closes clients.

Use `khezy.java-use-mockito`.

## 4. Verification

```bash
./gradlew :a2a-core-default:check
```

## 5. Handoff

Record the real SDK client event types used (`ClientEvent`/`TaskEvent`/`TaskUpdateEvent`/
`MessageEvent`), the discovery call, and any proto-JSON parsing friction. Task 06 wires
the registry/connector beans; task 07 consumes them. `graphify update .`.
