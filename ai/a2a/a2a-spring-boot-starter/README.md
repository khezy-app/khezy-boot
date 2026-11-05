# Khezy A2A Spring Boot Starter

Spring Boot auto-configuration for the official A2A Java SDK. It assembles the SDK
server graph, exposes the JSON-RPC and agent-card endpoints, maps Spring Security into
the SDK call context, and wires the client graph. The starter configures the SDK — it
does not replace it.

`io.github.khezyapp:a2a-spring-boot-starter:1.0.0` · SDK `1.4.0.Final` · Spring Boot 4.1

## One card + one executor = a working agent server

```java
@Bean
AgentCard agentCard() {
    return AgentCard.builder()
            .name("my-agent")
            .description("My A2A agent")
            .version("1.0.0")
            .capabilities(AgentCapabilities.builder().streaming(true).build())
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of(/* ... */))
            .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost:8080/a2a")))
            .url("http://localhost:8080/a2a")
            .build();
}

@Bean
AgentExecutor agentExecutor() {
    return new AbstractA2AAgentExecutor() {
        @Override
        protected void onMessage(final A2ARequest request) {
            request.complete("echo: " + request.userInput());
        }
    };
}
```

Both beans are optional: the card is built from
`io.github.khezyapp.a2a.server.agent-card.*` when no `AgentCard` bean exists, and a
missing `AgentExecutor` fails startup with an explicit message (the server is useless
without one). Set `io.github.khezyapp.a2a.server.enabled=false` for client-only apps.

## HTTP surface

| Endpoint | Purpose |
|---|---|
| `POST /a2a` (and `/a2a/`) | JSON-RPC and SSE. Accepts `application/json` and `application/a2a+json`. The SDK routes every method (`SendMessage`, `SendStreamingMessage`, `GetTask`, `ListTasks`, `CancelTask`, `SubscribeToTask`, push-config CRUD, extended card). |
| `GET /a2a/.well-known/agent-card.json` | Proto-JSON card with `ETag`, `Cache-Control: max-age` and conditional `304`. |
| `GET /a2a/.well-known/{tenant}/agent-card.json` | Tenant card via `AgentCardRouter`; unknown tenants return `404`. |

The base path is `io.github.khezyapp.a2a.server.path`.

Raw HTTP calls (curl, other languages) must send the protocol version and the proto-JSON
part shape — the SDK client library does both for you:

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":1,"method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"m1","parts":[{"text":"2 + 3"}]}}}'
```

Without `A2A-Version: 1.0` the SDK treats the request as protocol 0.3 and rejects it with
`VERSION_NOT_SUPPORTED`. A part is `{"text":"..."}` (proto-JSON oneof), not
`{"kind":"text","text":"..."}`.

## Auto-configured beans

Every bean is `@ConditionalOnMissingBean`.

| Area | Beans |
|---|---|
| Card | `AgentCard` (consumer bean or properties) |
| Stores | `A2AStoreProvider` (existing `TaskStore`/`TaskStateProvider`/`PushNotificationConfigStore` beans, otherwise in-memory) |
| Events | `MainEventBus`, `QueueManager` (`InMemoryQueueManager`), `MainEventBusProcessor`, `TaskStreamLifecycleHook`, `PushNotificationSender` |
| Execution | `a2aAgentExecutor` (virtual threads by default), `a2aEventConsumerExecutor` |
| SDK config | `a2aConfigProvider` (`SpringA2AConfigProvider`, environment first), `DefaultValuesConfigProvider` (non-candidate) |
| Security | `TaskAuthorizationProvider` (`TenantTaskAuthorizationProvider`) |
| Multi-tenancy | `AgentExecutorRouter`, `AgentCardRouter` (when enabled; override with `MapAgentExecutorRouter` / `MapAgentCardRouter`) |
| SDK handlers | `DefaultRequestHandler`, `JSONRPCHandler` |
| Transport | `A2ACallContextFactory<HttpServletRequest>`, `ServletA2AServerJsonRpcHandler`, `A2AJsonRpcController`, `AgentCardController` |
| Client | `A2AHttpClient` (SSE limits), `AgentCardSource`, `AgentCardResolver`, `ClientBuildOptions`, `A2AClientFactory`, `RemoteAgentRegistry`, `A2AInvoker`, `A2AConversationStore`, `A2AConnector`, `A2AClientOperations` |
| Spring AI (optional) | `A2AResultFormatter`, `A2ASkillToolFactory`, `A2AAgentTools`, `A2APromptProvider` (`A2APromptBuilder`) |

`A2AServerCustomizer` beans are called after the SDK handler is built.

### Replacing the queue manager (replicated queues)

`QueueManager` is a regular `@ConditionalOnMissingBean` bean: define your own and the
in-memory default backs off. The starter ships no broker dependency. For a multi-instance
deployment, build on the SDK
`a2a-java-queue-manager-replicated-core` module and pass the **same**
`TaskStateProvider` that backs your `TaskStore` (the `A2AStoreProvider` bean exposes it),
so queue and store agree on which tasks are active/finalized.

### Durable conversations and OpenTelemetry (optional)

- Add `io.github.khezyapp:a2a-store-jpa:1.0.0` with a `StringEncryptor` to replace the
  in-memory `A2AConversationStore`; the client auto-configuration runs after the JPA store
  configuration, so multi-node multi-turn resumption works without extra wiring.
- Add `io.github.khezyapp:a2a-observability-otel:1.0.0` for SDK OpenTelemetry tracing
  (see its [README](../a2a-observability-otel/)).

### Spring AI tools (optional)

`A2AToolingAutoConfiguration` is active only when `org.springframework.ai.chat.client.ChatClient`
**and** the optional `io.github.khezyapp:a2a-spring-ai` module are on the classpath. Without
them the configuration is skipped, so a plain server or client app is unaffected.

Add the module and a Spring AI model starter yourself; the starter only declares them as
optional (`compileOnly`) dependencies:

```groovy
implementation "io.github.khezyapp:a2a-spring-ai:1.0.0"
implementation "org.springframework.ai:spring-ai-starter-model-openai"
```

The tool beans are created eagerly so you can inject them, but nothing is attached to an
auto-configured `ChatClient` unless you opt in:

```properties
io.github.khezyapp.a2a.client.attach-tools-to-chat-client=true
```

With that flag the routing prompt and every skill/agent tool are added to each
`ChatClient` builder. Without it, wire the beans yourself (see
[`a2a-spring-ai`](../a2a-spring-ai/)) — that is the better default when only some chats
should delegate.

## Server properties (`io.github.khezyapp.a2a.server`)

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `path` | `/a2a` | Base path for JSON-RPC and the card |
| `agent-card.name` | `Khezy A2A Agent` | Card name |
| `agent-card.description` | `A KHEZY A2A agent` | Card description |
| `agent-card.version` | `1.0.0` | Card version |
| `agent-card.url` | `http://localhost:8080{path}` | Public JSON-RPC URL — set it in production |
| `agent-card.provider.organization` / `.url` | empty | Provider block |
| `agent-card.capabilities.streaming` | `true` | Advertise streaming |
| `agent-card.skills[]` | empty | `id`, `name`, `description`, `tags`, `examples`, `input-modes`, `output-modes` |
| `authorization.required` | `auto` | `auto` = Spring Security on the classpath; `true`/`false` override |
| `blocking.agent-timeout` | `600s` | Blocking agent wait |
| `blocking.consumption-timeout` | `60s` | Event consumption wait |
| `blocking.reconciliation-timeout` | `5s` | Task-store reconciliation wait |
| `push-notifications.enabled` | `false` | SDK push notifications |
| `multitenancy.enabled` | `false` | Default tenant routers and tenant cards |
| `populate-referred-tasks` | `false` | Resolve `referenceTaskIds` |
| `executor.virtual-threads` | `true` | Virtual threads (falls back on runtimes without them) |
| `executor.platform-threads` / `.platform-queue-capacity` | `32` / `1000` | Platform pool when virtual threads are off |
| `card-cache.max-age` | `60s` | Card `Cache-Control: max-age` |
| `call-context.tenant-header` | empty | Header carrying the SDK tenant (routing) |
| `call-context.scope-header` | empty | Header carrying the agent scope (task isolation) |

## Client properties (`io.github.khezyapp.a2a.client`)

`enabled` (`true`), `urls`, `agent-card-path`, `auth-headers`, `call-timeout` (`60s`,
per-call `SendOptions` win), `card-cache-ttl` (`5m`), `streaming`, `use-client-preference`,
`conversation-state-ttl` (`1h`), `max-conversations` (`1000`), `accepted-output-modes`,
`sse.max-line-length` / `max-buffer-lines` / `max-buffer-chars` (1 MiB / 1000 / 1 MiB),
`transport-parameters` (for SDK transport wrappers) and `attach-tools-to-chat-client`
(`false`, Spring AI only).

## What the starter does not do

- It does not replace the SDK wire model, task lifecycle or JSON-RPC routing.
- gRPC/REST **server** bindings are out of scope (JSON-RPC over HTTP in 1.0).
- Task isolation is only enforced when a `TaskAuthorizationProvider` is active
  (`authorization.required`); the in-memory store is not multi-tenant persistence.
- The JPA stores ship in `a2a-store-jpa` and activate only with a `StringEncryptor` bean.
- Replicated queues are consumer-provided; there is no built-in broker integration.
- OpenTelemetry lives in the optional `a2a-observability-otel` module; tracing is not
  enabled by the starter itself.
