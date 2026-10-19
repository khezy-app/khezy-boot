# A2A-04 — `a2a-spring-ai`: ChatClient adapter

**Prerequisite task files:** `00-plan-overview.md`, `02-a2a-core-ports.md`.

**Do NOT modify** other modules. All code lives in `ai/a2a/a2a-spring-ai/src/main/java` (package `io.github.khezyapp.a2a.springai`).

---

## 1. Goal

Provide the optional Spring AI convenience layer from design §4.1: adapt a Spring AI
`ChatClient` into an `AbstractA2AAgentExecutor` with blocking + streaming variants —
equivalent ergonomics to the community project's `ChatClientExecutorHandler`, but typed
on our `AgentExecutionContext` (no SDK internals leaked into user code).

## 2. Context digest

- Spring AI **2.0.1**: `org.springframework.ai:spring-ai-client-chat` already declared in the module's `build.gradle` (A2A-01). `ChatClient` = `org.springframework.ai.chat.client.ChatClient`.
- Add the Mockito plugin for tests only: `plugins { id("khezy.java-use-mockito") }` in `build.gradle` (it applies junit5 + mockito agent; `khezy.java-library` is already there — applying both is fine).
- Design §4.1 gives the exact shape: `ChatClientMessageHandler.handle(ChatClient, AgentExecutionContext)` returns `String`; `StreamingChatClientMessageHandler.handle(ChatClient, AgentExecutionContext, AgentEventSink)`; `ChatClientExecutors.from(...)` static factories.
- SDK shape: the natural "final event" for a text reply is a `TaskStatusUpdateEvent` whose `TaskStatus` carries a `Message` with an assistant `TextPart` (see `00` §3.2: `TaskStatusUpdateEvent(taskId, TaskStatus, contextId, metadata)`).

## 3. Classes to create (package `io.github.khezyapp.a2a.springai`)

### 3.1 `ChatClientMessageHandler`

```java
public interface ChatClientMessageHandler {

    /** Handle one incoming message and return the agent's text reply. */
    String handle(ChatClient chatClient, AgentExecutionContext context);
}
```

### 3.2 `StreamingChatClientMessageHandler`

```java
public interface StreamingChatClientMessageHandler {

    /** Handle one incoming message, emitting incremental results onto the sink. */
    void handle(ChatClient chatClient, AgentExecutionContext context, AgentEventSink sink);
}
```

### 3.3 `ChatClientExecutors` (final utility class, private constructor)

Factory methods (both return `AbstractA2AAgentExecutor`):

```java
public static AbstractA2AAgentExecutor from(final ChatClient chatClient,
        final ChatClientMessageHandler blocking) {
    // returns an executor whose execute() delegates to blocking.handle(...)
    // and whose stream() is the AbstractA2AAgentExecutor fallback (single completed event)
}

public static AbstractA2AAgentExecutor from(final ChatClient chatClient,
        final ChatClientMessageHandler blocking,
        final StreamingChatClientMessageHandler streaming) {
    // execute() -> blocking; stream() -> streaming (override)
}
```

**Blocking adapter `execute(context)` must:**
1. Extract the user text from `context.incoming()` — concatenate `TextPart.text()` across parts (first text part is enough; see SDK `TextPart` accessor).
2. `final String reply = blocking.handle(chatClient, context);`
3. Build the reply `Message`: `Message.builder().role(Message.Role.ROLE_ASSISTANT)` (verify the exact `Role` constant name via `javap org.a2aproject.sdk.spec.Message$Role`; adjust if different), `.parts(List.of(TextPart.builder().text(reply).build()))` (verify `TextPart` builder), `.taskId(context.incoming().taskId()).contextId(context.incoming().contextId()).build()`.
4. Return `new TaskStatusUpdateEvent(taskId, new TaskStatus(TASK_STATE_COMPLETED, replyMessage, OffsetDateTime.now()), contextId, Map.of())`.

**Streaming adapter `stream(context, sink)` must:**
1. Extract user text as above.
2. `chatClient.prompt().user(userText).stream().content()` → `Flux<String>`. Subscribe:
   - each element → `sink.chunk(TextPart.builder().text(chunk).build())` (reactive `doOnNext`); keep it simple and synchronous enough for tests.
   - completion → `sink.completed(<final TaskStatusUpdateEvent as in blocking>)`; on error → `sink.failed(new A2AError(-32000, message, Map.of()))`.
3. Handle null/blank user text: emit `sink.failed(new A2AError(-32602, "Invalid params: no text part", Map.of()))`.

> Keep the reactive details minimal (no Reactor API surface in public signatures). The
> executor contract is callback-based; do NOT return a `Flux` from our own API.

## 4. Tests (`src/test/java`)

Mock `ChatClient` and `ChatClient.ChatClientRequestSpec` with Mockito:
- `ChatClientExecutorsTest.fromBlocking` — returned executor is `AbstractA2AAgentExecutor`; `execute(context)` calls the handler and returns a `TaskStatusUpdateEvent` carrying the handler's reply text; `stream` (fallback) emits one `completed` and no `failed`.
- `ChatClientExecutorsTest.fromBoth` — `stream` delegates to the streaming handler (assert handler invoked).
- `ChatClientExecutorsTest.textExtraction` — an incoming `Message` whose first part is a `TextPart` yields the right user prompt.
- Stub handler implementations (lambdas) instead of mocking handlers.

Fixture note: building SDK `Message`/`TextPart` records requires knowing their builders —
verify with `javap -classpath /tmp/a2a-spec.jar org.a2aproject.sdk.spec.TextPart` (jar from `00` §6.5) before writing the fixture.

## 5. Verification

```bash
./gradlew :ai:a2a:a2a-spring-ai:test
./gradlew :ai:a2a:a2a-spring-ai:checkstyleMain :ai:a2a:a2a-spring-ai:checkstyleTest
./gradlew :ai:a2a:a2a-spring-ai:check
```

## 6. Handoff note for the next task

> Record this handoff by appending an entry to `99-handoff-log.md` (template inside;
> protocol in `00` §6.7). Chat is NOT the handoff channel.

Confirm the `Message.Role` constant name and `TextPart` builder shape discovered. Note
the exact return-event shape produced by the adapters (used by sample apps in A2A-08).
Then run `graphify update .`.

