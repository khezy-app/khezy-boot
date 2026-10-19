# CRYPTO-02 — Contract API, records, exceptions, codec

**Prerequisites:** `00`, `01`.
**Design:** §4.

## 1. Goal

Define the stable, Spring-free contract every KHEZY library and consumer will use. No
crypto implementation yet.

Package `io.github.khezyapp.crypto.contract`.

## 2. Types

### 2.1 Ciphertext and context

- `Ciphertext` — **final class**, not a record (byte[] equality/defensive copies):
  - fields: `formatVersion`, `algorithmId`, `keyId`, `iv`, `ciphertext`, `tag`.
  - validate non-null, `iv.length > 0`, `tag.length > 0`.
  - constructors and accessors copy `byte[]`.
  - `equals`/`hashCode` use `Arrays.equals` for byte fields.
- `CipherContext` — record `(String purpose, String aad)`:
  - `purpose` required, non-blank, `[a-zA-Z0-9_.:-]{1,64}`.
  - `aad` nullable; UTF-8 in the backend.
  - `none()`, `of(purpose)`, `of(purpose, aad)`.

### 2.2 Encryptors

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

### 2.3 Codec

```java
public interface CipherTextCodec {
    String encode(Ciphertext ciphertext);
    Ciphertext decode(String value);
}
```

- `KhezyCipherTextCodec` in `contract`? Put the **interface** in `contract`, the default
  in `default` (task 03).
- Document the v1 format precisely:
  `kzy:v1:<algorithmId>:<keyId>:<base64url(iv)>:<base64url(ciphertext)>:<base64url(tag)>`.
- `keyId` `[A-Za-z0-9_.-]{1,64}`; `algorithmId` `[A-Za-z0-9._-]{1,32}`.
- Strict parse; `CiphertextFormatException` on any mismatch.

### 2.4 Keys

- `KeyStatus` enum: `PENDING, ACTIVE, DECRYPT_ONLY, COMPROMISED, DESTROYED`.
- `KeyUsage` enum: `ENCRYPTION, KEY_WRAPPING`.
- `KeyMetadata` record:
  `(String id, String algorithmId, KeyUsage usage, KeyStatus status, Instant createdAt,
  Instant activatedAt, Instant retiredAt, Instant destroyAfter, long usageLimit)`.
  - validate: id non-blank; `usageLimit > 0`.
  - `isUsableForEncryption()` → `status == ACTIVE`.
  - `isUsableForDecryption()` → `ACTIVE`, `DECRYPT_ONLY`, `COMPROMISED`.
- `KeyHandle` — final class `(KeyMetadata metadata, SecretKey key)`:
  - `key` may be null for remote backends; document.
  - defensive metadata reference; `key` is immutable interface.
- `KeyProvider`:
  ```java
  Optional<KeyHandle> find(String keyId);
  KeyHandle activeKey();
  List<KeyMetadata> list();
  ```
- `KeyManager extends KeyProvider`:
  ```java
  KeyHandle create(KeyCreateRequest request);
  void transition(String keyId, KeyStatus target, KeyTransitionContext context);
  void destroy(KeyDestroyRequest request);
  ```
- `KeyCreateRequest` record `(String id, String algorithmId, KeyUsage usage,
  boolean activateNow)` + builder.
- `KeyTransitionContext` record `(String actor, String reason, Instant at)`.
- `KeyDestroyRequest` record `(String keyId, String actor, String reason,
  boolean confirmNoCiphertextReferences, boolean confirmBackupRetentionElapsed)`.

### 2.5 Backend and counter

```java
public interface CryptoBackend {
    String algorithmId();
    Ciphertext encrypt(byte[] plaintext, CipherContext context, KeyHandle key);
    byte[] decrypt(Ciphertext ciphertext, CipherContext context, KeyHandle key);
}

public interface EncryptionCounter {
    long incrementAndGet(String keyId);
    long current(String keyId);
}
```

### 2.6 Exceptions

Package `io.github.khezyapp.crypto.exception`:

- `CryptoException extends RuntimeException`
- `CryptoConfigurationException`
- `UnknownKeyException`
- `KeyNotUsableException`
- `CiphertextFormatException`
- `CiphertextIntegrityException`
- `KeyUsageExceededException`
- `KeyLifecycleException`
- `KeyRotationException`
- `KeyDestructionException`

All constructors accept a message and optional cause; never accept plaintext or keys.
Add `Objects.requireNonNull` validation where appropriate.

## 3. Tests

- `CiphertextTest` — defensive copies; equals/hashCode; null/empty IV/tag rejected.
- `CipherContextTest` — required purpose; validation; factories.
- `KeyMetadataTest` — usability rules; usage limit validation.
- `CiphertextFormatContractTest` — the documented regex/format is asserted (no encoder yet).
- `ExceptionTest` — no plaintext in messages (construct with dummy values and assert).

## 4. Verification

```bash
./gradlew :khezy-crypto:check
```

## 5. Handoff

Record the final signatures and the exact envelope format. Tasks 03/04/05 rely on these
shapes. `graphify update .`.
