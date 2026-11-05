# KHEZY Crypto — Utility Library Design

> A small, opinionated **application-level encryption utility** for sensitive data at
> rest. It provides a stable contract, a safe AES-GCM default, versioned ciphertext, and
> first-class **key rotation** so every KHEZY library (and any consumer project) handles
> cryptography the same way.
>
> This is **not** a KMS, HSM, TLS stack, or password hashing library. It is the thin
> application layer that sits between your data and a key source (properties, database,
> Vault, cloud KMS, HSM).

Status: design draft v1
Date: 2026-10-19
Target: Java 17, no mandatory Spring dependency in core
Package: `io.github.khezyapp.crypto`
Artifacts: `io.github.khezyapp:khezy-crypto`, `io.github.khezyapp:khezy-crypto-spring-boot-starter`

---

## 1. OWASP / NIST research summary

Sources (checked 2026-10-19):

- OWASP **Cryptographic Storage Cheat Sheet**
- OWASP **Key Management Cheat Sheet**
- OWASP **Secrets Management Cheat Sheet**
- OWASP **Top 10:2025 A04 — Cryptographic Failures**
- NIST SP 800-57 Part 1 Rev. 5 (key management / cryptoperiods)
- NIST SP 800-38D (GCM / IV / usage limits)
- OWASP ASVS 5.0 V13.1.4, V13.3.1–13.3.4

### 1.1 What the guidance requires

| Requirement | Consequence for the library |
|---|---|
| Use AES with ≥128-bit (ideally 256-bit) keys and an authenticated mode (GCM/CCM first). | Default `AES-256-GCM`; no CBC/ECB default; no custom algorithms. |
| Keys must be generated with a CSPRNG, not from words/passphrases. | `KeyGenerator` uses `SecureRandom`; key material is `byte[]`/`SecretKey`, never derived from text by default. |
| Rotate keys on compromise, cryptoperiod expiry, algorithm change, and usage limits. | Rotation is a first-class API with time/usage/manual/compromise triggers. |
| Two ways to handle old data: (a) re-encrypt, (b) tag ciphertext with the key ID and keep old keys. | Envelope carries `keyId`; default strategy is **gradual** (b) with an optional **eager** (a) re-encryption job. |
| Old keys must be retained long enough for old backups, then destroyed/zeroized. | `DECRYPT_ONLY` state, `retainFor` retention window, guarded destruction, audit. |
| AES-GCM with random 96-bit IV: at most 2^32 encryptions per key across all instances. | Per-key usage counter + configurable limit (default 2^32); rotate before the limit. |
| Never commit keys, embed them in artifacts, or rely on env vars for production. | `KeyProvider` SPI; property/env provider is marked dev-only; KMS/Vault/HSM recommended. |
| Separate keys from data; store keys encrypted (KEK/DEK). | Optional envelope mode / `KeyWrappingCipher`; `KeyProvider` can live outside the DB. |
| Keys are single-purpose; least privilege; audit access and rotation. | `KeyUsage` per key; audit events; no key material in logs. |
| Cryptographic operations should happen inside a module boundary; HSM for high assurance. | `CryptoBackend` SPI allows local JCE, PKCS#11/HSM, or KMS where key material never leaves the module. |
| Prefer automated rotation. | Spring starter scheduler + `RotationLock` SPI for multi-instance safety. |
| Document and test rotation before an incident. | Rotation/migration APIs and tests are part of v1, not a later add-on. |

### 1.2 What this library will not do

- It will not invent cryptography. It wraps JCE (`AES/GCM/NoPadding`) and optional
  providers (e.g., BouncyCastle FIPS).
- It is not a password hashing library. Passwords must use Argon2id/scrypt/PBKDF2-HMAC
  with a work factor, per the OWASP Password Storage Cheat Sheet.
- It is not a KMS/HSM. It provides the SPI to use one.
- It cannot protect plaintext or keys from a fully compromised runtime. It reduces the
  blast radius and makes rotation possible.
- It does not implement post-quantum key establishment. AES-256 at rest is symmetric;
  a PQC KEK can be plugged in later through `CryptoBackend`/`KeyProvider`.

## 2. Design goals

1. **One contract for the ecosystem.** `a2a-store-jpa`, `api-security`, and consumer
   projects use the same `Encryptor`/`KeyProvider` instead of private cipher classes.
2. **Rotation is not an afterthought.** Every ciphertext identifies the key that
   produced it; rotation never requires downtime; old keys remain readable.
3. **Safe by default, explicit by exception.** AES-256-GCM, random IV, authenticated
   encryption, fail-closed on unknown keys or tampering.
4. **Key-source agnostic.** Properties for dev/tests; JDBC, Vault, AWS KMS, Azure Key
   Vault, GCP KMS, or HSM through the same SPI for production.
5. **No Spring in the core.** The starter is optional; plain Java projects can use it.
6. **Auditable.** Rotation, compromise, and destruction are explicit operations with
   actor/reason metadata and events.

## 3. Architecture

```
                 ┌─────────────────────────────────────────────┐
Consumer ───────▶│ StringEncryptor  (String + codec)            │
                 │ Encryptor        (bytes + key selection)     │
                 │ ReEncryptor      (old value → active key)    │
                 └───────────────┬─────────────────────────────┘
                                 │
                 ┌───────────────▼─────────────────────────────┐
                 │ DefaultEncryptor                            │
                 │  - active key lookup                        │
                 │  - usage counter / limit                    │
                 │  - envelope construction                    │
                 └───────┬───────────────────────┬─────────────┘
                         │                       │
        ┌────────────────▼────────┐   ┌──────────▼──────────────┐
        │ CryptoBackend           │   │ KeyProvider             │
        │  AesGcmBackend (JCE)    │   │  InMemory (dev/test)    │
        │  KmsBackend (optional)  │   │  Properties (dev)       │
        │  HsmBackend (optional)  │   │  Jdbc / Vault / KMS     │
        └─────────────────────────┘   └──────────┬──────────────┘
                                                 │
                                      ┌──────────▼──────────────┐
                                      │ KeyRotationService      │
                                      │  strategies + lifecycle │
                                      │  audit + destroy guard  │
                                      └─────────────────────────┘
```

### Layering

| Layer | Type | Responsibility |
|---|---|---|
| Low | `CryptoBackend` | One algorithm; encrypt/decrypt bytes with a key handle; may be local JCE or remote KMS/HSM. |
| Mid | `Encryptor` | Selects the active key, enforces usage limits, builds `Ciphertext`. |
| High | `StringEncryptor` | UTF-8 String convenience + `CipherTextCodec`. |
| Key | `KeyProvider` / `KeyManager` | Read and lifecycle operations for keys. |
| Rotation | `KeyRotationService` | Policy evaluation, activation, retirement, migration session. |
| Migration | `ReEncryptor` / `ReEncryptionExecutor` | Re-encrypt stored values in consumer-defined batches. |

## 4. Contract API (core)

### 4.1 Ciphertext and context

```java
public final class Ciphertext {
    private final String formatVersion;   // "v1"
    private final String algorithmId;     // "AES-256-GCM"
    private final String keyId;           // stable, non-secret
    private final byte[] iv;              // 12 bytes for GCM
    private final byte[] ciphertext;
    private final byte[] tag;             // 16 bytes for GCM

    // defensive copies in constructor and accessors
    public String formatVersion();
    public String algorithmId();
    public String keyId();
    public byte[] iv();
    public byte[] ciphertext();
    public byte[] tag();
}

public record CipherContext(String purpose, String aad) {
    public static CipherContext none();
    public static CipherContext of(String purpose);
    public static CipherContext of(String purpose, String aad);
}
```

- `aad` is optional but recommended. It is **not stored** in the ciphertext; it must be
  reproducible at decrypt time (e.g., `table=users;column=ssn;tenant=acme`).
- `purpose` is a label for policy/audit (`a2a-task`, `oauth2-token`, `user-ssn`).

### 4.2 Encryptor

```java
public interface Encryptor {
    Ciphertext encrypt(byte[] plaintext, CipherContext context);
    byte[] decrypt(Ciphertext ciphertext, CipherContext context);
    String algorithmId();
    String activeKeyId();
}

public interface StringEncryptor {
    String encrypt(String plaintext, CipherContext context);
    String decrypt(String ciphertext, CipherContext context);
}
```

Rules:

- `encrypt` uses the **active** key only. If the active key is not usable, fail closed.
- `decrypt` dispatches by `ciphertext.keyId()`; unknown key → `UnknownKeyException`.
- Authentication failure (tag/AAD) → `CiphertextIntegrityException`; never fall back to
  plaintext.
- Implementations are thread-safe.

### 4.3 Cipher text codec

```java
public interface CipherTextCodec {
    String encode(Ciphertext ciphertext);
    Ciphertext decode(String value);
}
```

Default format (versioned, delimiter-safe):

```
kzy:v1:AES-256-GCM:<keyId>:<base64url(iv)>:<base64url(ciphertext)>:<base64url(tag)>
```

- `keyId` is restricted to `[A-Za-z0-9_.-]{1,64}`.
- Base64 URL without padding; no `:` inside.
- `v1` is the envelope format version; `AES-256-GCM` is the algorithm id.
- Strict parse: wrong field count, unknown prefix, invalid base64 → `CiphertextFormatException`.
- `formatVersion` allows a future envelope (e.g., wrapped DEK) without breaking readers.

### 4.4 Keys

```java
public enum KeyStatus {
    PENDING,        // generated, not yet used for encryption
    ACTIVE,         // used for new encryption; also decrypts
    DECRYPT_ONLY,   // rotated out; decrypt only
    COMPROMISED,    // no new encryption; decrypt for migration only
    DESTROYED       // metadata retained, material destroyed
}

public enum KeyUsage { ENCRYPTION, KEY_WRAPPING }

public record KeyMetadata(
        String id,
        String algorithmId,
        KeyUsage usage,
        KeyStatus status,
        Instant createdAt,
        Instant activatedAt,
        Instant retiredAt,
        Instant destroyAfter,
        long usageLimit) {
}

public record KeyHandle(KeyMetadata metadata, SecretKey key) {
    // key may be null for remote KMS/HSM backends
}

public interface KeyProvider {
    Optional<KeyHandle> find(String keyId);
    KeyHandle activeKey();
    List<KeyMetadata> list();
}

public interface KeyManager extends KeyProvider {
    KeyHandle create(KeyCreateRequest request);
    void transition(String keyId, KeyStatus target, KeyTransitionContext context);
    void destroy(KeyDestroyRequest request);
}
```

`KeyCreateRequest`: algorithm, optional key id, usage, `activateNow`.
`KeyTransitionContext`: actor, reason, timestamp (audit).
`KeyDestroyRequest`: actor, reason, `confirmNoCiphertextReferences`,
`confirmBackupRetentionElapsed`.

`KeyManager.transition` validates the lifecycle:

```
PENDING -> ACTIVE -> DECRYPT_ONLY -> DESTROYED
PENDING -> DESTROYED
ACTIVE -> COMPROMISED -> DECRYPT_ONLY -> DESTROYED
```

Invalid transitions throw `KeyLifecycleException`.

### 4.5 Crypto backend

```java
public interface CryptoBackend {
    String algorithmId();
    Ciphertext encrypt(byte[] plaintext, CipherContext context, KeyHandle key);
    byte[] decrypt(Ciphertext ciphertext, CipherContext context, KeyHandle key);
}
```

- `AesGcmBackend` uses JCE with `AES/GCM/NoPadding`, 96-bit random IV, 128-bit tag,
  `SecureRandom`; optional `Provider` (for FIPS).
- A `KmsBackend` / `HsmBackend` keeps key material inside the external module and uses
  `key.metadata().id()` for the operation. It can ignore `key.key()`.

### 4.6 Usage tracking

```java
public interface EncryptionCounter {
    long incrementAndGet(String keyId);
    long current(String keyId);
}
```

- In-memory default is correct for a single instance only; production multi-instance
  deployments should use a shared counter (DB/KMS) or rely on KMS-side limits.
- The `DefaultEncryptor` refuses to encrypt when the limit is reached and raises
  `KeyUsageExceededException`, which should trigger rotation.
- AES-GCM random-IV limit: 2^32 encryptions per key (NIST SP 800-38D §8.3). The default
  limit is `4_294_967_296`; a safety threshold (e.g., 80%) can trigger rotation earlier.

### 4.7 Exceptions

`CryptoException` (runtime) with subclasses:

- `CryptoConfigurationException`
- `UnknownKeyException`
- `KeyNotUsableException`
- `CiphertextFormatException`
- `CiphertextIntegrityException`
- `KeyUsageExceededException`
- `KeyLifecycleException`
- `KeyRotationException`
- `KeyDestructionException`

Messages must never include plaintext or key material.

## 5. Rotation design

### 5.1 Lifecycle

```
create (PENDING)
   │ activate
   ▼
 ACTIVE ──rotate (time/usage/manual)──▶ DECRYPT_ONLY ──retention──▶ DESTROYED
   │
   └──compromise──▶ COMPROMISED ──migration──▶ DECRYPT_ONLY ──retention──▶ DESTROYED
```

During rotation:

1. Generate or import a new key in `PENDING`.
2. Publish/install it in the shared `KeyProvider` (must be atomic across instances).
3. Activate the new key (`ACTIVE`); transition the old key to `DECRYPT_ONLY` (or
   `COMPROMISED` for an incident).
4. New writes use the new key; old ciphertext keeps its `keyId` and remains readable.
5. Optionally re-encrypt old data (eager mode) or keep old keys for reads (gradual).
6. After the retention window and backup expiry, destroy the old key (guarded).

### 5.2 Strategies

| Strategy | What happens | When to use | Pros / cons |
|---|---|---|---|
| **GRADUAL** (default) | New writes use the new key; old values keep old `keyId`; old keys stay `DECRYPT_ONLY`. | Most applications; online rotation. | No bulk write, zero downtime; old keys retained longer. |
| **EAGER** | Rotate, then re-encrypt all stored values in batches; retire old key after completion. | Small/medium data sets; compliance requires one active key. | Simple end state; heavy I/O; needs a migration job. |
| **ENVELOPE_REWRAP** | Rotate the KEK and re-wrap DEKs; data ciphertext unchanged. | Envelope mode with per-record DEKs. | **Reserved in v1.0**; the mode is defined but not implemented until envelope encryption lands. |
| **COMPROMISE** | Immediately activate a new key; mark old `COMPROMISED`; re-encrypt urgently; old key used only for migration. | Key leak/suspected leak. | Limits exposure; requires incident runbook and speed. |

Rotation **triggers** are independent of the strategy:

- `TIME_BASED` — cryptoperiod expiry. OWASP/NIST: symmetric DEKs up to ~2 years for
  low-volume; much shorter for high-volume (days/weeks). Default in the starter: 1 year,
  configurable; warn if > 2 years.
- `USAGE_BASED` — encryption count approaches the mode limit (2^32 for AES-GCM).
- `MANUAL` — operator-initiated with actor/reason.
- `COMPROMISE` — immediate, never waits for a schedule.
- `ALGORITHM_CHANGE` — new algorithm or key size; old ciphertext remains decryptable by
  algorithm id.

```java
public enum RotationMode { GRADUAL, EAGER, ENVELOPE_REWRAP, COMPROMISE }
public enum RotationReason { SCHEDULED, MANUAL, USAGE_LIMIT, COMPROMISE, ALGORITHM_CHANGE }

public record RotationRequest(
        String newKeyId,          // null = generate
        RotationMode mode,
        RotationReason reason,
        String actor,
        Duration retainOldFor) {
}

public record RotationResult(
        String oldKeyId,
        String newKeyId,
        RotationMode mode,
        KeyStatus oldKeyStatus,
        boolean reEncryptionRequired) {
}

public interface RotationPolicy {
    boolean shouldRotate(RotationContext context);
}

public record RotationContext(KeyMetadata activeKey, Instant now, long usageCount) {
}

public interface KeyRotationService {
    RotationResult rotate(RotationRequest request);
    ReEncryptor reEncryptor(String oldKeyId);
}
```

Built-in policies: `TimeBasedRotationPolicy`, `UsageBasedRotationPolicy`,
`CompositeRotationPolicy`, `AlwaysRotatePolicy` (manual/compromise).

### 5.3 Re-encryption

The key service does **not** access your database. It returns a `ReEncryptor`; the
consumer iterates its own records. A batch helper is provided for convenience:

```java
public interface ReEncryptor {
    /** Returns the new stored value, or the same value if already on the active key. */
    String reEncrypt(String storedValue, CipherContext context);
}

@FunctionalInterface
public interface ReEncryptionSource<T> {
    List<T> nextBatch();
}

public interface ReEncryptionRecord<T> {
    String ciphertext();
    T withCiphertext(String value);
}

public interface ReEncryptionSink<T> {
    void write(T record);
}

public final class ReEncryptionExecutor {
    public <T> ReEncryptionReport run(ReEncryptionSource<T> source,
                                      ReEncryptionSink<T> sink,
                                      BiFunction<T, ReEncryptor, T> mapper,
                                      int batchSize);
}
```

Rules:

- Idempotent: if `ciphertext.keyId()` is already active, return the value unchanged.
- Resumable: the consumer's source controls the cursor; the executor reports counts.
- Safe to run while the application is online; new writes use the active key.
- Encrypt and update in one transaction per record/page where possible; never log the
  plaintext.

### 5.4 Destruction and retention

- Old keys are **never** destroyed automatically while any ciphertext may reference
  them. The library cannot know your data; the consumer must confirm.
- `KeyManager.destroy` requires both confirmations and an audit context.
- Software keys: best-effort zeroization of byte arrays; managed runtimes cannot
  guarantee erasure (GC copies). HSMs use vendor zeroize.
- Keep encrypted backups for the retention window before destroying the key; otherwise
  old backups become unreadable.
- Public/non-secret metadata may be retained for audit after destruction.

### 5.5 Multi-instance and automation

- `KeyProvider` must provide **atomic** activation across instances. A property file is
  not sufficient for multi-instance production; use a shared DB/KMS/Vault.
- Auto-rotation must run on one instance. Provide a `RotationLock` SPI:
  ```java
  public interface RotationLock {
      Optional<LockHandle> tryLock(String name, Duration lease);
  }
  ```
  Default `NoopRotationLock` (single instance); provide a JDBC advisory-lock example.
- Prefer provider-side rotation (KMS) when available; the library still tracks key ids
  and ciphertext metadata.

## 6. Default implementations (v1)

| Type | Implementation | Notes |
|---|---|---|
| `CryptoBackend` | `AesGcmBackend` | AES-256-GCM, 96-bit IV, 128-bit tag, AAD, injectable `SecureRandom`/`Provider`. |
| `Encryptor` | `DefaultEncryptor` | active key, usage counter, envelope assembly. |
| `CryptoBackend` | `AesGcmBackend` (single algorithm in v1.0) | Envelope carries `algorithmId`; a backend registry for algorithm migration is v1.1. |
| `StringEncryptor` | `DefaultStringEncryptor` | UTF-8 + `CipherTextCodec`. |
| `CipherTextCodec` | `KhezyCipherTextCodec` | `kzy:v1:...` format. |
| `KeyProvider` | `InMemoryKeyProvider` | immutable snapshot; dev/test; active + retired keys. |
| `KeyProvider` | `PropertiesKeyProvider` | Base64url keys from config; **dev only** + warning. |
| `KeyManager` | `DefaultKeyManager` | in-memory lifecycle; suitable for tests/single node. |
| `KeyManager` | `JdbcKeyManager` (v1.1) | encrypted key table; shared across instances. |
| `KeyRotationService` | `DefaultKeyRotationService` | strategies + lifecycle + audit. |
| `EncryptionCounter` | `InMemoryEncryptionCounter` | single node; pluggable. |
| `KeyGenerator` | `AesKeyGenerator` | `KeyGenerator` + `SecureRandom`. |
| `KeyCodec` | `Base64KeyCodec` | Base64url without padding; constant-time decode not needed for public key ids. |

### 6.1 AES-GCM rules enforced

- Key length must be 256 bits (128 allowed only by explicit policy).
- IV is exactly 12 bytes, generated by `SecureRandom` for every encryption.
- Tag is exactly 16 bytes.
- AAD is UTF-8; optional.
- No IV reuse: random + per-key usage limit. For very high volume (>2^32 per key), use
  a deterministic counter-based IV construction or rotate more often; document.
- Decryption verifies the tag before returning plaintext.

## 7. Spring Boot starter (optional)

Prefix `io.github.khezyapp.crypto`.

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `algorithm` | `AES-256-GCM` | Algorithm id |
| `active-key-id` | — | Required for property provider |
| `keys.<id>` | — | Base64url 32-byte key (dev/test only; warn) |
| `key-provider` | `properties` | `properties`, `in-memory`, `jdbc`, custom bean |
| `rotation.mode` | `GRADUAL` | Default rotation strategy |
| `rotation.cryptoperiod` | `P365D` | Time-based trigger; warn if > 2 years |
| `rotation.usage-limit` | `4294967296` | AES-GCM per-key limit |
| `rotation.usage-threshold` | `0.8` | Trigger before the hard limit |
| `rotation.retain-old-for` | `P90D` | Retention before destruction |
| `rotation.auto` | `false` | Enable scheduled rotation |
| `rotation.check-interval` | `PT1H` | Policy check interval |
| `rotation.batch-size` | `500` | Re-encryption page size |
| `aad.enabled` | `true` | Require non-null AAD in application code |

Beans (all `@ConditionalOnMissingBean`): `KeyProvider`, `CryptoBackend`, `Encryptor`,
`StringEncryptor`, `CipherTextCodec`, `KeyRotationService`, `ReEncryptor`,
`RotationLock`.

Also provide:

- `CryptoHealthIndicator` — active key present, age vs cryptoperiod, usage %, no
  `COMPROMISED` key in the active slot.
- `KeyRotatedEvent`, `KeyStatusChangedEvent` as Spring `ApplicationEvent`s.
- Startup warning if the property provider is used outside a `dev`/`test` profile, or if
  keys are shorter than 256 bits.
- Optional actuator endpoint exposing **metadata only** (key ids, status, age, usage),
  never key material.

## 8. Integration with the KHEZY ecosystem

### 8.1 `api-security`

The existing `io.github.khezyapp.api.security.crypto` package becomes a compatibility
adapter:

- `CustomEncryptor` → deprecated, delegates to `StringEncryptor`.
- `EncryptorRegistry` / `EncryptorData` → deprecated; map to `KeyProvider`/`KeyMetadata`.
- `CipherTextFormatter` → replaced by `CipherTextCodec`; keep a legacy formatter for old
  `formatterId.version.cipherText` values.
- Add a migration path: read old values with Spring Security `TextEncryptor`, write new
  `kzy:v1:...` values; then retire the old codec.
- Fix the existing `DefaultEncryptorRegistry` inverted-cache bug during migration.

### 8.2 `a2a-store-jpa` (deferred)

This module does not exist yet; it is A2A plan task `A2A2-05`. Do not implement the
integration in crypto v1.0. The intended adoption (no crypto API change required):

- Replace `A2ACipher` with `io.github.khezyapp.crypto.StringEncryptor` (or keep
  `A2ACipher` as a thin adapter for source compatibility).
- Use AAD tied to the row identity:
  - task data: `a2a:task:<task_id>`
  - push config: `a2a:push:<task_id>:<config_id>`
  This prevents swapping ciphertext between rows.
- Rotation: run `ReEncryptionExecutor` over `a2a_tasks.task_data` and
  `a2a_push_notification_configs.task_data`; update rows in pages.
- Keep the encryption boundary: payload columns encrypted; operational columns plaintext
  and queryable.

### 8.3 Other projects

- `kflowllmlap` and future services use `khezy-crypto` directly.
- For KMS/Vault users, implement `KeyProvider`/`CryptoBackend` once and share it.

## 9. Module layout and build

```
security/crypto/
  khezy-crypto/                              (khezy.java-library)
    src/main/java/io/github/khezyapp/crypto/
      contract/     Encryptor, StringEncryptor, Ciphertext, CipherContext,
                    CipherTextCodec, KeyProvider, KeyManager, KeyMetadata,
                    KeyHandle, KeyStatus, KeyUsage, CryptoBackend, ...
      default/      DefaultEncryptor, DefaultStringEncryptor,
                    AesGcmBackend, KhezyCipherTextCodec, InMemoryKeyProvider,
                    PropertiesKeyProvider, DefaultKeyManager, AesKeyGenerator,
                    InMemoryEncryptionCounter, DefaultKeyRotationService, ...
      rotation/     RotationMode, RotationReason, RotationRequest/Result,
                    RotationPolicy, TimeBasedRotationPolicy, UsageBasedRotationPolicy,
                    CompositeRotationPolicy, ReEncryptor, ReEncryptionExecutor, ...
      exception/    CryptoException hierarchy
  README.md

auto-configurations/security/crypto-spring-boot-starter/  (khezy.springboot-library)
  src/main/java/io/github/khezyapp/crypto/spring/
  src/main/resources/META-INF/spring/...AutoConfiguration.imports
```

Root `settings.gradle`:

```groovy
includeBuild("security/crypto/khezy-crypto")
includeBuild("auto-configurations/security/crypto-spring-boot-starter")
```

Cross-module coordinates: `io.github.khezyapp:khezy-crypto:1.0.0`.

## 10. Testing and validation

### 10.1 Correctness

- AES-GCM known-answer tests from NIST CAVP (NIST SP 800-38D vectors).
- Round-trip for empty, 1-byte, 1 MB, multi-byte UTF-8, and binary payloads.
- AAD round-trip; missing/different AAD fails with `CiphertextIntegrityException`.

### 10.2 Security

- Tamper each field (IV, ciphertext, tag, keyId, algorithm) → decryption fails.
- Unknown key id → `UnknownKeyException`.
- Key status `DECRYPT_ONLY`/`COMPROMISED` cannot encrypt.
- Weak/short key rejected.
- IV uniqueness: N encryptions produce N distinct IVs (statistical test with injected
  deterministic IV source for vectors; production uses `SecureRandom`).
- No plaintext/key material in exception messages or logs (assert).

### 10.3 Rotation

- Encrypt with K1 → rotate to K2 → old value decrypts with K1; new value uses K2.
- Gradual: old key remains `DECRYPT_ONLY`; new writes use K2.
- Eager: `ReEncryptionExecutor` converts all values; old key can be destroyed afterwards.
- Compromise: old key marked `COMPROMISED`; new writes rejected on old key.
- Destruction requires confirmations; invalid lifecycle transition rejected.
- Concurrent encrypt/decrypt while rotating (no lost data; no unknown key).
- Idempotent re-encryption.

### 10.4 Operations

- Properties provider warns in non-dev profile.
- Health indicator reports key age/usage and fails on `COMPROMISED` active key.
- Auto-rotation lock prevents double rotation in multi-instance tests (fake lock).

## 11. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Key loss = data loss | `KeyProvider` backup/escrow documentation; retention before destroy; never derive keys from data. |
| Multi-instance rotation races | Atomic provider activation + `RotationLock`; prefer KMS. |
| IV reuse under high volume | Usage counter, 2^32 limit, rotate early; deterministic IV option documented. |
| Provider returns key material | `CryptoBackend` SPI allows KMS/HSM where keys never leave; local backend is for lower assurance. |
| Properties/env key leakage | Dev-only warning; production guide points to Vault/KMS/HSM; no key logging. |
| Migration miscounts | `ReEncryptionReport` + idempotent re-encrypt; verify with a checksum/count job. |
| Algorithm deprecation | Algorithm id in every ciphertext; `CryptoBackend` registry by id; rotate/migrate. |
| FIPS requirement | Injectable `Provider`; document BouncyCastle FIPS; JCE default is not FIPS-validated. |
| Old backups unreadable after destroy | Retention window + backup policy; require explicit confirmation. |

## 12. Implementation phases

| Phase | Outcome |
|---|---|
| 0 | Module skeleton, build, root registration. |
| 1 | Contracts + exceptions + codec. |
| 2 | AES-GCM backend, key ring/provider, default encryptor, usage counter, vectors. |
| 3 | Key lifecycle, rotation strategies, re-encryption executor, audit. |
| 4 | Spring Boot starter, properties, health, events, scheduler/lock. |
| 5 | Integrate `api-security` and `a2a-store-jpa`; migration notes. |
| 6 | Optional KMS/Vault/JDBC providers, docs, release. |

## 13. Decision log

1. **AES-256-GCM default** — OWASP first preference (authenticated mode), available in
   JCE, no extra dependency.
2. **Key-id envelope, not key-version-per-column** — rotation is transparent and
   per-record; no schema change per rotation.
3. **Gradual rotation default** — matches OWASP "tag with key ID + keep old keys"; eager
   re-encryption is opt-in.
4. **Separate `CryptoBackend` from `KeyProvider`** — supports local JCE and remote
   KMS/HSM without leaking key material.
5. **No Spring in core** — usable by non-Spring projects; starter is optional.
6. **AAD optional but recommended** — bind ciphertext to its logical location without
   storing the binding; callers must be consistent.
7. **Guard destruction** — the library cannot know data references; explicit human
   confirmation + audit is safer than silent deletion.
