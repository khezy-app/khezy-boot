# KHEZY Crypto v1 — Handoff Log

> Context is handed off **in this file, never in chat**. Append one entry per completed
> task. Do not edit earlier entries.

## CRYPTO-01 — Module skeleton and root registration

- **Status:** completed
- **Date:** 2026-10-19
- **Verification:** `./gradlew :khezy-crypto:compileJava :crypto-spring-boot-starter:compileJava` PASS (NO-SOURCE, expected).
- **What was built:** `security/crypto/khezy-crypto` (`khezy.java-library`,
  `io.github.khezyapp:khezy-crypto:1.0.0`) and
  `auto-configurations/security/crypto-spring-boot-starter` (`khezy.springboot-library`,
  `io.github.khezyapp:khezy-crypto-spring-boot-starter:1.0.0`), each with isolated
  `settings.gradle`; both registered in root `settings.gradle`; README stubs.
- **Deviations from task spec:** none. No placeholder classes were created; source roots
  are empty until CRYPTO-02.
- **Gotchas for next tasks:** the starter already declares `api` on
  `io.github.khezyapp:khezy-crypto:1.0.0`; composite substitution resolves it. Build from
  root with `:khezy-crypto:<task>` / `:crypto-spring-boot-starter:<task>`.

## CRYPTO-02 — Contract API, records, exceptions, codec interface

- **Status:** completed
- **Date:** 2026-10-19
- **Verification:** `./gradlew :khezy-crypto:check` PASS (main + test + checkstyle).
- **What was built:** `io.github.khezyapp.crypto.contract` — `Ciphertext` (immutable,
  defensive byte copies, no payload in `toString`), `CipherContext`, `Encryptor`,
  `StringEncryptor`, `CipherTextCodec`, `KeyStatus`, `KeyUsage`, `KeyMetadata`,
  `KeyHandle`, `KeyProvider`, `KeyManager`, `KeyCreateRequest(+Builder)`,
  `KeyTransitionContext`, `KeyDestroyRequest`, `CryptoBackend`, `EncryptionCounter`.
  `io.github.khezyapp.crypto.exception` — `CryptoException` + 9 subclasses.
- **Deviations from task spec:** `Encryptor` also exposes `activeKeyId()` (needed by the
  re-encryptor). The default codec implementation is deferred to CRYPTO-03 as planned.
- **Gotchas for next tasks:** `Ciphertext.keyId` validates `[A-Za-z0-9_.-]{1,64}`;
  `CipherContext.purpose` validates `[A-Za-z0-9_.:-]{1,64}`. Tests use JUnit assertions
  only — the pure `khezy.java-library` plugin does not provide AssertJ.

## CRYPTO-03 — AES-GCM default implementation

- **Status:** completed
- **Date:** 2026-10-19
- **Verification:** `./gradlew :khezy-crypto:check` PASS (NIST vector + round-trip + tamper + codec + provider tests, checkstyle clean).
- **What was built:** `io.github.khezyapp.crypto.defaults` — `AesGcmBackend` (AES-256-GCM,
  fixed 12-byte IV/16-byte tag, AAD), `IvGenerator`/`SecureRandomIvGenerator`,
  `KhezyCipherTextCodec` (`kzy:v1:...`), `DefaultEncryptor`, `DefaultStringEncryptor`,
  `InMemoryEncryptionCounter`, `AesKeyGenerator`, `InMemoryKeyProvider` (KeyManager),
  `PropertiesKeyProvider`, `ReadOnlyKeyManager`.
- **Deviations from task spec:** `KeyTransitionContext` gained an optional `destroyAfter`
  component (needed so retirement can record the retention deadline); existing 3-arg
  constructor preserved. The NIST AES-256-GCM zero-key/zero-IV vector is used.
- **Gotchas for next tasks:** `AesGcmBackend` requires a 32-byte key; only `AES-256-GCM`
  is supported in v1.0. `PropertiesKeyProvider` is read-only and logs a JUL warning.
  `InMemoryKeyProvider` is synchronized for lifecycle operations.

## CRYPTO-04 — Key lifecycle, rotation strategies, re-encryption

- **Status:** completed
- **Date:** 2026-10-19
- **Verification:** `./gradlew :khezy-crypto:check` PASS (gradual/compromise rotation, audit, re-encryption, policy, batch executor; checkstyle clean).
- **What was built:** `io.github.khezyapp.crypto.rotation` — `RotationMode`,
  `RotationReason`, `RotationRequest(+Builder)`, `RotationResult`, `RotationContext`,
  `RotationPolicy`, `TimeBasedRotationPolicy`, `UsageBasedRotationPolicy`,
  `CompositeRotationPolicy`, `AlwaysRotatePolicy`, `KeyEvent`, `AuditListener`,
  `KeyRotationService`, `DefaultKeyRotationService`, `ReEncryptor`,
  `DefaultReEncryptor`, `ReEncryptionSource/Record/Sink/Report/Executor`.
- **Deviations from task spec:** `KeyManager` gained `swapActive(newKeyId,
  previousStatus, context)` so activation of the new key and retirement of the old key
  are one atomic provider operation. `KeyTransitionContext` carries `destroyAfter`.
- **Gotchas for next tasks:** `DefaultKeyRotationService` requires an `Encryptor` and
  `CipherTextCodec`; `reEncryptor(oldKeyId)` only accepts a key usable for decryption.
  `RotationRequest` defaults: GRADUAL / MANUAL / actor `system` / 90-day retention.

## CRYPTO-05 — Spring Boot starter

- **Status:** completed
- **Date:** 2026-10-19
- **Verification:** `./gradlew :crypto-spring-boot-starter:check` PASS (properties, auto-config context, health, scheduler, audit, checkstyle).
- **What was built:** `io.github.khezyapp.crypto.spring` — `CryptoProperties`,
  `CryptoAutoConfiguration`, `CryptoHealthIndicator`, `CryptoRotationScheduler`,
  `CryptoAuditEventPublisher`, `CryptoKeyEvent`; auto-config imports file.
- **Deviations from task spec:** Spring Boot 4 moved health types to
  `org.springframework.boot.health.contributor` (not `org.springframework.boot.actuate.health`).
  `rotation.auto` + read-only properties provider fails at startup by design.
- **Gotchas for next tasks:** actuator is `compileOnly` in the starter; the health nested
  config is guarded by `@ConditionalOnClass(HealthIndicator.class)`. The scheduled method
  uses `fixedDelayString` with the ISO-8601 `check-interval` property.

## CRYPTO-06 — Ecosystem integration and release

- **Status:** completed (with `a2a-store-jpa` deferred)
- **Date:** 2026-10-19
- **Verification:** `./gradlew :api-security:check` PASS; docs and release notes written.
- **What was built:** `api-security` now depends on `khezy-crypto`; `CustomEncryptor` is a
  deprecated dual-format adapter (new `kzy:v1:` + legacy `formatterId.version.cipherText`);
  `CustomEncryptors.createEncryptor(StringEncryptor)` added; `DefaultEncryptorRegistry`
  cache bug fixed; migration tests added. Full READMEs + `RELEASE-NOTES-v1.md`.
- **Deviations from task spec:** `a2a-store-jpa` integration intentionally skipped — the
  module does not exist yet (A2A task `A2A2-05`).
- **Gotchas for next tasks:** `CustomEncryptor` keeps the legacy path only when a
  `StringEncryptor` is absent or the value is not `kzy:`-prefixed. `ENVELOPE_REWRAP` is
  reserved; `DefaultKeyRotationService` throws for it.

---

## Entry template

```markdown
## CRYPTO-XX — <task title>

- **Status:** completed | partial (explain)
- **Date:** YYYY-MM-DD
- **Verification:** <commands run + result>
- **What was built:** <files/packages, decisions>
- **Deviations from task spec:** <renamed types, real signatures, surprises>
- **Gotchas for next tasks:** <constraints, test/flakiness/checkstyle notes>
```
