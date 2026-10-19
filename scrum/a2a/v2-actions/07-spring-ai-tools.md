# A2A2-07 — `a2a-spring-ai`: client tooling for LLMs

**Prerequisites:** `00`, `03`, `04`.
**Design:** §8.
**Reference:** `/mnt/data/refs-proj/kflowllmlap/a2a-spring-ai` and
`a2a-spring-ai-spring-boot-starter` (port, simplify).

## 1. Goal

Expose discovered remote A2A agents/skills as Spring AI tools so a host LLM can delegate
work. This module is **client-side only** — it does not adapt a `ChatClient` into a
server `AgentExecutor` (deleted v1 behavior).

Package root `io.github.khezyapp.a2a.springai.*`.

## 2. Files

### 2.1 `A2AToolContextKeys`

`CONVERSATION_ID = "conversationId"`. The host passes it via Spring AI `ToolContext`;
A2A ids never reach the model.

### 2.2 `SkillInput`

`record SkillInput(String task)` — free-text task description; no ids.

### 2.3 `A2AResultFormatter`

- `format(SendOutcome)` → compact JSON string with `agent`, `skill`, `state`, `message`,
  `error`, `artifacts[]` (name/description/text).
- Omit `contextId`/`taskId`.
- Escape JSON; truncate to `maxChars` (default 8000).
- No external JSON library needed; keep the reference's manual builder (deterministic,
  no Jackson coupling).

### 2.4 `A2ASkillToolFactory`

- Constructor `(RemoteAgentRegistry, A2AConnector, A2AResultFormatter)`.
- `toolCallbacks()` — one `ToolCallback` per discovered skill, cached once.
- `toolCallbacks(List<RemoteSkill>)` — explicit list, no cache.
- Tool name: `sanitize(agentName) + "__" + sanitize(skillId)`, max 64 chars, unique
  suffix on collision.
- Description from card: agent, skill description, tags, examples, instruction to
  restate new information when continuing.
- Input schema: `{ "task": string }` (required, no additional properties).
- Invoke: `connector.invoke(agent, List.of(new TextPart(task)), skill.id(),
  conversationId(toolContext))` → format outcome. Unknown agent → structured error.

### 2.5 `A2AAgentTools`

- `sendToAgent(agentName, task, ToolContext)` — coarse delegation, resolves by name.
- `listRemoteAgents()` — names, URLs, descriptions, skills.
- Same conversation-id handling and formatter.

### 2.6 `A2APromptProvider` / `A2APromptBuilder`

- Functional `build(List<RemoteSkill>)`.
- Default builder: instructions (route to most specific tool, restate new information,
  call listRemoteAgents when unsure, do not invent results), skills header, per-skill
  line, empty prompt.
- Configurable instructions/header/template/empty-prompt via builder.

### 2.7 Spring AI auto-config (in the starter, not this module)

Add to `a2a-spring-boot-starter` (task 06 already created the starter; this is an
incremental addition):

- `A2A ToolingAutoConfiguration` `@ConditionalOnClass(ChatClient.class)` +
  `@ConditionalOnProperty(io.github.khezyapp.a2a.client.enabled)`.
- Beans: `A2AResultFormatter`, `A2ASkillToolFactory`, `A2AAgentTools`,
  `A2APromptProvider` (all `@ConditionalOnMissingBean`).
- **Do not** attach tools to every `ChatClient` by default. Optional customizer only when
  `io.github.khezyapp.a2a.client.attach-tools-to-chat-client=true`.
- Add the FQCN to `AutoConfiguration.imports` in the starter (the Spring AI classes are
  optional; the config class itself must be in the starter so it can be conditional).

## 3. Tests

- `A2AResultFormatterTest` — fields, artifacts, escaping, truncation, ids absent.
- `A2ASkillToolFactoryTest` — one tool per skill, unique names, description contains
  agent/skill, invoke delegates to connector and formats, unknown agent error.
- `A2AAgentToolsTest` — send/list behavior.
- `A2APromptBuilderTest` — default prompt, custom instructions, empty skills.
- `A2AToolingAutoConfigurationTest` — reflection checks: `@ConditionalOnMissingBean`,
  `@ConditionalOnClass(ChatClient.class)`.

Use a stub `A2AConnector`/registry where possible (Mockito allowed).

## 4. Verification

```bash
./gradlew :a2a-spring-ai:check
./gradlew :a2a-spring-boot-starter:check
```

## 5. Handoff

Record the Spring AI 2.0.1 tool API used (`ToolCallback`, `FunctionToolCallback`,
`ToolContext`, `@Tool`), and whether the optional auto-config breaks contexts without
Spring AI (condition must prevent that). `graphify update .`.
