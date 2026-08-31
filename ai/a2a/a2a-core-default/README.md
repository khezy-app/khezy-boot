# Khezy A2A Core Default

Ready-to-use implementations of the [`a2a-core`](../a2a-core/) ports, built on the
official A2A Java SDK. They make an agent server work out of the box — and each one is
an override point when the default stops fitting.

`io.github.khezyapp:a2a-core-default:1.0.0` — package root
`io.github.khezyapp.a2a.coredef`.

## What's inside

| Class | Port | Behavior |
|---|---|---|
| `dispatch.DefaultA2ARequestDispatcher` | `A2ARequestDispatcher` | Routes send → `execute`, stream → `stream`; persists tasks, generates ids for anonymous messages, publishes a failed status on executor errors |
| `store.InMemoryTaskStore` | `A2ATaskStore` | Thread-safe, in-memory task persistence |
| `store.InMemoryEventQueue` | `A2AEventQueue` | In-memory event fan-out with `Subscription`s |
| `store.NoopPushNotificationSender` | `PushNotificationSender` | Does nothing — replace it to actually push |
| `executor.DefaultAgentExecutionContext` / `SimpleCancellationToken` | — | Plain context + cooperative cancellation |
| `client.SdkA2ARemoteAgentClient` | `A2ARemoteAgentClient`, `A2AStreamingRemoteAgentClient` | SDK JSON-RPC client: `forJsonRpc(URI, Duration[, interceptors])`, `discover(URI)`, `sendMessage`, `streamMessage` |
| `client.BearerTokenInterceptor` | SDK `ClientCallInterceptor` | Adds `Authorization: Bearer` per call from a token supplier; a per-call header wins |

## When to replace them

- **Restart-safe or multi-instance tasks** → implement `A2ATaskStore` (JDBC/Redis) and
  register your bean; the starter's defaults are all `@ConditionalOnMissingBean`.
- **Real push notifications** → implement `PushNotificationSender`.
- **Custom auth on outbound calls** → add your own `ClientCallInterceptor` bean or set
  `io.github.khezyapp.a2a.client.auth.*` (see the starter).

## What this does NOT do

- No durable storage — everything in memory; state dies with the JVM and instances do
  not share it.
- No token issuance/refresh — `BearerTokenInterceptor` only attaches tokens you supply.
- No transport of its own server-side — HTTP endpoints live in the
  [starter](../a2a-spring-boot-starter/).
