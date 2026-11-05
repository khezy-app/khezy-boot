# Khezy A2A Core

Behavior contracts and the business seam for A2A, typed entirely on the official A2A Java
SDK spec model (`org.a2aproject.sdk.spec.*`). This module sits on top of the SDK: it owns
no wire types and adds no protocol behavior. Pure Java — no Spring, no HTTP.

`io.github.khezyapp:a2a-core:1.0.0` · SDK `1.4.0.Final`

## Server seam (`io.github.khezyapp.a2a.core.server`)

| Type | Purpose |
|---|---|
| `AbstractA2AAgentExecutor` | Seeds a new task, calls `startWork()`, then hands your code an `A2ARequest`. Still a plain SDK `AgentExecutor`, so lambdas keep working. |
| `A2ARequest` | `userInput()`, `incoming()`, `currentTask()`, `caller()`, `tenant()`, `attributes()` plus `reply`, `artifact`, `complete`, `requireInput`, `requireAuth`, `fail`, `cancel`. Escape hatches: `context()`, `emitter()`. |
| `A2ACallContextFactory<T>` | Maps a transport request to the SDK `ServerCallContext` (user, tenant, agent scope, headers). |
| `TaskScopeLocator` | Resolves the persistence scope of a stored task. `fromTaskStore(...)` is the generic fallback; JPA provides a denormalized lookup. |
| `TenantTaskAuthorizationProvider` | SDK `TaskAuthorizationProvider` that requires both call-state and task-metadata scopes to match. Missing/blank/different scopes deny. |
| `A2AStoreProvider` | Bundles `TaskStore` + `TaskStateProvider` + `PushNotificationConfigStore`; `inMemory()` shares one store for all three views. |
| `A2AServerCustomizer` | Hook called after the SDK `DefaultRequestHandler` is built. |
| `A2AContextKeys` | App-owned call-context keys; `AGENT_SCOPE` is the persistence scope. The SDK tenant is routing only. |

Business failures: use `request.fail(...)` for a visible failed task or throw an SDK
`A2AError` for protocol errors. Unexpected runtime exceptions are mapped by the SDK
request handler; the base class does not swallow them.

## Client contracts (`io.github.khezyapp.a2a.core.client`)

| Type | Purpose |
|---|---|
| `RemoteAgent` / `RemoteSkill` / `RemoteArtifact` | Views over a resolved SDK `AgentCard` and its skills/artifacts. |
| `AgentCardResolver` | `Optional<AgentCard> resolve(String baseUrl)`. |
| `A2AInvoker` | Sends one `SendRequest` and returns a `SendOutcome`. |
| `A2AConnector` | Store-aware multi-turn entry point: injects remembered ids, serializes calls per conversation, records interrupted/pending/settled state. |
| `A2AConversationStore` | Conversation memory (`ref`, `recordInterrupted`, `recordPending`, `recordSettled`). |
| `SendRequest` / `SendOptions` | Immutable request options (parts, skill, ids, tenant, metadata, extensions) and strategy/timeout options (`BLOCKING`, `POLL`, `STREAM`). |
| `SendOutcome` | One normalized result: state, message, artifacts, ids, or an error string. No KHEZY exception hierarchy. |
| `A2AClientOperations` | Facade for `getTask`, `listTasks`, `cancelTask`, push-config CRUD and the extended card. |

`SendOutcome.isFinal()` is also true for a standalone message response (no task state).
`A2AConnector.invoke(...)` requires a non-blank conversation id; `""` is the store key for
"no skill".

## What this does NOT do

- No implementations — defaults live in [`a2a-core-default`](../a2a-core-default/).
- No transport, no Spring, no auto-configuration.
- No duplicate A2A model or error taxonomy; learn those from the
  [A2A specification](https://a2a-protocol.org) and the SDK.
