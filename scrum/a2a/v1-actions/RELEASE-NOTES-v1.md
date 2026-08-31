# Release Notes — KHEZY A2A v1.0.0

First release of the KHEZY A2A layer: behavior contracts + defaults + Spring Boot
auto-configuration on top of the official A2A Java SDK (`org.a2aproject.sdk`, wire
model owned by the SDK — we abstract behavior only).

## Modules

| Coordinates | Summary |
|---|---|
| `io.github.khezyapp:a2a-core:1.0.0` | Port interfaces (executor, store, dispatch, client, identity) + error taxonomy, typed on the SDK spec model |
| `io.github.khezyapp:a2a-core-default:1.0.0` | In-memory task store/event queue, request dispatcher, SDK JSON-RPC remote client, bearer-token interceptor |
| `io.github.khezyapp:a2a-spring-ai:1.0.0` | Optional adapter: Spring AI `ChatClient` → agent executor (`ChatClientExecutors`) |
| `io.github.khezyapp:a2a-spring-boot-starter:1.0.0` | Auto-configuration, JSON-RPC HTTP endpoints (incl. SDK proto-JSON interop at `POST /`), `io.github.khezyapp.a2a.*` properties |

Sample apps (not published): `a2a-server-sample`, `a2a-client-sample` under
`ai/a2a/samples/` — runnable end-to-end math-agent demo.

## Baselines

- A2A Java SDK: `org.a2aproject.sdk:*:1.2.0.Final`
- Spring AI: `2.0.1` (`a2a-spring-ai` only)
- Spring Boot: `4.1.0` (starter; Jackson 3 / `tools.jackson.*`)
- Java 17 target, Checkstyle 13.1.0

## Known gaps

From design §9 — accepted trade-offs for v1:

- **Spec velocity** — A2A is moving toward Linux Foundation governance; wire changes
  arrive through SDK upgrades and may ripple into port signatures. Ports are kept thin.
- **SDK lock-in** — bounded by design: we depend on the SDK's *types*, not its runtime;
  other stacks speaking the same spec stay reachable via our client ports.
- **Reactor wrappers deferred** — core is callback-based (`AgentEventSink`); Flux-style
  APIs would be an add-on module.
- **In-memory state only** — no durable/multi-node task store yet (Redis/JDBC are the
  natural v2 candidates).
- **JSON-RPC over HTTP transport only** — no gRPC wiring in v1.
