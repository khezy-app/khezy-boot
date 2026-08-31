# Khezy A2A Spring Boot Starter

Auto-configuration for the KHEZY A2A layer: drop in the starter, add one executor bean,
and your app serves the A2A JSON-RPC protocol over HTTP. It sits on top of Spring Boot's
web stack and the official A2A Java SDK — it configures both rather than replacing them.

`io.github.khezyapp:a2a-spring-boot-starter:1.0.0`

## Auto-configured beans

Every port is `@ConditionalOnMissingBean` — define your own to take over.

| Bean | From | Note |
|---|---|---|
| `taskStore`, `eventQueue`, `pushNotificationSender` | `a2a-core-default` | In-memory defaults |
| `agentCardSupplier` | properties below | Provide your own `Supplier<AgentCard>` bean to bypass |
| `callerIdentityResolver` | Spring Security context | Only materializes **with Spring Security** on the classpath |
| `a2aRequestDispatcher` | `a2a-core-default` | Requires an `A2AAgentExecutor` bean — **you must supply it**; without one the context fails fast |
| `a2aRemoteAgentClient` | outbound client | Created only when `client.base-url` is set |
| `bearerTokenInterceptor` | outbound auth | Only when `client.auth.type=bearer` |

## HTTP surface

- `GET /.well-known/agent-card.json` — agent card discovery
- `POST /message/send`, `POST /message/stream` (SSE) — v0.3-style routes with our
  Jackson envelopes (`role: "ROLE_USER"`, parts carry a wire `"kind"` discriminator)
- `POST /tasks/get`, `POST /tasks/cancel`
- `POST /` — **SDK interop endpoint**: proto-JSON JSON-RPC (`SendMessage`,
  `SendStreamingMessage`, `GetTask`, `CancelTask`) that the official SDK clients speak

## Properties (`io.github.khezyapp.a2a.*`)

| Property | Default | Purpose |
|---|---|---|
| `agent-card-name` | `Khezy A2A Agent` | Card display name |
| `agent-card-description` | *(empty)* | Card description |
| `agent-card-url` | *(empty)* | Base URL published in the card — set it or SDK clients can't connect after discovery |
| `agent-card-version` | `1.0.0` | Card version |
| `streaming-enabled` | `true` | Advertises SSE capability |
| `client.base-url` | *(empty)* | Remote agent to call; setting this creates the client bean |
| `client.timeout` | `30s` | Reply timeout |
| `client.auth.type` | `NONE` | `BEARER` adds a static token interceptor |
| `client.auth.token` | *(empty)* | Token for `type=bearer` |

## Try it

```bash
curl -s -X POST http://localhost:8080/message/send -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"ROLE_USER","parts":[{"kind":"text","text":"2 + 3"}]}}}'
```

The result is a completed `TaskStatusUpdateEvent`; read the reply at
`result.status.message.parts[0].text`. Runnable server + client live in
[`samples`](../samples/).

## What this does NOT do

- No security enforcement of its own — endpoints are plain MVC; protect them with
  Spring Security as you would any endpoint.
- No default identity resolver for security-less apps — without Spring Security,
  register a `CallerIdentityResolver` bean (e.g. `CallerIdentity::anonymous`).
- No durable store, no gRPC transport — see the [umbrella README](../README.md).
