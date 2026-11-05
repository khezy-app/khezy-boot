# KHEZY A2A

A thin, Spring-native layer **on top of** the official
[A2A Java SDK](https://github.com/a2aproject/a2a-java) — it does not replace the SDK and
does not re-implement the protocol.

The SDK owns the wire model, task lifecycle, event bus, JSON-RPC routing and the
authorization contract. KHEZY adds:

- a small business-logic seam (`AbstractA2AAgentExecutor` + `A2ARequest`);
- Spring Boot assembly of the SDK server graph and HTTP transport;
- tenant routing and task isolation helpers;
- JPA persistence **with encryption at rest**;
- client ergonomics: card cache, registry, multi-turn connector, task operations;
- optional Spring AI tools that expose remote agents/skills to a model.

> **One `AgentCard` + one `AgentExecutor` is a working agent server.** Business logic is
> the only thing you write.

- Design: [`scrum/a2a/a2a-design-v2.md`](../../scrum/a2a/a2a-design-v2.md)
- Task plan: [`scrum/a2a/v2-actions/00-plan-overview.md`](../../scrum/a2a/v2-actions/00-plan-overview.md)
- Release notes: [`RELEASE-NOTES.md`](RELEASE-NOTES.md)
- Runnable samples: [`samples/`](samples/)

## Modules

All modules use group `io.github.khezyapp` and target the SDK baseline `1.4.0.Final`
(BOM), Java 17 bytecode, Spring Boot 4.1. All modules are version `1.0.0`.

| Module | Purpose |
|---|---|
| [`a2a-core`](a2a-core/) | Server seam (`A2ARequest`, `AbstractA2AAgentExecutor`, call context, store provider, tenant authorization) and client contracts (`RemoteAgent`, `A2AConnector`, `SendRequest`/`SendOutcome`). No Spring, no HTTP. |
| [`a2a-core-default`](a2a-core-default/) | SDK-backed implementations: card source/cache, client factory, registry, `SdkA2AInvoker`, in-memory conversation store, transport discovery. |
| [`a2a-store-jpa`](a2a-store-jpa/) | `TaskStore`, `TaskStateProvider`, `PushNotificationConfigStore`, `A2AConversationStore` and scope lookup on Spring Data JPA, with AES-GCM encryption at rest through `khezy-crypto`. |
| [`a2a-spring-ai`](a2a-spring-ai/) | Optional Spring AI tooling: skills as tools, agent-as-tool, routing prompt, compact result formatting. |
| [`a2a-spring-boot-starter`](a2a-spring-boot-starter/) | Auto-configuration for the SDK server graph, JSON-RPC/card endpoints, security call context, client graph, properties and optional Spring AI wiring. |
| [`a2a-observability-otel`](a2a-observability-otel/) | Optional OpenTelemetry tracing: SDK server decorator and client transport parameters for span propagation. |

## Server quick start

Add the starter and define a card plus one executor:

```groovy
implementation "io.github.khezyapp:a2a-spring-boot-starter:1.0.0"
```

```java
@Bean
AgentExecutor agentExecutor() {
    return new AbstractA2AAgentExecutor() {
        @Override
        protected void onMessage(final A2ARequest request) {
            request.artifact("echo", request.userInput());
            request.complete("done");
        }
    };
}
```

The card can come from a bean or from properties:

```properties
io.github.khezyapp.a2a.server.agent-card.name=My Agent
io.github.khezyapp.a2a.server.agent-card.description=Does useful things
io.github.khezyapp.a2a.server.agent-card.url=http://localhost:8080/a2a
```

That boots `POST /a2a` (JSON-RPC + SSE) and
`GET /a2a/.well-known/agent-card.json`. Raw SDK clients must send
`A2A-Version: 1.0`; the SDK client library adds it for you.

## Client quick start

With the starter's client auto-configuration (`io.github.khezyapp.a2a.client.enabled=true`
by default):

```java
// Registry is populated from io.github.khezyapp.a2a.client.urls[...]
final var agent = registry.find("My Agent").orElseThrow();

// One send with a normalized outcome (no SDK event branching in your code)
final var outcome = connector.invoke(agent, List.of(new TextPart("hello")),
        "echo", "conversation-1");
log.info("{} -> {}", outcome.state(), outcome.message());

// The same conversation id resumes an INPUT_REQUIRED task automatically.
final var resumed = connector.invoke(agent, List.of(new TextPart("more")), "echo",
        "conversation-1");
```

Add `a2a-spring-ai` (plus a model starter) to expose every discovered skill as a Spring AI
tool; pass the conversation id through `ToolContext` with
`A2AToolContextKeys.CONVERSATION_ID`.

## Encryption quick start

`a2a-store-jpa` is fail-closed: it only activates when a `StringEncryptor` bean exists.
Add `crypto-spring-boot-starter` (or your own `StringEncryptor`) and JPA:

```properties
io.github.khezyapp.crypto.active-key-id=dev
io.github.khezyapp.crypto.keys.dev=<base64url-32-byte-key>
```

Task and push-config payloads are stored as `kzy:v1` ciphertext with per-row AAD. Legacy
plaintext rows remain readable. Keys are owned by `khezy-crypto`; this module ships no
crypto code.

## What this does NOT do

- Re-implement A2A protocol behavior — no own `Message`, `Task`, `AgentCard` or error
  hierarchy; those are SDK types.
- gRPC or REST **server** transports — JSON-RPC over HTTP only.
- Arbitrary agent-name routing inside one process. **Per-tenant** routing is supported via
  the SDK `AgentExecutorRouter` / `AgentCardRouter`.
- Per-identity extended-card scoping (public and tenant scopes only).
- A reactive core API.
- OpenTelemetry by default. It is an optional module (`a2a-observability-otel`) and the
  starter never depends on OpenTelemetry.
- v0.3 protocol compatibility modules. The SDK ships `compat-0.3` modules, but they are
  JAX-RS/Quarkus oriented and are not wired into this servlet transport; serve legacy
  agents side by side instead.
- A replicated queue manager or broker integration. A consumer `QueueManager` bean
  overrides the in-memory one; use the SDK replicated queue core as the building block.
- UI.
- Token issuance/refresh, OAuth flows, model clients or durable client conversation
  storage.

## Who it is for

- **Beginners** who want a working A2A agent without reading the entire SDK reference:
  start with one card and one executor, then replace pieces as you learn them.
- **Experienced developers** who know Spring and the protocol and want the boilerplate
  (transport, card serving, config, client cache) already wired, with every bean
  replaceable.
- **Bootstrap/MVP projects** that need a real agent endpoint in a sprint without
  committing to a custom protocol layer.

You still need to understand the A2A concepts you use — tasks, messages, artifacts and
task states are SDK types, and the [A2A specification](https://a2a-protocol.org) is the
reference.
