# CRYPTO-04 — Key lifecycle, rotation strategies, re-encryption

**Prerequisites:** `00`, `03`.
**Design:** §5.

## 1. Goal

Implement the reason this library exists: safe key rotation with explicit lifecycle,
configurable strategies, audit metadata, and a re-encryption helper.

Package `io.github.khezyapp.crypto.rotation`.

## 2. Types

### 2.1 Requests/results

```java
public enum RotationMode { GRADUAL, EAGER, ENVELOPE_REWRAP, COMPROMISE }
public enum RotationReason { SCHEDULED, MANUAL, USAGE_LIMIT, COMPROMISE, ALGORITHM_CHANGE }

public record RotationRequest(String newKeyId, RotationMode mode, RotationReason reason,
                              String actor, Duration retainOldFor) { /* builder */ }

public record RotationResult(String oldKeyId, String newKeyId, RotationMode mode,
                             KeyStatus oldKeyStatus, boolean reEncryptionRequired) { }
```

Validation:

- `mode` default `GRADUAL`; `reason` required.
- `COMPROMISE` reason forces `mode = COMPROMISE` and old status `COMPROMISED`.
- `retainOldFor` default 90 days; must be positive.

### 2.2 Policy

```java
public record RotationContext(KeyMetadata activeKey, Instant now, long usageCount) { }

public interface RotationPolicy {
    boolean shouldRotate(RotationContext context);
}
```

Implementations:

- `TimeBasedRotationPolicy(Duration cryptoperiod)` — compare `now` with
  `activeKey.activatedAt + cryptoperiod`.
- `UsageBasedRotationPolicy(long limit, double threshold)` — `usageCount >= limit *
  threshold` (default 0.8).
- `CompositeRotationPolicy(List<RotationPolicy>)` — any match.
- `AlwaysRotatePolicy` — manual / compromise.
- Recommend cryptoperiod ≤ 2 years; log a warning above that (NIST/OWASP guidance).

### 2.3 Key lifecycle

`KeyLifecycle` (package-private or public utility) validates transitions:

```
PENDING -> ACTIVE, DESTROYED
ACTIVE -> DECRYPT_ONLY, COMPROMISED
DECRYPT_ONLY -> DESTROYED
COMPROMISED -> DECRYPT_ONLY, DESTROYED
DESTROYED -> (none)
```

- Illegal transition → `KeyLifecycleException`.
- `destroy` requires `confirmNoCiphertextReferences` and
  `confirmBackupRetentionElapsed`; otherwise `KeyDestructionException`.

### 2.4 Rotation service

```java
public interface KeyRotationService {
    RotationResult rotate(RotationRequest request);
    ReEncryptor reEncryptor(String oldKeyId);
}
```

`DefaultKeyRotationService(KeyManager keyManager, KeyProvider keyProvider,
EncryptionCounter counter, Clock clock, List<AuditListener> listeners)`:

1. Read the current active key.
2. If `newKeyId` equals the active id → return a no-op result (idempotent).
3. Create the new key (`PENDING`) with `ENCRYPTION` usage and `activateNow=false`.
4. Atomically activate the new key in the `KeyManager`.
5. Transition the old key:
   - `COMPROMISE` → `COMPROMISED`
   - otherwise → `DECRYPT_ONLY`
   - set `retiredAt = now`, `destroyAfter = now + retainOldFor`.
6. Publish `KeyRotatedEvent` (plain Java listener; Spring adapter in task 05) with old/new
   ids, reason, actor, mode — never key material.
7. Return `RotationResult`; `reEncryptionRequired = mode != GRADUAL` (or always true if
   the consumer wants full migration).

`rotate` must be idempotent and safe under concurrent calls: use the `KeyManager`'s
atomic activation and a local lock; in multi-instance deployments the provider must
guarantee atomicity (document).

### 2.5 Re-encryption

```java
public interface ReEncryptor {
    String reEncrypt(String storedValue, CipherContext context);
}
```

`DefaultReEncryptor(Encryptor encryptor, CipherTextCodec codec)`:

1. Decode the stored value; if decode fails → `CiphertextFormatException` (do not guess).
2. If `ciphertext.keyId().equals(encryptor.activeKeyId())` → return the original value.
3. Decrypt with the old key, encrypt with the active key, encode.
4. Idempotent and resumable.

`KeyRotationService.reEncryptor(oldKeyId)` validates that the old key exists and is
`DECRYPT_ONLY`/`COMPROMISED`; returns a `ReEncryptor` bound to that migration.

### 2.6 Batch migration helper

```java
@FunctionalInterface
public interface ReEncryptionSource<T> { List<T> nextBatch(); }

public interface ReEncryptionRecord<T> {
    String ciphertext();
    T withCiphertext(String value);
}

public interface ReEncryptionSink<T> { void write(T record); }

public record ReEncryptionReport(long scanned, long rewritten, long skipped, long failed) { }

public final class ReEncryptionExecutor {
    public <T extends ReEncryptionRecord<T>> ReEncryptionReport run(
        ReEncryptionSource<T> source, ReEncryptionSink<T> sink, ReEncryptor reEncryptor,
        CipherContext context, int batchSize);
}
```

- Process in batches; write per batch (or per record if the consumer's sink does).
- On a single record failure: count it, log only key id + record identifier (never
  plaintext), continue or abort based on a `continueOnError` flag (default false).
- The consumer owns transactions and cursors. Document that a page-level transaction is
  recommended.
- `ReEncryptionReport` is for observability, not a guarantee; provide a verification
  query in docs.

## 3. Audit

```java
public interface AuditListener {
    void onKeyEvent(KeyEvent event);
}

public record KeyEvent(String type, String keyId, String relatedKeyId,
                       String actor, String reason, Instant at) { }
```

- Types: `CREATED`, `ACTIVATED`, `ROTATED`, `COMPROMISED`, `DESTROYED`, `RE_ENCRYPTED`.
- Never include key material or plaintext.
- The Spring starter publishes these as `ApplicationEvent`s.

## 4. Tests

- `rotation/DefaultKeyRotationServiceTest`:
  - gradual: new active, old `DECRYPT_ONLY`, old ciphertext decrypts, new writes use new.
  - eager: re-encrypt all values, old destroyable after confirmations.
  - compromise: old `COMPROMISED`, new writes rejected on old key, re-encrypt works.
  - idempotent rotate (same new id).
  - invalid lifecycle transitions.
  - destruction without confirmations fails.
  - audit events emitted (no key material).
- `rotation/TimeBasedRotationPolicyTest` — before/at/after cryptoperiod using a fixed `Clock`.
- `rotation/UsageBasedRotationPolicyTest` — threshold behavior.
- `rotation/DefaultReEncryptorTest` — already-active no-op; old→new; malformed value fails.
- `rotation/ReEncryptionExecutorTest` — batching, skip, failure counting, report.
- Concurrency test: rotate while N threads encrypt/decrypt; no `UnknownKeyException`, no
  lost values.

## 5. Verification

```bash
./gradlew :khezy-crypto:check
```

## 6. Handoff

Record the exact lifecycle rules, event shape, and re-encryption contract. Task 05 wires
the service; task 06 integrates the stores. `graphify update .`.
