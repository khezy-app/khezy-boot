# KHEZY A2A — agent-to-agent on Spring Boot

The KHEZY A2A layer lets a Spring Boot application act as an **A2A agent** (serve tasks
over JSON-RPC HTTP) or as a **host** that calls other A2A agents — using the official
[A2A Java SDK](https://github.com/a2aproject/a2a-java) wire model. It sits **on top of**
the official A2A Java SDK, not instead of it: every port interface is typed on the
SDK's `org.a2aproject.sdk.spec` types, so nothing here re-models the protocol. What we
add is the part the SDK leaves open — behavior contracts (execution, storage, dispatch,
client calls, identity), sensible defaults, and Spring Boot auto-configuration.

## Modules

| Module | Coordinates | What you get |
|---|---|---|
| [`a2a-core`](a2a-core/) | `io.github.khezyapp:a2a-core:1.0.0` | Port interfaces + error taxonomy, typed on the SDK spec model |
| [`a2a-core-default`](a2a-core-default/) | `io.github.khezyapp:a2a-core-default:1.0.0` | In-memory stores, request dispatcher, SDK-based remote client |
| [`a2a-spring-ai`](a2a-spring-ai/) | `io.github.khezyapp:a2a-spring-ai:1.0.0` | Adapts a Spring AI `ChatClient` into an agent executor (optional) |
| [`a2a-spring-boot-starter`](a2a-spring-boot-starter/) | `io.github.khezyapp:a2a-spring-boot-starter:1.0.0` | Auto-configuration, JSON-RPC endpoints, properties, caller identity |
| [`samples`](samples/) | `0.0.1-SNAPSHOT` | Runnable math-agent server + host-agent client |

Baseline: A2A Java SDK `org.a2aproject.sdk:1.2.0.Final`, Spring AI `2.0.1`
(optional module only), Spring Boot `4.1.0`, Java 17.

## Quick start

Add the starter, define **one executor bean**, done:

```groovy
dependencies {
    implementation "io.github.khezyapp:a2a-spring-boot-starter:1.0.0"
}
```

```properties
io.github.khezyapp.a2a.agent-card-name=My Agent
io.github.khezyapp.a2a.agent-card-url=http://localhost:8080
```

```java
@Component
public class EchoAgentExecutor extends AbstractA2AAgentExecutor {

    @Override
    public EventKind execute(final AgentExecutionContext context) {
        final var taskId = context.currentTask().map(Task::id).orElse(UUID.randomUUID().toString());
        final var contextId = context.currentTask().map(Task::contextId).orElse(taskId);
        final var reply = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("echo")))
                .taskId(taskId)
                .contextId(contextId)
                .build();
        return new TaskStatusUpdateEvent(
                taskId,
                new TaskStatus(TaskState.TASK_STATE_COMPLETED, reply, OffsetDateTime.now()),
                contextId,
                Map.of());
    }
}
```

The starter wires everything else: task store, event queue, dispatcher, agent card,
HTTP endpoints (`POST /message/send`, `POST /message/stream` SSE, `GET /.well-known/
agent-card.json`). See the [starter README](a2a-spring-boot-starter/) for the full
property table and curl examples, and [`samples`](samples/) for two runnable apps that
do this end to end.

## What this does NOT do

Honest boundaries, so you can plan around them:

- **JSON-RPC over HTTP only.** The official SDK also speaks gRPC and REST; no gRPC
  transport is wired yet.
- **In-memory state only.** Tasks and event queues live in memory — restart loses
  history, and multiple instances behind a load balancer do not share task state.
  Durable stores are your job (implement `A2ATaskStore`).
- **No upstream contribution yet.** Improvements stay local in v1; PRing pieces back
  to the A2A project is deliberately deferred.
- **No reactive API in core.** `AgentEventSink` is callback-based by design; Flux-style
  wrappers would be a future add-on module.

You will still need to learn the A2A concepts themselves — agents, tasks, messages,
agent cards — from the official spec. This layer removes boilerplate, not knowledge.

## Who it's for

- **Beginners:** working A2A infrastructure without reading the whole protocol spec first.
- **Experienced developers:** skip JSON-RPC endpoint, dispatch-lifecycle, and
  identity-propagation boilerplate and keep only your agent's business logic.
- **Bootstrap projects:** an MVP agent server in one sprint, with clean seams to swap
  in durable stores later.
