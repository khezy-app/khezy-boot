# A2A-07 — `SdkA2ARemoteAgentClient` + end-to-end integration test

**Prerequisite task files:** `00-plan-overview.md`, `02-a2a-core-ports.md`, `03-a2a-core-default.md`, `06-starter-http-endpoints.md`.

**Code locations:** `ai/a2a/a2a-core-default/src/main/java` (client default) and `ai/a2a/a2a-spring-boot-starter/src/test/java` (integration test).

---

## 1. Goal

Close the loop on the client side: default implementation of `A2ARemoteAgentClient`
(and its streaming variant) over the SDK's JSON-RPC client, then an **end-to-end
integration test** that boots the starter over HTTP and drives it through our own client
port — replicating the community project's `A2AClientServerIntegrationTests` scenario.

## 2. Context digest

- Design §4.4 (client ports) + §8 Phase 3/4; the default wraps `a2a-java-sdk-client`.
- SDK client coordinates already needed in `a2a-core-default/build.gradle`:
  ```groovy
  implementation "org.a2aproject.sdk:a2a-java-sdk-client:${a2aSdkVersion}"
  implementation "org.a2aproject.sdk:a2a-java-sdk-client-transport-jsonrpc:${a2aSdkVersion}"
  ```
  (Add them in this task; `a2aSdkVersion` ext already exists.)
- SDK client API (verify details with `javap`): `org.a2aproject.sdk.client.ClientBuilder`
  + `org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportProvider` /
  `JSONRPCTransportConfigBuilder`; discovery via the client's agent-card call; messaging
  via `ClientTaskManager` or `Client.sendMessage(...)`. The exact method names must be
  confirmed from the jars (`00` §6.5) — do not guess.
- The integration test lives in the **starter's** test sources so it can boot
  `@SpringBootTest` with the auto-configuration + controllers. `spring-boot-starter-webmvc-test`
  (already a test dep) provides `@SpringBootTest` support.
- Optional (Phase 3 migration shortcut, may be skipped): an `SdkRequestHandlerBridge`
  adapter that delegates an `A2ARequestDispatcher` to the SDK's `DefaultRequestHandler`.
  Only add it if the direct path proves insufficient — the final architecture routes HTTP
  through OUR dispatcher.

## 3. Class to create: `io.github.khezyapp.a2a.core.default.client.SdkA2ARemoteAgentClient`

Implements `A2ARemoteAgentClient` + `A2AStreamingRemoteAgentClient`.

```java
public final class SdkA2ARemoteAgentClient implements A2ARemoteAgentClient, A2AStreamingRemoteAgentClient {

    public SdkA2ARemoteAgentClient(final Client sdkClient, final Duration defaultTimeout) { ... }
    // optionally a static factory: forJsonRpc(URI baseUrl, Duration defaultTimeout)
}
```

Behavior:
- `discover(URI baseUrl)` → returns `AgentCard` from the SDK client (e.g. `client.agentCard(...)`; confirm exact method via javap).
- `sendMessage(Message message, Duration timeout)` → sends via the SDK client's task manager (JSON-RPC over HTTP); returns the final `EventKind` (`TaskStatusUpdateEvent`, `Message`, or `Task`).
- `streamMessage(Message message, Consumer<EventKind> listener)` → subscribes to the SSE stream and forwards each received event to the listener.
- `close()` → closes the underlying SDK client.
- Map SDK exceptions (transport errors, `A2AError` payloads) to our error taxonomy where reasonable; keep it thin.

## 4. Integration test: `ai/a2a/a2a-spring-boot-starter/src/test/java/.../A2AClientServerIntegrationTest`

- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` on a
  test application class that imports the auto-configuration (either `@Import(A2AAutoConfiguration.class)`
  + `@Import(A2AWebAutoConfiguration.class)`, or the real `@SpringBootApplication` test slice if simpler).
- Provide an **echo executor** `@TestConfiguration` bean implementing `AbstractA2AAgentExecutor`
  (returns a `TaskStatusUpdateEvent` echoing the incoming text) so no real LLM is needed.
- Test flow (replicates the community scenario, minus their TODO):
  1. Build `SdkA2ARemoteAgentClient.forJsonRpc(uri("http://localhost:" + port), Duration.ofSeconds(10))`.
  2. `discover(baseUrl)` → assert non-null `AgentCard` with our configured name.
  3. `sendMessage(<message with text part>)` → assert a final completed `TaskStatusUpdateEvent`; assert the reply contains the echoed text.
  4. `onGetTask` equivalent through the client (or HTTP) → assert the task exists with `TASK_STATE_COMPLETED`.
  5. Optional streaming: `streamMessage(...)` → assert at least one chunk event received.
- Use `@LocalServerPort int port` (or `@Value("${local.server.port}")`).

## 5. Verification

```bash
./gradlew :ai:a2a:a2a-core-default:check   # client default compiles + its tests pass
./gradlew :ai:a2a:a2a-spring-boot-starter:test --tests '*A2AClientServerIntegrationTest'
./gradlew :ai:a2a:a2a-spring-boot-starter:check
```

## 6. Handoff note for the next task

> Record this handoff by appending an entry to `99-handoff-log.md` (template inside;
> protocol in `00` §6.7). Chat is NOT the handoff channel.

Document the exact SDK client method names used (`ClientBuilder`/`Client`/`ClientTaskManager`)
and any friction found. A2A-08 builds sample apps reusing `SdkA2ARemoteAgentClient`.
Then run `graphify update .`.

