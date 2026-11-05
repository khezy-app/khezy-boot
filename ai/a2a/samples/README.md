# KHEZY A2A Samples

Two runnable apps that prove the v2 stack end to end:

- **`a2a-server-sample`** — a math agent built on `a2a-spring-boot-starter`
  (one card + one executor);
- **`a2a-client-sample`** — a host built on the starter's client auto-configuration
  (registry + multi-turn connector), using a deliberately small SSE line limit.

## Run them

```bash
# Terminal 1 — start the math agent
./gradlew -p ai/a2a/samples/a2a-server-sample bootRun

# Terminal 2 — agent card discovery
curl -s http://localhost:8080/a2a/.well-known/agent-card.json

# Terminal 2 — raw JSON-RPC SendMessage
curl -s -X POST http://localhost:8080/a2a \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"m1","parts":[{"text":"2 + 3"}]}}}'

# Terminal 2 — run the host agent client against the server
./gradlew -p ai/a2a/samples/a2a-client-sample bootRun
```

Expected: discovery returns a card named **Math Agent**, the curl result carries `"5"` in
`result.task.status.message.parts[0].text` plus a `result` artifact, and the client logs

```
Discovered remote agent 'Math Agent' v1.0.0 at http://localhost:8080/a2a
2 + 3 -> TASK_STATE_COMPLETED "5" artifacts=[...]
Agent asks: What is the missing operand?
2 + 3 (resumed) -> TASK_STATE_COMPLETED "5" artifacts=[...]
```

### Working wire form

The SDK 1.4 JSON-RPC surface is strict:

- method names are PascalCase (`SendMessage`, `GetTask`, ...);
- every raw request needs `A2A-Version: 1.0` (without it the SDK assumes 0.3 and returns
  `VERSION_NOT_SUPPORTED`);
- a text part is `{"text":"..."}` (proto-JSON oneof), **not**
  `{"kind":"text","text":"..."}`;
- `message.messageId` is required;
- the endpoint is the base path root (`POST /a2a`), not `/message/send`;
- the card lives at `<path>/.well-known/agent-card.json`.

`Content-Type: application/a2a+json` works too, with the same payload and header.

## Tenant routing demo

The server ships an opt-in SDK 1.4 multi-tenancy profile:

```bash
./gradlew -p ai/a2a/samples/a2a-server-sample bootRun \
  --args='--spring.profiles.active=tenant'

curl -s http://localhost:8080/a2a/.well-known/acme/agent-card.json   # tenant card
curl -s -X POST http://localhost:8080/a2a \
  -H 'Content-Type: application/json' -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":2,"method":"SendMessage","params":{"tenant":"acme","message":{"role":"ROLE_USER","messageId":"t1","parts":[{"text":"2 + 3"}]}}}'
```

Unknown tenants return `404`. The tenant id is routing metadata; persistence isolation
uses `agent_scope`, which a production executor should seed in task metadata.

## Encryption at rest (H2 profile)

```bash
./gradlew -p ai/a2a/samples/a2a-server-sample bootRun \
  --args='--spring.profiles.active=h2'
```

That profile enables the JPA stores from `a2a-store-jpa` on a file-backed H2 database with
a development AES key. Send a message carrying a secret in metadata, then inspect the
table (H2 `AUTO_SERVER=TRUE`, so the shell can connect while the app runs):

```bash
H2JAR=$(find ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2 -name 'h2-*.jar' | head -1)
java -cp "$H2JAR" org.h2.tools.Shell \
  -url 'jdbc:h2:file:/tmp/khezy-a2a-sample;MODE=PostgreSQL;AUTO_SERVER=TRUE' \
  -user sa -password '' \
  -sql "select task_data from a2a_tasks"
```

`task_data` starts with `kzy:v1:` and does not contain the secret. The H2 file lives in
`/tmp`; the profile uses `create-drop`, so data disappears on shutdown.

## What each app shows

- **`a2a-server-sample`** (`sampleserver/`)
  - `MathAgentExecutor extends AbstractA2AAgentExecutor`: parses `<int> <op> <int>`,
    asks for a missing operand via `requireInput`, rebuilds the expression from task
    history on the next turn, emits a `result` artifact and completes.
  - `MathAgentCardConfiguration`: explicit `AgentCard` bean with the `add`, `subtract`,
    `multiply` and `divide` skills.
  - `StreamLifecycleLoggingConfiguration`: optional SDK 1.4 `TaskStreamLifecycleHook`
    demo that logs subscriber counts.
  - `TenantSampleConfiguration` (`tenant` profile): `MapAgentExecutorRouter` +
    `MapAgentCardRouter` with an `acme` tenant.
- **`a2a-client-sample`** (`sampleclient/`) — `RemoteAgentRegistry` discovery,
  `A2AConnector` send and `INPUT_REQUIRED` resume, driven by a `CommandLineRunner`.
  Target server via `io.github.khezyapp.a2a.client.urls[0]`.

## What these do NOT show

No authentication, no streaming UI, no extended cards, no push notifications. The point is
the smallest honest wiring of the library; see the module READMEs for the rest.
