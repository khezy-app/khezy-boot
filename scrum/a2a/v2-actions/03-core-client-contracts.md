# A2A2-03 — `a2a-core`: client contracts

**Prerequisites:** `00`, `01`.
**Design:** §6.1, §6.3.

## 1. Goal

Define the client-side contracts and normalized records over the SDK client. No Spring,
no transport implementation. Package root `io.github.khezyapp.a2a.core.client.*`.

## 2. Files

### 2.1 Model records (`client/`)

- `RemoteAgent(String baseUrl, AgentCard card, Client client)`:
  - `name()`, `skills()`, `selectedInterface()`, `tenant()`.
  - `client()` is the SDK long-lived client; ownership stays with the registry.
- `RemoteSkill(agentName, agentUrl, id, name, description, tags, examples, inputModes,
  outputModes)` + `from(agentName, agentUrl, AgentSkill)`. Defensive list copies.
- `RemoteArtifact(name, description, text)`.
- `TaskRef(contextId, taskId)` with `hasTask()`, `hasContext()`.
- `SendOutcome(agent, skill, TaskState state, contextId, taskId, message,
  List<RemoteArtifact> artifacts, String error)`:
  - `ofTask`, `ofMessage`, `error` factories.
  - `isError`, `isFinal`, `isInterrupted`, `isPending`, `taskRef()`.
- `RemotePartExtractor` — reduce `Part<?>`/`Artifact` to text; handles `TextPart`,
  `DataPart`, `FilePart`, null, unknown.

### 2.2 Request/options (`client/invoke/`)

- `SendOptions` record: `(Duration timeout, Strategy strategy, boolean stopOnInterrupt,
  Map<String,String> headers, Map<String,Object> state)`.
  - `Strategy { BLOCKING, POLL, STREAM }` (only `BLOCKING` implemented in v2.0).
  - `DEFAULT_TIMEOUT = 60s`, `defaults()`, builder, `toBuilder()`.
- `SendRequest` record (mirrors `MessageSendParams` + client conventions):
  `(RemoteAgent agent, List<Part<?>> parts, String skillId, String contextId,
  String taskId, List<String> referenceTaskIds, String tenant,
  MessageSendConfiguration configuration, Map<String,Object> paramsMetadata,
  Map<String,Object> messageMetadata, List<String> extensions, SendOptions options)`.
  - Defensive copies; `builder()`, `toBuilder()`.
  - Builder tracks `hasExplicitTaskRef()` (contextId or taskId non-blank).
  - Do **not** put `agent` in the record if the invoker receives it separately; keep it
    for ergonomics (reference does).

### 2.3 Contracts

- `AgentCardResolver`:
  ```java
  @FunctionalInterface
  Optional<AgentCard> resolve(String baseUrl);   // must never throw
  ```
- `A2AInvoker`:
  ```java
  @FunctionalInterface
  SendOutcome send(SendRequest request);
  ```
- `A2AConversationStore`:
  ```java
  TaskRef ref(String conversationId, String agentName, String skillId);
  void recordInterrupted(...);
  void recordPending(...);
  void recordSettled(String conversationId, String agentName, String skillId, String contextId);
  void clearConversation(String conversationId);
  default void clear() {}
  ```
- `A2AConnector`: concrete (not interface) in `a2a-core`? It depends only on the
  contracts, so put the implementation here to avoid a second module:
  - constructor `(A2AInvoker, A2AConversationStore)`.
  - `invoke(RemoteAgent, List<Part<?>>, String skillId, String conversationId)`.
  - `invoke(RemoteAgent, SendRequest.Builder, String conversationId)`.
  - injects stored ids when the builder has no explicit ref.
  - serializes calls per conversation id using a bounded lock map.
  - updates store: error → no change; interrupted/pending → keep both; settled → keep
    context, drop task id.
- `A2AClientOperations`: thin facade over `RemoteAgent.client()`:
  `getTask`, `listTasks`, `cancelTask`, push-config CRUD, `getExtendedAgentCard`.
  SDK errors propagate unchanged.
- `ClientBuildOptions` (in this module, used by task 04) includes
  `A2AHttpClient httpClient` and `Map<String,Object> transportParameters` so the starter
  can share one HTTP client and activate SDK transport wrappers (OpenTelemetry).
- `A2ACardScope` (reserved for v2.1) — optional in this task:
  `key()`, `extended()`, `callContext()`. If added, document public-only default and do
  not wire scoping into the registry yet.

### 2.4 Contracts that do not belong here

- No `A2ARemoteAgentClient`/`A2AStreamingRemoteAgentClient` (v1 ports) — deleted.
- No custom error hierarchy. `SendOutcome.error` is the client-facing normalization;
  `A2AClientOperations` lets SDK exceptions propagate.

## 3. Tests

- `SendOutcomeTest` — factories and `isFinal/isInterrupted/isPending/taskRef`.
- `SendRequestTest` — defensive copies, `hasExplicitTaskRef`, builder round-trip.
- `SendOptionsTest` — defaults and defensive copies.
- `RemotePartExtractorTest` — text/data/file/null.
- `A2AConnectorTest` — with a stub invoker + in-memory fake store:
  - new conversation sends no ids;
  - stored interrupted ids injected;
  - explicit ids win;
  - interrupted result keeps both ids;
  - pending keeps both;
  - settled keeps context, drops task;
  - error leaves store unchanged;
  - same conversation calls are serialized (two threads, ordered stub).

## 4. Verification

```bash
./gradlew :a2a-core:check
```

## 5. Handoff

Record exact record component sets and any SDK `MessageSendConfiguration` builder
surprises. Tasks 04/06/07 depend on these shapes. `graphify update .`.
