# Khezy A2A Store JPA

JPA-backed `TaskStore`, `TaskStateProvider`, `PushNotificationConfigStore`,
`A2AConversationStore` and task-scope lookup for A2A — with task payloads encrypted at
rest by `khezy-crypto` (AES-256-GCM).

`io.github.khezyapp:a2a-store-jpa:1.0.0` · package root
`io.github.khezyapp.a2a.store.jpa`.

## Activation

Add the module and provide a `StringEncryptor` bean:

```groovy
implementation "io.github.khezyapp:a2a-store-jpa:1.0.0"
implementation "io.github.khezyapp:crypto-spring-boot-starter:1.0.0"
runtimeOnly "com.h2database:h2"
```

The auto-configuration is **fail-closed**: without a `StringEncryptor` it registers no
stores and no store repositories, so nothing is ever persisted as plaintext. The starter
then falls back to the SDK in-memory stores.

Entity and repository scanning are additive (the module appends its package through
`EntityScanPackages`); it does not replace your application's entities.

## Beans

| Bean | Type(s) | Notes |
|---|---|---|
| `springA2ATaskStore` | `SpringA2ATaskStore` (`TaskStore`, `TaskStateProvider`) | Keyset listing, scope filter, 15s grace period, finalized event. |
| `jpaTaskScopeLocator` | `JpaTaskScopeLocator` (`TaskScopeLocator`) | Denormalized `agent_scope` lookup. |
| `springA2APushNotificationConfigStore` | `SpringA2APushNotificationConfigStore` (`PushNotificationConfigStore`) | SDK 1.4 omitted-id create-only, per-task limit, pagination. |
| `springA2AConversationStore` | `SpringA2AConversationStore` (`A2AConversationStore`) | Multi-node multi-turn resumption; replaces the client's in-memory store. |

All are `@ConditionalOnMissingBean`.

## Properties (`io.github.khezyapp.a2a.store`)

| Property | Default | Meaning |
|---|---|---|
| `type` | `JPA` | `MEMORY` or `JPA`; defaults to JPA because the module only activates when JPA is present. |
| `task.grace-period` | `15s` | How long a terminal task is retained before deletion eligibility. |
| `push.max-per-task` | SDK default (`100`) | Maximum push configs per task; non-positive falls back to the SDK default. |
| `conversation.ttl` | `1h` | Idle retention for conversation entries; expired entries are ignored and can be purged. |

Encryption keys are configured under `io.github.khezyapp.crypto.*` (for example
`active-key-id` and `keys.<id>`); see the
[crypto starter](../../../auto-configurations/security/crypto-spring-boot-starter/).

## Encryption

Payloads are serialized with the SDK `JsonUtil`, then encrypted and prefixed with
`kzy:v1`. AAD is fixed and per row:

- task: purpose `a2a-task`, AAD `a2a:task:<taskId>`;
- push config: purpose `a2a-push-config`, AAD `a2a:push:<taskId>:<configId>`.

Legacy plaintext rows (no `kzy:` prefix) are returned unchanged. Rotation re-encrypts one
row at a time with that row's context; never batch task rows under a single context.

Conversation entries are **not** encrypted: they hold only A2A routing identifiers
(conversation id, agent, skill, `context_id`, `task_id`), which already travel on the
wire. No message payload is stored there.

## Gotchas

- A task is only visible to a scoped caller when its metadata carries
  `agent_scope`; the consumer executor must seed it. Without it,
  `TenantTaskAuthorizationProvider` denies reads.
- `list` reads the scope from `ServerCallContext.state` (`A2AContextKeys.AGENT_SCOPE`):
  a missing context means unscoped/internal, a context without the key is fail-closed.
- `A2ATaskFinalizedEvent` is a plain record, not a Spring `ApplicationEvent`; consume it
  with `@EventListener`, not `ApplicationListener`.
- `@DataJpaTest` does not import this auto-configuration automatically; import
  `A2AJpaStoreAutoConfiguration` (and a `StringEncryptor`) or use `@SpringBootTest`.
- The default push-config protocol version follows the SDK
  (`AgentInterface.CURRENT_PROTOCOL_VERSION`, currently `1.0`).
- The client auto-configuration in `a2a-spring-boot-starter` runs after this configuration,
  so `springA2AConversationStore` replaces the in-memory conversation store automatically
  when both modules are present.

## What this does NOT do

- No key storage, rotation policy or envelope format of its own — that is `khezy-crypto`.
- No cross-store transaction manager; it uses your application's JPA setup.
- No plaintext fallback mode, by design.
