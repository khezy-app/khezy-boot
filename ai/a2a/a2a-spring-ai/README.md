# Khezy A2A Spring AI

Client-side tooling that exposes discovered remote A2A agents and skills to a Spring AI
model. It sits **on top of** Spring AI and `a2a-core` — you still build and configure the
`ChatClient` (model, memory, advisors); this module only turns agent cards into tools and
turns tool calls into A2A requests.

**Prerequisite:** this module assumes you already know Spring AI's `ToolCallback`,
`ToolContext` and `ChatClient` (`spring-ai-client-chat:2.0.1`). If tool calling is new to
you, read the [Spring AI tool calling reference](https://docs.spring.io/spring-ai/reference/)
first.

`io.github.khezyapp:a2a-spring-ai:1.0.0`

## What is inside

| Type | Purpose |
|---|---|
| `A2ASkillToolFactory` | One `ToolCallback` per discovered skill (name/description from the card) |
| `A2AAgentTools` | `sendToAgent` + `listRemoteAgents` — coarse delegation and discovery |
| `A2AResultFormatter` | Compact JSON result for the model, A2A ids omitted |
| `A2APromptProvider` / `A2APromptBuilder` | Routing system prompt built from the skills |
| `A2AToolContextKeys` | `conversationId` key the host passes via `ToolContext` |
| `SkillInput` | The `{ "task": "..." }` tool argument |

## Usage

```java
final var registry = ...;   // RemoteAgentRegistry from a2a-core-default
final var connector = ...;  // A2AConnector (resumes tasks per conversation)
final var formatter = new A2AResultFormatter();
final var skills = new A2ASkillToolFactory(registry, connector, formatter);
final var agents = new A2AAgentTools(registry, connector, formatter);
final var prompt = new A2APromptBuilder().build(registry.skills());

chatClient.prompt()
        .system(prompt)
        .user(question)
        .toolCallbacks(skills.toolCallbacks())
        .tools(agents)
        .toolContext(Map.of(A2AToolContextKeys.CONVERSATION_ID, chatSessionId))
        .call();
```

The conversation id is host state, not model input: it lets the connector resume an
`INPUT_REQUIRED` task without exposing `contextId`/`taskId` to the LLM. A call without it
returns a structured error instead of silently sharing a conversation.

When using the Spring Boot starter, all of the above are beans; see
[`a2a-spring-boot-starter`](../a2a-spring-boot-starter/).

## What this does NOT do

- It does not adapt a `ChatClient` into a server `AgentExecutor`. Server execution is your
  business logic — implement `onMessage` and call your own `ChatClient` there.
- It does not create model clients, memory, or advisors. You own `ChatClient` construction.
- It does not expose A2A ids to the model, and it does not pick a conversation id for you.
- Tool descriptions come from the agent card; per-skill JSON input schemas are not modeled
  (every tool takes a single `task` string).
