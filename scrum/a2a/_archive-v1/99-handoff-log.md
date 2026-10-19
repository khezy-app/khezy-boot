# A2A v1 — Task Handoff Log

> **Protocol (binding for every agent working on this plan).**
> Context is handed off **in this file, never in chat**. Chat messages are lost when a
> session ends and are re-sent as history on every turn of the same session — writing
> handoff context in chat wastes tokens and does not reach the next task's agent.
>
> **When you FINISH a task:** append one entry below using the template, then stop.
> Do NOT paste the entry into your final chat message — reply in chat only that the
> task is complete and the log entry was written.
>
> **When you START a task:** read (1) `00-plan-overview.md`, (2) your task file,
> (3) the LAST entry in this file for each prerequisite task. That is all the context
> you need — do not re-explore source code beyond what the entries point to.

## Entry template

```markdown
## A2A-XX — <task title>

- **Status:** completed | partial (explain) 
- **Date:** YYYY-MM-DD
- **Verification:** <commands run + result, e.g. `./gradlew :ai:a2a:a2a-core:check` PASS>
- **What was built:** <1–3 bullets: files/packages created, decisions taken>
- **Deviations from task spec:** <anything that differs from §4/§5 of the task file,
  e.g. SDK signature surprises, renamed types, extra helpers — downstream tasks rely
  on REAL shapes, not planned ones>
- **Gotchas for next tasks:** <non-obvious constraints, pitfalls, things checkstyle
  or tests forced>
```

---

## A2A-01 — Skeleton: scaffold library modules + root registration

- **Status:** completed
- **Date:** 2026-08-23 (backfilled from repo state)
- **Verification:** modules registered in root `settings.gradle`; builds resolve.
- **What was built:** 4 library modules created under `ai/a2a/` — `a2a-core`,
  `a2a-core-default`, `a2a-spring-ai`, `a2a-spring-boot-starter` — each an isolated
  Gradle build (`settings.gradle` includes `../../build-logic`) and registered via
  `includeBuild("ai/a2a/<name>")` in root `settings.gradle`.
- **Deviations from task spec:** package root of `a2a-core-default` is
  `io.github.khezyapp.a2a.coredef` (not `...core.default` — `default` is a Java
  keyword). Placeholder marker classes exist and are expected to be deleted by later
  tasks.
- **Gotchas for next tasks:** cross-build deps resolve by published coordinates
  (`io.github.khezyapp:a2a-core:1.0.0`) substituted by the composite build — never
  add project() refs across module builds.

## A2A-02 — `a2a-core`: port interfaces, records, error taxonomy

- **Status:** completed
- **Date:** 2026-08-23 (backfilled from repo state)
- **Verification:** `CallerIdentityTest`, `ErrorTaxonomyTest`,
  `AbstractA2AAgentExecutorTest` present under
  `ai/a2a/a2a-core/src/test/java/io/github/khezyapp/a2a/core/`.
- **What was built:** full port set in `ai/a2a/a2a-core/src/main/java/io/github/khezyapp/a2a/core/`:
  `error/` (A2ACoreException, TaskNotCancelableException, TaskNotFoundException,
  TaskCancelledException, TaskStoreException), `executor/` (A2AAgentExecutor,
  A2AStreamingAgentExecutor, AbstractA2AAgentExecutor, AgentEventSink,
  AgentExecutionContext, CallerIdentity, CancellationToken), `store/` (A2ATaskStore,
  A2AEventQueue, Subscription, PushNotificationSender), `dispatch/`
  (A2ARequestDispatcher, CallContext), `client/` (A2ARemoteAgentClient,
  A2AStreamingRemoteAgentClient), `identity/` (CallerIdentityResolver).
- **Deviations from task spec:** none known at backfill time — signatures match the
  task §4 listings (SDK base error caught in AbstractA2AAgentExecutor is
  `org.a2aproject.sdk.spec.A2AError`).
- **Gotchas for next tasks:** these interfaces are contract — implement against the
  real files, not the design doc examples. CallerIdentity has an `anonymous()`
  factory. Subscription extends AutoCloseable with close() delegating to
  unsubscribe().

## A2A-03 — `a2a-core-default`: dispatcher + in-memory stores

- **Status:** completed
- **Date:** 2026-08-23
- **Verification:** `./gradlew :a2a-core-default:check` green (17 tests + checkstyleMain/checkstyleTest).
- **What was built:** under `ai/a2a/a2a-core-default/src/main/java/io/github/khezyapp/a2a/coredef/`:
  `store/InMemoryTaskStore`, `store/InMemoryEventQueue`, `store/NoopPushNotificationSender`,
  `executor/DefaultAgentExecutionContext`, `executor/SimpleCancellationToken`,
  `dispatch/DefaultA2ARequestDispatcher` (with inner `StreamingFallback` bridge and
  persisting `PersistingEventSink`). Tests mirror main in
  `src/test/java/io/github/khezyapp/a2a/coredef/`.
- **Deviations from task spec:**
  - Package root is `coredef` (task doc said `core.default` — invalid Java).
  - `A2AError` implements SDK `Event`, NOT `EventKind`, so it cannot be published via
    `A2AEventQueue.publish(EventKind)`. Failure path instead publishes a
    `TaskStatusUpdateEvent(TASK_STATE_FAILED)` whose metadata carries
    `errorCode`/`errorMessage`; the raw `A2AError` is still rethrown to the caller.
  - Generic `RuntimeException` from executor is wrapped in `A2ACoreException` (per spec's
    "wrap if preferred") after publishing failed status.
  - `TaskCancelledException` handling returns a canceled `TaskStatusUpdateEvent`.
- **SDK facts confirmed (A2A Java SDK spec 1.2.0.Final):**
  - `Task.builder().id().contextId().status(new TaskStatus(state)).history(List<Message>).build()`;
    `Task.builder(Task)` copy-constructor used by `InMemoryTaskStore.updateState`.
  - `Message.builder()` requires non-empty `parts(...)` (IllegalArgumentException otherwise)
    and role constants are `Message.Role.ROLE_USER/ROLE_AGENT/ROLE_UNSPECIFIED`.
  - `TaskStatusUpdateEvent(taskId, TaskStatus, contextId, metadata)`; metadata may be null
    on `MessageSendParams.metadata()` — dispatcher normalizes to `Map.of()`.
  - `AgentCard.builder()` minimum viable: name/description/version.
- **Dispatcher constructor signature (for A2A-05 starter wiring):**
  `(A2AAgentExecutor, A2ATaskStore, A2AEventQueue, PushNotificationSender, Supplier<AgentCard>)`.
  Note `Message.taskId()` blank → dispatcher generates UUID for both taskId and contextId.

## A2A-04 — `a2a-spring-ai`: ChatClient adapter

- **Status:** completed
- **Date:** 2026-08-23
- **Verification:** `./gradlew -p ai/a2a/a2a-spring-ai check` green (5 tests +
  checkstyleMain/checkstyleTest).
- **What was built:** in
  `ai/a2a/a2a-spring-ai/src/main/java/io/github/khezyapp/a2a/springai/`:
  `ChatClientMessageHandler`, `StreamingChatClientMessageHandler` interfaces and
  `ChatClientExecutors` with `from(chatClient, blocking)` /
  `from(chatClient, blocking, streaming)` factories plus a public
  `reactiveStreamingHandler()` (built-in Flux-driven handler: chunk per content element,
  completed with accumulated text, failed on error / missing text part). Mockito plugin
  added to the module's `build.gradle`; placeholder `SpringAiMarker` deleted;
  module `settings.gradle` now `includeBuild("../a2a-core")`.
- **SDK facts confirmed:**
  - Assistant role constant is `Message.Role.ROLE_AGENT` (no ROLE_ASSISTANT exists).
  - `TextPart` has NO builder — it is a record: `new TextPart(text)` or
    `new TextPart(text, metadata)`. Same for reading: `.text()`, `.metadata()`.
  - Return event shape produced by both adapters:
    `new TaskStatusUpdateEvent(taskId, new TaskStatus(TASK_STATE_COMPLETED,
    Message{role=ROLE_AGENT, parts=[TextPart(reply)], taskId, contextId},
    OffsetDateTime.now()), contextId, Map.of())` — taskId/contextId copied from the
    incoming message.
- **Deviations from task spec:**
  - The 3-arg factory's `stream()` delegates DIRECTLY to the user's streaming handler —
    it does NOT prompt the ChatClient itself. The reactive pipeline from §3.3 lives in
    the exposed `ChatClientExecutors.reactiveStreamingHandler()` instead. This matches
    "stream() -> streaming (override)" and keeps handler stubs testable without stubbing
    ChatClient.
  - Blocking path throws `A2AError(-32602, ...)` when no non-blank TextPart found and
    `A2AError(-32000, "Agent returned no reply", ...)` on null reply.
  - Records can't extend classes — adapters are private static final classes, not records.
  - Module tests must run as `./gradlew -p ai/a2a/a2a-spring-ai <task>`; the root build
    cannot address included-build tasks by `:ai:a2a:a2a-spring-ai:test`.
- **Gotchas for next tasks:** Spring AI 2.0.1 fluent chain verified:
  `chatClient.prompt().user(String).stream().content()` → `Flux<String>`.
  `ChatClient.ChatClientRequestSpec` mock cleanly via
  `Mockito.mock(spec.class, RETURNS_SELF)`. SDK `Message.builder()` rejects empty parts
  list (fixture for "no text part" case uses a blank TextPart instead).

## A2A-05 — `a2a-spring-boot-starter`: auto-configuration, properties, identity

- **Status:** completed
- **Date:** 2026-08-23
- **Verification:** `./gradlew -p ai/a2a/a2a-spring-boot-starter check` green (16 tests +
  checkstyleMain/checkstyleTest). Note: task doc's `./gradlew :ai:a2a:...` path form does
  NOT work from root — use `-p ai/a2a/<module>` like A2A-04.
- **What was built:** in
  `ai/a2a/a2a-spring-boot-starter/src/main/java/io/github/khezyapp/a2a/starter/`:
  `A2AAutoConfiguration`, `A2AProperties`; sub-package `security/`:
  `SecurityCallerIdentityResolver`.
  Resources: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  with the single line `io.github.khezyapp.a2a.starter.A2AAutoConfiguration`.
  Placeholder `StarterMarker` deleted. Module `settings.gradle` now has
  `includeBuild("../a2a-core")` + `includeBuild("../a2a-core-default")` so standalone
  `-p` builds resolve cross-build deps.
- **Bean names (for A2A-06):** `taskStore`, `eventQueue`, `pushNotificationSender`,
  `agentCardSupplier`, `callerIdentityResolver`, `a2aRequestDispatcher`. All package-private
  `@Bean` methods, each `@ConditionalOnMissingBean`. There is NO default
  `A2AAgentExecutor` bean — the dispatcher bean only materializes when the app defines
  an executor; without one the context fails fast on missing dependency.
- **AgentCard builder components used:** `name`, `description`, `url`, `version`,
  `capabilities(AgentCapabilities.builder().streaming(props.isStreamingEnabled()))`,
  `defaultInputModes(List.of("text"))`, `defaultOutputModes(List.of("text"))`,
  `skills(List.of())`,
  `supportedInterfaces([new AgentInterface(TransportProtocol.JSONRPC.asString(), url)])`
  — interface entry added ONLY when url is non-blank.
- **SDK facts confirmed (spec 1.2.0.Final):**
  - `AgentCard.build()` hard-requires non-null: `name`, `description`, `capabilities`,
    `defaultInputModes`, `defaultOutputModes`, `skills`, `supportedInterfaces`. Builder
    defaults do NOT cover them — omitting any throws IllegalArgumentException. `url`
    may be null/blank.
  - `AgentInterface` canonical component order is `(protocolBinding, url, tenant,
    protocolVersion)` — binding FIRST, counterintuitive vs spec prose. Use
    `TransportProtocol.JSONRPC.asString()` for the binding value.
- **A2AProperties field set (prefix `io.github.khezyapp.a2a`):** `agentCardName`
  ("Khezy A2A Agent"), `agentCardDescription` (""), `agentCardUrl` (""), 
  `agentCardVersion` ("1.0.0"), `streamingEnabled` (true). Lombok @Getter/@Setter.
- **Gotchas for next tasks:** `callerIdentityResolver` bean is gated by
  `@ConditionalOnClass(name = "...SecurityContextHolder")` (string form — security is
  compileOnly); SecurityCallerIdentityResolver maps authorities→scopes via
  `Collectors.toUnmodifiableSet()` and claims via `Map.of("principal", getPrincipal())`.
  Checkstyle RedundantModifier forbids `final` on try-with-resources variables;
  UnusedImports flagged two type-only-used-for-inference imports in tests.

## A2A-06 — `a2a-spring-boot-starter`: JSON-RPC HTTP endpoints

- **Status:** completed
- **Date:** 2026-08-23
- **Verification:** `./gradlew -p ai/a2a/a2a-spring-boot-starter check` PASS (30 tests +
  checkstyleMain/checkstyleTest). Root path form (`:ai:a2a:...`) still does NOT work —
  use `-p` like A2A-04/05.
- **What was built:** package `io.github.khezyapp.a2a.starter.web`:
  `HttpCallContext`, `JsonRpcEnvelope`, `JsonRpcParamMapper`, `SseAgentEventSink`,
  `AgentCardController`, `MessageController`, `TaskController`, `A2AWebAutoConfiguration`
  (+ line appended to `AutoConfiguration.imports`). Tests: `web/{AgentCardControllerTest,
  MessageControllerTest, MessageControllerStreamTest, TaskControllerTest, HttpCallContextTest,
  WebFixtures}` + shared slice anchor `starter/TestBootConfiguration`;
  `AutoConfigurationImportsTest` now asserts BOTH registrations.
- **Routes:** GET `/.well-known/agent-card.json`; POST `/message/send`, `/message/stream`
  (SSE); POST `/tasks/get`, `/tasks/cancel`. Controllers depend ONLY on
  `A2ARequestDispatcher` + `CallerIdentityResolver` + Jackson 3 `ObjectMapper`.
- **Deviations from task spec (REAL shapes — rely on these):**
  - SDK wrappers are Gson-based with NO Jackson creator metadata: you CANNOT
    `readValue(body, SendMessageRequest.class)`. Requests are read as `JsonNode`;
    `JsonRpcParamMapper` maps `params` → spec records: `MessageSendParams`
    (message/configuration/metadata/tenant), `TaskQueryParams`(id/historyLength/tenant),
    `TaskIdParams`(id/tenant) for cancel — dispatcher port takes `TaskIdParams`, NOT
    `CancelTaskParams`.
  - Responses use our `JsonRpcEnvelope.Envelope(jsonrpc,id,result,error)` +
    `ErrorPayload(code,message,details)` records (NON_NULL) instead of
    `SendMessageResponse`/`GetTaskResponse`: identical wire shape, but avoids
    serializing `A2AError` (a RuntimeException → stackTrace noise) and `"error":null`.
  - Error mapping `JsonRpcEnvelope.toA2AError(Throwable)`: `TaskNotFoundException` →
    `new TaskNotFoundError(null, Map.of("taskId", id))` → code **-32001**,
    msg "Task not found"; `TaskNotCancelableException` → `new TaskNotCancelableError(
    null, null, Map.of("taskId", id))` → **-32002**, "Task cannot be canceled"; other
    `A2AError` passthrough; anything else → `InternalError(msg)` → **-32603**. Unknown
    method → `MethodNotFoundError()` → **-32601**; bad parts/params →
    `InvalidParamsError` → **-32602**. NOTE: first String arg of these SDK error ctors
    is a MESSAGE OVERRIDE (null keeps the default message).
  - message/send and message/stream are SEPARATE routes under `/message` (task doc
    hinted one POST /message branching on method) so handlers keep precise return
    types (`ResponseEntity<Envelope>` vs `SseEmitter`); the JSON-RPC `method` field is
    still validated against the route.
  - SSE approach: SYNCHRONOUS dispatch into `SseEmitter(0L)` (blessed by task §3.3).
    Every callback becomes one JSON-RPC envelope `data:` frame; intermediate events
    carry a synthetic result `{kind: status-update|artifact-update|chunk, payload}`;
    completed/failed frames close the stream; `finishIfOpen()` guards executors that
    never emit a terminal event; `closeWithFailure(Throwable)` for controller-side
    errors.
  - Part polymorphism resolved from wire `kind`: text→`TextPart(text, meta?)`,
    data→`DataPart(converted Object, meta?)`, file→URI-only
    `FileWithUri(mimeType?, name?, uri)`; base64 file parts → InvalidParamsError
    (`FileWithBytes` needs sdk-common runtime, not guaranteed here).
- **Known limitation for A2A-07:** Jackson 3 serializes records by COMPONENTS ONLY —
  `EventKind` results have NO `"kind"` discriminator on the wire (Message/
  TaskStatusUpdateEvent `kind()` accessors are ignored). Envelope shape is correct;
  add discriminator handling if strict client interop is needed later.
- **Gotchas for next tasks:**
  - Boot 4 moved test annotations: `org.springframework.boot.webmvc.test.autoconfigure.
    {WebMvcTest, AutoConfigureMockMvc}` — NOT `...boot.test.autoconfigure.web.servlet.*`.
  - Boot 4 `@WebMvcTest` REQUIRES a discoverable `@SpringBootConfiguration` (even with
    explicit `@Import`) AND does NOT register `controllers={...}` classes by itself —
    tests must `@Import({ControllerUnderTest.class, ...})`. Shared anchor
    `TestBootConfiguration` has NO `@ComponentScan` on purpose: scanning would pick up
    `A2AWebAutoConfiguration` → duplicate controller beans → ambiguous mapping errors.
  - Use `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`) —
    Boot's `@MockBean` is gone. Security on test classpath ⇒
    `@AutoConfigureMockMvc(addFilters = false)` to bypass the filter chain in slices.
  - `AgentEventSink.chunk` takes RAW `Part` (not `Part<?>`); `SseEmitter.send` throws
    checked `IOException`; checkstyle forbids `final` on try-with-resources variables.

## A2A-07 — `SdkA2ARemoteAgentClient` + end-to-end integration test

- **Status:** completed
- **Date:** 2026-08-23
- **Verification:** `./gradlew :a2a-core-default:check :a2a-core-default:checkstyleMain :a2a-core-default:checkstyleTest` PASS; `./gradlew :a2a-spring-boot-starter:check :a2a-spring-boot-starter:checkstyleMain :a2a-spring-boot-starter:checkstyleTest` PASS; `./gradlew :a2a-spring-boot-starter:test --tests '*A2AClientServerIntegrationTest'` → 4 tests, 0 failures. (Use the `:<module>:<task>` form from the ROOT — the task doc's `:ai:a2a:...` path form does NOT work in this composite, same as A2A-04/05.)
- **What was built:**
  - `a2a-core-default` deps: added `a2a-java-sdk-client` + `a2a-java-sdk-client-transport-jsonrpc:1.2.0.Final`.
  - `io.github.khezyapp.a2a.coredef.client.SdkA2ARemoteAgentClient` — implements `A2ARemoteAgentClient` + `A2AStreamingRemoteAgentClient`; ctor `(Client, Duration)`, static `forJsonRpc(URI, Duration)`, `discover(URI)`, `sendMessage(Message, Duration)` (CompletableFuture bridge — first terminal event wins, single deadline via `terminal.get(timeout)`; daemon executor runs the SDK send off the caller thread), `streamMessage(Message, Consumer<EventKind>)`, `close()`.
  - `a2a-spring-boot-starter` deps: added `a2a-java-sdk-spec-grpc` (the proto-JSON codec).
  - `io.github.khezyapp.a2a.starter.web.SdkA2AJsonRpcController` — **the SDK-interop endpoint**: `POST /` dispatching on JSON-RPC `method` (`SendMessage`/`SendStreamingMessage`/`GetTask`/`CancelTask`), speaking the SDK's proto-JSON wire format via `JSONRPCUtils`/`ProtoUtils`. Plus `web/SdkSseEventSink` (proto `StreamResponse` SSE frames). Both registered in `A2AWebAutoConfiguration`. The A2A-06 path controllers (`/message/*`, `/tasks/*`) remain untouched for v0.3-style routes.
  - `starter/A2AClientServerIntegrationTest` — `@SpringBootTest(RANDOM_PORT)` + `TestBootConfiguration`, echo executor + lazily-built agent-card `Supplier<AgentCard>` (name `agentCardSupplier`, overrides the auto-config default) as `@TestConfiguration`.
- **Deviations from task spec (REAL shapes — rely on these):**
  - **The 1.2.0.Final SDK client speaks the NEW protobuf-based JSON-RPC protocol.** `A2AMethods` are camelCase (`SendMessage`, `SendStreamingMessage`, `GetTask`, `CancelTask`) — NOT the v0.3-style `message/send`/`message/stream`/`tasks/get`/`tasks/cancel` the A2A-06 controllers use. Params AND results are proto-JSON (`JsonFormat`), and `JSONRPCTransport` posts to the agent's **base URL root** (`POST /`), NOT to `/message/send`. A2A-06's Jackson/Gson-style envelopes are NOT parseable by the SDK client — hence the new root controller.
  - Blocking `SendMessage` result MUST be a proto `SendMessageResponse` (oneof `message`/`task`). `ProtoUtils.ToProto.taskOrMessage(EventKind)` throws for a `TaskStatusUpdateEvent`, so the controller lifts a final `TaskStatusUpdateEvent` into a `Task` result first. (Our own client always uses the streaming path, so this is only hit by external blocking clients.)
  - Echo executor returns a `TaskStatusUpdateEvent` whose `status` carries a `Message{ROLE_AGENT, [TextPart(echo)], taskId, contextId}` (A2A-04 shape). Through the SDK streaming client it arrives as `TaskUpdateEvent` whose `getUpdateEvent()` is the `TaskStatusUpdateEvent` — the test asserts the reply via `update.status().message()`.
  - `UpdateEvent` (return type of `TaskUpdateEvent.getUpdateEvent()`) is NOT an `EventKind` subtype (its two permitted impls are) — client casts to `EventKind`.
  - Agent-card discovery (`A2A.getAgentCard`) parses the served card with proto `JsonFormat` (ignoringUnknownFields=true); the Jackson-3-serialized `AgentCard` record from `AgentCardController` round-trips fine (verified by the passing test).
- **SDK client method names used (for A2A-08):**
  - Discovery: `A2A.getAgentCard(String url)` → `AgentCard` (fetches `<url>/.well-known/agent-card.json`).
  - Build: `Client.builder(card).clientConfig(ClientConfig).withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder()).build()`; `ClientConfig.builder().setStreaming(true).build()`.
  - Messaging: `Client.sendMessage(Message, List<BiConsumer<ClientEvent, AgentCard>>, Consumer<Throwable>, ClientCallContext)` — void; BLOCKING when `!config.isStreaming() || !card.capabilities().streaming()`, otherwise async SSE. No `ClientTaskManager` exposure (package-private).
  - `ClientEvent` impls: `MessageEvent.getMessage()`, `TaskEvent.getTask()`, `TaskUpdateEvent.getTask()/getUpdateEvent()`.
  - `Client.getTask(TaskQueryParams, ClientCallContext)`, `Client.close()`.
- **Gotchas for next tasks:**
  - `SSEEventListener.onComplete()` signals normal stream completion by invoking the error handler with **`null`** — a client must not treat a null error as failure.
  - A full-context `@SpringBootTest` with `spring-boot-starter-security` on the test classpath must exclude the Boot 4 security auto-configs (`org.springframework.boot.security.autoconfigure.{SecurityAutoConfiguration, UserDetailsServiceAutoConfiguration, web.servlet.ServletWebSecurityAutoConfiguration}`) or every endpoint is 401.
  - The agent card's `supportedInterfaces[0].url` MUST be set for SDK-client discovery+connect; the auto-config default is property-driven (`agentCardUrl`, blank → no interface) so the test overrides `agentCardSupplier` with a lazily-built card reading the `@LocalServerPort` via a static holder.
  - The SDK client needs `a2a-java-sdk-http-client`'s default (JDK) HTTP client — auto-selected by `A2AHttpClientFactory.create()`; no extra dep needed.
  - Checkstyle: no `final` on try-with-resources variables (caught in the new test); pre-existing `InMemoryTaskStoreConcurrencyTest` was broken on Java 17 API (`try(ExecutorService)`) — rewrote with `finally { pool.shutdownNow(); }` and removed an unused import in `InMemoryEventQueueTest` to make `:a2a-core-default:checkstyleTest` green.
  - `Task` builder does not require `history`; `Task.builder().id().contextId().status().build()` is enough for the blocking-result lift.
  - **Pre-existing, out of scope:** `a2a-spring-ai` `checkstyleMain` fails on an unused `reactor.core.publisher.Flux` import in `springai/ChatClientExecutors.java` (from A2A-04). Not touched by A2A-07; a cleanup task or A2A-08 should remove it so `./gradlew check` is fully green.

## A2A-08 — Sample apps: math-agent server + host-agent client

- **Status:** completed
- **Date:** 2026-08-25
- **Verification:** `./gradlew :a2a-server-sample:build :a2a-client-sample:build` PASS
  (server: 4 tests green + checkstyleMain/checkstyleTest); `./gradlew checkstyleMains`
  PASS; `./gradlew unittest` PASS. Live E2E validated: server `bootRun`, curl card
  discovery + raw JSON-RPC, then client `bootRun` logged "Discovered remote agent
  'Math Agent' v0.0.1-SNAPSHOT" and "Remote math agent replied:
  TASK_STATE_COMPLETED \"5\"".
- **What was built:** two self-contained builds under `ai/a2a/`, registered in root
  `settings.gradle`: `a2a-server-sample`
  (`sampleserver/{MathAgentApplication, MathAgentExecutor, AnonymousIdentityConfiguration}`,
  depends on the starter; executor parses `<int> <op> <int>` and answers with a completed
  `TaskStatusUpdateEvent`) and `a2a-client-sample`
  (`sampleclient/{HostAgentApplication, HostAgentRunner}`, depends on core +
  core-default; `CommandLineRunner` doing discover → sendMessage("2 + 3") → log → close,
  errors logged not thrown). Server has `MathAgentExecutorTest` (4 tests, plain JUnit,
  no Spring context).
- **Deviations from task spec (REAL shapes — rely on these):**
  - **Wire payload for the A2A-06 routes** (task doc's curl was wrong twice): route is
    POST `/message/send` (NOT `/message` — 404), role must be the enum name
    `"ROLE_USER"` (`"user"` → InvalidParamsError), and every part needs its wire
    `"kind"` discriminator. Working minimal body:
    `{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":
    "ROLE_USER","parts":[{"kind":"text","text":"2 + 3"}]}}}`. Reply result is a
    TaskStatusUpdateEvent JSON with `status.message.parts[0].text`.
  - Assistant role constant is `Message.Role.ROLE_AGENT` (no ROLE_ASSISTANT — same as
    A2A-04). Reply message copies taskId/contextId from the resolved identity.
  - **Executor id resolution:** `TaskStatusUpdateEvent` ctor hard-requires non-null
    taskId, but when the wire message omits ids the dispatcher keeps them ONLY in the
    store — `context.currentTask()` carries them; incoming() still has nulls. The sample
    resolves ids as currentTask().id/contextId → else incoming values → else fresh UUIDs
    (see MathAgentExecutor.resolveTaskId/resolveContextId). Any agent following the task
    doc literally crashes with IllegalArgumentException on anonymous messages.
  - Server `application.properties` adds
    `io.github.khezyapp.a2a.agent-card-url=http://localhost:8080` (not in the task doc):
    without it the served card has no `supportedInterfaces[0].url` and SDK-client
    discovery+connect fails (A2A-07 gotcha, confirmed live).
  - Server needs an app-side `CallerIdentityResolver` bean: the starter's security-gated
    resolver never materializes without Spring Security, and `A2AWebAutoConfiguration`
    requires one unconditionally — first bootRun failed with
    "Parameter 1 of method agentCardController ... required a bean of type
    CallerIdentityResolver". Sample ships `AnonymousIdentityConfiguration`
    (`CallerIdentity::anonymous`). Consider a starter-level default for security-less
    apps in a future version bump.
  - Client uses Lombok `@Slf4j`: a hand-written `static final Logger log` fails
    Checkstyle ConstantName ('log' not UPPER_SNAKE) — @Slf4j generates the field outside
    source so it passes.
- **Gotchas for next tasks:** root aggregate tasks DO pick these apps up
  (`unittest`/`checkstyleMains` include them — they are not under `/examples/`);
  addressing works from root as `:a2a-server-sample:<task>` / `-p ai/a2a/<app>`. The
  a2a-spring-ai unused-Flux-import checkstyle failure noted by A2A-07 no longer exists —
  checkstyleMains is fully green now. `Message.builder()` tolerates null
  taskId/contextId/extensions/metadata; only `parts` non-empty + messageId are needed
  beyond role.

## A2A-09 — READMEs, full verification, release notes

- **Status:** completed
- **Date:** 2026-08-25
- **Verification:** `./gradlew unittest` PASS; `./gradlew checkstyleMains checkstyleTests`
  PASS; `./gradlew :a2a-core:check :a2a-core-default:check :a2a-spring-ai:check
  :a2a-spring-boot-starter:check` PASS. NOTE: this task file's §4 command form
  `./gradlew :ai:a2a:<module>:check` still does NOT work from root — address included
  builds by name (`:a2a-core:check`) or with `-p ai/a2a/<module>`, same as A2A-04/05/07.
- **What was built:** docs only (no library code touched): umbrella
  `ai/a2a/README.md` (modules table, quick-start executor snippet, NOT-do list,
  who-it's-for); module READMEs `a2a-core/README.md`, `a2a-core-default/README.md`,
  `a2a-spring-ai/README.md`, `a2a-spring-boot-starter/README.md` (bean table, endpoint
  list, full properties table, corrected curl); `ai/a2a/samples/README.md` (how-to-run);
  `RELEASE-NOTES-v1.md` (this directory).
- **Deviations from task spec:**
  - Samples were MOVED after A2A-08: they now live under `ai/a2a/samples/{a2a-server-
    sample,a2a-client-sample}` (root `settings.gradle` updated; their own
    `settings.gradle` already point at `../../../../build-logic`). Run commands are now
    `-p ai/a2a/samples/<app> ...`; root addressing stays `:a2a-server-sample:<task>`.
    Aggregates (`unittest`/`checkstyleMains`) still pick them up — exclusion filter only
    matches `/examples/`.
  - Starter has TWO auto-configurations beyond A2A-05's `A2AAutoConfiguration` that the
    earlier log entries never recorded (added around A2A-07/08):
    `A2AClientAutoConfiguration` (outbound `A2ARemoteAgentClient` bean when
    `io.github.khezyapp.a2a.client.base-url` is set; `bearerTokenInterceptor` when
    `client.auth.type=bearer`; all `ClientCallInterceptor` beans are registered ordered
    on the transport) and `web.A2AWebAutoConfiguration`. `AutoConfiguration.imports`
    lists three FQCNs total. New properties: `client.base-url`, `client.timeout` (30s),
    `client.auth.type` (NONE|BEARER), `client.auth.token`; plus `coredef.client.
    BearerTokenInterceptor` (per-call Authorization header wins over the supplied token).
  - README curl examples use the A2A-08-corrected wire shape (POST `/message/send`,
    `"role":"ROLE_USER"`, parts with `"kind"` discriminator) — the A2A-08 task file §6
    curl is stale on both route and payload.
- **Gotchas for next tasks:** v1 is feature-complete and verified; publishing goes
  through the existing manual `manual_release.yml` workflow (POM metadata set in each
  module `build.gradle`). Natural v1.x candidates: durable task store (Redis/JDBC),
  gRPC transport wiring, default `CallerIdentityResolver` for security-less apps
  (sample ships one app-side today), JSON-RPC `kind` discriminator for strict client
  interop on the A2A-06 routes, upstream PRs (design Phase 5).
