# A2A2-05 — `a2a-store-jpa`: persistence + encryption at rest

**Prerequisites:** `00`, `02`.
**Design:** §7.
**Reference:** `/mnt/data/refs-proj/kflowllmlap/a2a-spring-store` (same SDK interfaces,
plus the `ConfigCipher` idea).

## 1. Goal

Implement the SDK persistence interfaces with Spring Data JPA and add the KHEZY
differentiator: **pluggable encryption of stored payloads**. Defaults remain in-memory
when this module is absent.

Package root `io.github.khezyapp.a2a.store.jpa.*`.

## 2. Schema (SDK extras-compatible)

### `a2a_tasks`

| Column | Type | Encrypted |
|---|---|---|
| `task_id` (PK) | varchar | no |
| `context_id` | varchar | no |
| `state` | varchar | no |
| `status_timestamp` | timestamp | no |
| `agent_scope` | varchar, indexed | no |
| `task_data` | text | **yes** (full `Task` JSON) |
| `finalized_at` | timestamp | no |

### `a2a_push_notification_configs`

| Column | Type | Encrypted |
|---|---|---|
| `task_id` (PK part) | varchar | no |
| `config_id` (PK part) | varchar | no |
| `task_data` | text | **yes** (full config JSON; contains token/credentials) |
| `protocol_version` | varchar | no |
| `created_at` | timestamp | no |

Serialization uses `org.a2aproject.sdk.jsonrpc.common.json.JsonUtil` so Part/Message
discriminators match the wire format. Do not use Jackson for these payloads.

## 3. Files

### 3.1 Crypto — use `khezy-crypto`, do not implement here

Add `api "io.github.khezyapp:khezy-crypto:1.0.0"` (see
`scrum/crypto/crypto-library-design.md`). The A2A store does **not** define its own AES
code; it consumes the shared contract:

- `io.github.khezyapp.crypto.contract.StringEncryptor` — encrypt/decrypt payloads.
- `CipherContext` with AAD bound to the row identity:
  - task: `CipherContext.of("a2a-task", "a2a:task:" + taskId)`
  - push config: `CipherContext.of("a2a-push-config", "a2a:push:" + taskId + ":" + configId)`
- Keep a thin `A2ACipher` adapter **only** for source compatibility:
  ```java
  public interface A2ACipher {
      String encrypt(String plaintext, String aad);
      String decrypt(String ciphertext, String aad);
  }
  ```
  `StringEncryptorA2ACipher` delegates to `StringEncryptor`; no crypto logic.
- No `A2AKeyring`, `AesGcmA2ACipher`, `NoopA2ACipher`, or envelope classes here.
- Key management/rotation lives in `khezy-crypto`; the store only binds context/AAD and
  exposes a re-encryption path (see §6).

### 3.2 Task store (`task/`)

- `A2ATaskEntity` — entity + `fromTask`, `updateFrom`, `toTask`:
  - the entity stores ciphertext strings only; the **store** performs
    `StringEncryptor.encrypt/decrypt` around `JsonUtil` using the row AAD.
  - entities never hold Spring beans or the encryptor.
  - denormalizes contextId/state/statusTimestamp/finalizedAt/agentScope.
  - preserves existing `agentScope` when incoming task has no scope metadata.
- `A2ATaskRepository extends JpaRepository<A2ATaskEntity, String>, JpaSpecificationExecutor`:
  - `@Query("select e.agentScope from A2ATaskEntity e where e.id = :taskId")`
    `Optional<String> findAgentScopeByTaskId(String taskId)`.
- `SpringA2ATaskStore implements TaskStore, TaskStateProvider`:
  - constructor `(A2ATaskRepository, StringEncryptor)`.
  - `save(Task, boolean)` — upsert; serialize with `JsonUtil`, encrypt with the
    `a2a:task:<taskId>` AAD.
  - `get`, `delete` — decrypt with the same AAD.
  - `isTaskActive` / `isTaskFinalized` with 15s grace period.
  - `list(ListTasksParams, ServerCallContext)` — keyset pagination on
    `(statusTimestamp desc, id asc)`, filters contextId/state/statusTimestampAfter,
    scope from call-context state (`AGENT_SCOPE`), history/artifact trimming.
  - translate serialization/persistence failures to SDK
    `TaskSerializationException`/`TaskPersistenceException`.
- `JpaTaskScopeLocator implements TaskScopeLocator` (single scalar query).
- Publish `A2ATaskFinalizedEvent(taskId, task)` through `ApplicationEventPublisher` after
  saving a locally-created task in a final state (not for replicated saves). Mirrors the
  SDK extras `TaskFinalizedEvent`; consumers can listen for cleanup/metrics.

### 3.3 Push config store (`push/`)

- `A2APushConfigId` `@EmbeddedId` (never null; resolve omitted ids before constructing).
- `A2APushNotificationConfigEntity` `(taskId, configId)` + `task_data` ciphertext;
  `configId` is always the resolved (non-null) value.
- `A2APushNotificationConfigRepository`.
- `SpringA2APushNotificationConfigStore implements PushNotificationConfigStore`:
  - constructor `(A2APushNotificationConfigRepository, StringEncryptor)`.
  - **SDK 1.4 semantics:** `TaskPushNotificationConfig.id` is nullable. When omitted,
    resolve the default id to `taskId`; this is **create-only** — if the default config
    already exists, throw `InvalidParamsError` instead of replacing it. Updates must
    supply the id explicitly.
  - per-task limit from `PushNotificationConfigStore.maxPushConfigsPerTask(null)`;
  - pagination via SDK `PageToken`;
  - serialize with `JsonUtil`, encrypt with the
    `a2a:push:<taskId>:<configId>` AAD.

### 3.4 Auto-configuration (`A2AJpaStoreAutoConfiguration`)

- `@AutoConfiguration(after = HibernateJpaAutoConfiguration.class,
  DataJpaRepositoriesAutoConfiguration.class)`.
- `@ConditionalOnClass({EntityManager.class, JpaRepository.class})`.
- Additive entity scan: `BeanFactoryPostProcessor` appends
  `io.github.khezyapp.a2a.store.jpa` to `EntityScanPackages` (do not replace the app's
  packages).
- `@EnableJpaRepositories(basePackages = "...store.jpa")`.
- Beans (all `@ConditionalOnMissingBean`):
  - `StringEncryptor` comes from `khezy-crypto` (its own starter/auto-config); this module
    consumes it. If no `StringEncryptor` bean exists, the store auto-config is
    `@ConditionalOnBean(StringEncryptor.class)` or fails with a clear message — do not
    silently store plaintext.
  - `SpringA2ATaskStore`, `JpaTaskScopeLocator`,
    `SpringA2APushNotificationConfigStore`.
- Register in
  `a2a-store-jpa/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- **Important:** the starter's server auto-config must prefer these beans when present
  (`@ConditionalOnMissingBean` ordering); task 06 handles that.

### 3.5 Properties (`A2AStoreProperties`)

`@ConfigurationProperties(prefix = "io.github.khezyapp.a2a.store")`:
`type`, `task.grace-period`, `push.max-per-task`. Encryption keys/rotation belong to
`khezy-crypto` (`io.github.khezyapp.crypto.*`), not to the A2A store. Document that the
A2A store is fail-closed when no `StringEncryptor` bean is present.

## 4. Tests (`src/test/java`, H2)

Encryption primitives are already covered by `khezy-crypto`; this module tests the
integration.

- `A2ASpringJpaStoreTest` (`@DataJpaTest` or `@SpringBootTest` with H2):
  - task save/get/update/list with pagination + scope filtering;
  - `isTaskActive` grace period, `isTaskFinalized`;
  - push config CRUD + pagination + per-task limit;
  - omitted id creates the default; a second omitted-id create is rejected; explicit-id
    update succeeds;
  - `A2ATaskFinalizedEvent` fires once on final save and not for replicated saves;
  - entity/repository coexistence with an app entity (regression for additive scan).
- `A2AEncryptionAtRestTest`:
  - save a task containing a secret in a TextPart;
  - query `task_data` column directly and assert the secret is absent and the
    `kzy:v1:` prefix present;
  - read through the store returns the original;
  - decrypting a task row with the wrong AAD (simulating row swap) fails.
- `A2ACipherAdapterTest` — `StringEncryptorA2ACipher` delegates and preserves AAD.
- `A2ARotationMigrationTest`:
  - save with key K1; inject K2 as active; old row still reads;
  - run `ReEncryptionExecutor` over `a2a_tasks.task_data`; row now uses K2;
  - task and push-config AADs remain stable during migration.

## 5. Verification

```bash
./gradlew :a2a-store-jpa:check
./gradlew :khezy-crypto:check
```

## 6. Handoff

Record the exact `StringEncryptor` bean expectations, AAD strings, and whether
`@DataJpaTest` picks up the additive entity scan. Task 06 depends on the auto-config FQCN
and bean names. `graphify update .`.
