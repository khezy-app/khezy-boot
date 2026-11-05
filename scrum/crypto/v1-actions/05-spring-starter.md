# CRYPTO-05 — Spring Boot starter

**Prerequisites:** `00`, `03`, `04`.
**Design:** §7.

## 1. Goal

Make the library one dependency in a Spring Boot app: bind properties, create beans, warn
about unsafe key sources, expose health/events, and optionally schedule rotation safely.

Package `io.github.khezyapp.crypto.spring`. No crypto logic here — only wiring.

## 2. Properties

`@ConfigurationProperties(prefix = "io.github.khezyapp.crypto")` → `CryptoProperties`:

| Property | Default | Notes |
|---|---|---|
| `enabled` | `true` | Master switch |
| `algorithm` | `AES-256-GCM` | Must be supported by the backend registry |
| `active-key-id` | null | Required for `properties` provider |
| `keys` | empty | `Map<String,String>` Base64url 32-byte keys (dev/test only) |
| `key-provider` | `properties` | `properties`, `in-memory`, `jdbc`, `custom` |
| `rotation.mode` | `GRADUAL` | |
| `rotation.cryptoperiod` | `P365D` | Warn if > `P730D` |
| `rotation.usage-limit` | `4294967296` | AES-GCM 2^32 |
| `rotation.usage-threshold` | `0.8` | |
| `rotation.retain-old-for` | `P90D` | |
| `rotation.auto` | `false` | |
| `rotation.check-interval` | `PT1H` | |
| `rotation.batch-size` | `500` | |
| `aad.enabled` | `true` | If true, `CipherContext.aad()` must be non-null for app code (enforced by convention; library allows null) |

Validate:

- key ids `[A-Za-z0-9_.-]{1,64}`.
- key values decode to exactly 32 bytes; reject 128-bit by default.
- `usage-threshold` in `(0,1]`.
- `cryptoperiod` positive; warn > 2 years.

## 3. Auto-configuration

`CryptoAutoConfiguration` (`@AutoConfiguration` + `@EnableConfigurationProperties`):

Beans, all `@ConditionalOnMissingBean`:

1. `KeyProvider` / `KeyManager`:
   - `properties` → `PropertiesKeyProvider` wrapped in `ReadOnlyKeyManager` (rotation
     disabled; `rotate` fails with a clear message). If `rotation.auto=true` with a
     read-only provider, fail at startup rather than scheduling a no-op rotation.
   - `in-memory` → `InMemoryKeyProvider` (dev/test).
   - `custom`/`jdbc` → expect a user bean; fail fast if missing.
2. `CryptoBackend` → `AesGcmBackend` with an optional `Provider` bean.
3. `EncryptionCounter` → `InMemoryEncryptionCounter` with a startup warning in
   non-dev/test profiles.
4. `Encryptor` → `DefaultEncryptor`.
5. `CipherTextCodec` → `KhezyCipherTextCodec`.
6. `StringEncryptor` → `DefaultStringEncryptor`.
7. `KeyRotationService` → `DefaultKeyRotationService` with `RotationPolicy` composite
   (time + usage).
8. `ReEncryptor` → `DefaultReEncryptor`.
9. `RotationLock` → `NoopRotationLock` (single instance) by default; a user bean
   overrides for DB/advisory locking.
10. `CryptoHealthIndicator` (`@ConditionalOnClass(HealthIndicator.class)`).
11. `CryptoAuditEventPublisher` — converts `AuditListener` events to Spring
    `ApplicationEvent`s.
12. `CryptoRotationScheduler` — enabled only when `rotation.auto=true`:
    - `@Scheduled(fixedDelayString = "${...check-interval}")`.
    - acquire `RotationLock`; if not acquired, skip.
    - evaluate policy; rotate with `rotation.mode`.

Register the auto-config in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
Never use `@Import` for conditionally-gated classes.

## 4. Safety behavior

- If `key-provider=properties` and the active Spring profiles do not contain `dev` or
  `test`, log a **WARN** (or fail if `crypto.fail-on-insecure-key-source=true`, default
  false).
- If the active key status is `COMPROMISED` or missing → startup fails (fail closed).
- Health indicator:
  - `UP` when an active key exists, is not compromised, and usage < threshold.
  - `OUT_OF_SERVICE`/`DOWN` when missing/compromised/over limit.
  - details: key id, status, age, usage count/limit, algorithm — **no key material**.
- Actuator endpoint (optional, guarded by `management.endpoints...`): metadata only.

## 5. Tests

- `CryptoPropertiesTest` — defaults, validation, key length rejection.
- `CryptoAutoConfigurationTest` — reflection/annotation checks:
  - `@AutoConfiguration`, `@EnableConfigurationProperties`;
  - every bean method `@ConditionalOnMissingBean`;
  - imports file contains the FQCN.
- `CryptoContextTest` (`ApplicationContextRunner` with a minimal context):
  - property keys create `StringEncryptor`; round-trip works;
  - missing active key fails;
  - `rotation.auto=false` creates no scheduler;
  - a user `KeyProvider` bean overrides the default.
- `CryptoHealthIndicatorTest` — UP/DOWN cases; no key in details.
- `CryptoRotationSchedulerTest` — fake `RotationLock`; rotates once per lease; skips when
  locked.
- `CryptoAuditEventPublisherTest` — Spring events emitted; no key material.

## 6. Verification

```bash
./gradlew :crypto-spring-boot-starter:check
```

## 7. Handoff

Record bean names, property binding shape (especially `keys` map with dots), and the
exact scheduler/lock behavior. Task 06 integrates modules and documents. `graphify update .`.
