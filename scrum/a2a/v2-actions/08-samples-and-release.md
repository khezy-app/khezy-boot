# A2A2-08 — Samples, E2E, docs, release

**Prerequisites:** all previous tasks.
**Design:** §11, §12.

## 1. Goal

Prove the v2 stack end to end with two runnable samples, update all docs to the SDK-based
architecture, and run the full verification gate.

## 2. Samples

Rewrite `ai/a2a/samples/a2a-server-sample` and `a2a-client-sample`.

### 2.1 Server sample (`a2a-server-sample`)

- Dependencies: `a2a-spring-boot-starter` (and `a2a-store-jpa` + H2 optionally to prove
  encryption; use in-memory by default, H2 in a second profile).
- `MathAgentExecutor extends AbstractA2AAgentExecutor`:
  - `onMessage(A2ARequest request)`: parse `<int> <op> <int>`, `reply(...)` or
    `artifact("result", "...")` + `complete()`.
  - demonstrate `requireInput` for incomplete input.
  - keep it free of A2A ids and emitter ceremony.
- `AgentCard` bean with name, description, skills (`add`, `subtract`, `multiply`,
  `divide`), `supportedInterfaces` JSON-RPC + card URL.
- `application.properties`:
  ```properties
  server.port=8080
  io.github.khezyapp.a2a.server.path=/a2a
  io.github.khezyapp.a2a.server.agent-card.url=http://localhost:8080/a2a
  ```
- Exactly **one** executor bean; no dispatcher/store/call-context boilerplate.

### 2.2 Client sample (`a2a-client-sample`)

- Dependencies: starter (client auto-config).
- `application.properties`:
  ```properties
  io.github.khezyapp.a2a.client.urls[0]=http://localhost:8080/a2a
  io.github.khezyapp.a2a.client.call-timeout=30s
  ```
- `CommandLineRunner`: discover → send `"2 + 3"` → log outcome; fail-soft.
- Optional: a second run showing multi-turn `INPUT_REQUIRED` resume through
  `A2AConnector` (server `MathAgentExecutor` asks for missing operand).

## 2.3 SDK 1.4 additions to demonstrate

- Tenant routing sample: a `Map<String, AgentExecutor>` + `AgentExecutorRouter` bean and
  a tenant-specific card; call it with a `tenant` field and the tenant card path.
- SSE limits: set a small `sse.max-line-length` in the client sample and verify a normal
  reply still works.
- `TaskStreamLifecycleHook`: optional demo bean that logs subscriber counts.

## 3. End-to-end verification (live)

```bash
# terminal 1
./gradlew -p ai/a2a/samples/a2a-server-sample bootRun

# terminal 2
curl -s http://localhost:8080/a2a/.well-known/agent-card.json
curl -s -X POST http://localhost:8080/a2a \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"m1","parts":[{"kind":"text","text":"2 + 3"}]}}}'

# terminal 3
./gradlew -p ai/a2a/samples/a2a-client-sample bootRun
```

Also verify with the official SDK client semantics: POST to the base path root, camelCase
method names, proto-JSON params. If the SDK's exact wire form differs, record the working
payload in the sample README.

Encryption check (second server profile with H2 + key):
- send a message containing a secret;
- query `a2a_tasks.task_data` with H2 console/SQL and assert the secret is absent.

## 4. Documentation

- `ai/a2a/README.md` — rewrite:
  - "sits on top of the SDK, not instead of it";
  - modules table (including `a2a-store-jpa`);
  - quick start: one card + one executor;
  - client quick start: registry/connector/tools;
  - encryption quick start;
  - what the library does **not** do (from design §12);
  - who it is for (KHEZY tone: beginners, experienced devs, bootstrap projects).
- Module READMEs: `a2a-core`, `a2a-core-default`, `a2a-store-jpa`, `a2a-spring-ai`,
  `a2a-spring-boot-starter` — API surface, property tables, gotchas.
- `scrum/a2a/README.md` — index (design v2, v2 actions, archive).
- `scrum/a2a/_archive-v1/README.md` — one paragraph: "superseded by v2; kept for
  historical context. Do not implement."
- `RELEASE-NOTES-v2.md`:
  - breaking changes vs v1 (deleted ports/dispatcher/controllers; no `A2AAgentExecutor`,
    `CallerIdentity`, `ChatClientExecutors`);
  - new `a2a-store-jpa` + encryption;
  - SDK baseline **1.4.0.Final**;
  - adopted 1.4 features: multi-tenancy routers, SSE config, `application/a2a+json`,
    `TaskStreamLifecycleHook`, push config create-only semantics, manual-wiring fix;
  - migration table from design §10.

## 5. Full verification

```bash
./gradlew :a2a-core:check :a2a-core-default:check :a2a-store-jpa:check \
          :a2a-spring-ai:check :a2a-spring-boot-starter:check
./gradlew unittest
./gradlew checkstyleMains checkstyleTests
```

Then `graphify update .`.

## 6. Handoff

Record final module coordinates/versions, SDK version, any live-wire payload corrections,
and known gaps (gRPC server, extended-card scoping, durable conversation store).
