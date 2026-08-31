# Khezy A2A Core

Port interfaces for an A2A agent server and host, typed on the official A2A Java SDK
spec model (`org.a2aproject.sdk.spec.*`). This module sits on top of the SDK — it owns
no wire types and adds none — it only defines the **behavior contracts** the SDK leaves
to you: how agents execute, where tasks live, how requests dispatch, and how callers
identify themselves.

`io.github.khezyapp:a2a-core:1.0.0` — depends only on `org.a2aproject.sdk:a2a-java-sdk-spec`.

## The ports

| Package | Ports | Role |
|---|---|---|
| `core.executor` | `A2AAgentExecutor`, `A2AStreamingAgentExecutor`, `AbstractA2AAgentExecutor`, `AgentExecutionContext`, `AgentEventSink`, `CallerIdentity`, `CancellationToken` | Your agent's business logic |
| `core.store` | `A2ATaskStore`, `A2AEventQueue`, `Subscription`, `PushNotificationSender` | Task persistence + event fan-out |
| `core.dispatch` | `A2ARequestDispatcher`, `CallContext` | Transport-independent request routing |
| `core.client` | `A2ARemoteAgentClient`, `A2AStreamingRemoteAgentClient`, `CallContext` | Calling remote agents |
| `core.identity` | `CallerIdentityResolver` | Resolving who is calling |
| `core.error` | `A2ACoreException`, `TaskNotFoundException`, `TaskNotCancelableException`, `TaskCancelledException`, `TaskStoreException` | Error taxonomy |

## The dual-interface executor pattern

Following Spring AI's pattern, execution comes in two flavors:

- `A2AAgentExecutor.execute(context)` → returns one final `EventKind`
  (blocking / non-streaming).
- `A2AStreamingAgentExecutor.stream(context, sink)` → emits many events through
  `AgentEventSink` (`chunk`, `completed`, `failed`).

Extend `AbstractA2AAgentExecutor` and implement only `execute()` — streaming works
automatically by emitting your result as a single completed event. Override `stream()`
when you want true incremental output.

## What this does NOT do

- No implementations — every port needs a bean (see [`a2a-core-default`](../a2a-core-default/)).
- No Spring, no HTTP — this is pure Java; transport lives in the starter.
- No protocol modeling — messages, tasks, errors are SDK types; learn them from the
  [official A2A spec](https://a2a-protocol.org), not from here.
