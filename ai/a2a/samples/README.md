# KHEZY A2A Samples

Two runnable apps that prove the A2A layer end to end — a **math-agent server** built on
the starter, and a **host-agent client** built on `a2a-core` + `a2a-core-default`. The
client discovers the server's agent card and asks it to compute `2 + 3`.

## Run them

```bash
# Terminal 1 — start the math agent
./gradlew -p ai/a2a/samples/a2a-server-sample bootRun

# Terminal 2 — agent card discovery
curl -s http://localhost:8080/.well-known/agent-card.json

# Terminal 2 — raw JSON-RPC message/send (note: role is the enum name, parts carry "kind")
curl -s -X POST http://localhost:8080/message/send -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"ROLE_USER","parts":[{"kind":"text","text":"2 + 3"}]}}}'

# Terminal 2 — run the host agent client against the server
./gradlew -p ai/a2a/samples/a2a-client-sample bootRun
```

Expected: discovery returns a card named **Math Agent**, the curl result carries `"5"`
at `result.status.message.parts[0].text`, and the client logs

```
Discovered remote agent 'Math Agent' v0.0.1-SNAPSHOT
Remote math agent replied: TASK_STATE_COMPLETED "5"
```

## What each app shows

- **`a2a-server-sample`** (`sampleserver/`) — the minimum agent server: one
  `MathAgentExecutor extends AbstractA2AAgentExecutor` parsing `<int> <op> <int>`,
  an `AnonymousIdentityConfiguration` (the starter's security-gated identity resolver
  needs Spring Security — samples don't use it), and card properties in
  `application.properties`.
- **`a2a-client-sample`** (`sampleclient/`) — the minimum host:
  `SdkA2ARemoteAgentClient.forJsonRpc(...)` → `discover()` → `sendMessage()` → log →
  `close()`, driven by a `CommandLineRunner` (`HostAgentRunner`). Target server is
  configurable via `a2a.server.url`.

## What these do NOT show

No streaming UI, no durable task store, no authentication — the point is the smallest
honest wiring of the library. For production-shaped concerns see the module READMEs.
