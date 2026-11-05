# CRYPTO-03 — AES-GCM default implementation

**Prerequisites:** `00`, `02`.
**Design:** §4.5–§6.1.

## 1. Goal

Provide the production-quality default: AES-256-GCM, versioned codec, key generation,
in-memory key provider, usage counter, and the default `Encryptor`/`StringEncryptor`.

Package `io.github.khezyapp.crypto.default`.

## 2. Classes

### 2.1 `AesGcmBackend implements CryptoBackend`

- Algorithm id `AES-256-GCM`.
- `Cipher.getInstance("AES/GCM/NoPadding", provider)`; provider optional (null = default
  JCE).
- Key: `key.key()` must be non-null; `AES` algorithm; 32 bytes (256-bit) by default;
  16 bytes allowed only through an explicit `allow128Bit` flag (default false).
- IV: exactly 12 bytes from the injected `IvGenerator` (default `SecureRandomIvGenerator`
  using `SecureRandom`).
- Tag: 128 bits.
- AAD: `context.aad()` UTF-8, or empty when null.
- `encrypt`: `Cipher.init(ENCRYPT_MODE, key, new GCMParameterSpec(128, iv))`; updateAAD;
  doFinal; return `Ciphertext("v1", algorithmId, key.id, iv, ciphertext, tag)`.
  JCE returns ciphertext||tag; split at `length - 16`.
- `decrypt`: validate the ciphertext algorithm id matches (or delegate by algorithm id in
  task 04), reconstruct `ciphertext||tag`, init `DECRYPT_MODE`, verify; on
  `AEADBadTagException` throw `CiphertextIntegrityException` with no plaintext.
- Thread-safe: no mutable state; create `Cipher` per operation.

### 2.2 `SecureRandomIvGenerator` + `IvGenerator`

```java
public interface IvGenerator { byte[] nextIv(int length); }
```
- Default uses `SecureRandom.nextBytes`.
- Tests inject a deterministic generator for NIST vectors only.

### 2.3 `AesKeyGenerator`

- `KeyGenerator.getInstance("AES")` + `SecureRandom`.
- `generate(KeyCreateRequest)` → `KeyHandle` with `PENDING` or `ACTIVE` metadata.
- Key id: `request.id()` or `Instant`-based + random suffix; validate `[A-Za-z0-9_.-]`.
- `SecretKeySpec` with 256-bit key by default.

### 2.4 `KhezyCipherTextCodec implements CipherTextCodec`

- Encode exactly:
  `kzy:v1:<alg>:<keyId>:<b64url(iv)>:<b64url(ciphertext)>:<b64url(tag)>`.
- Base64 URL without padding (`java.util.Base64.getUrlEncoder().withoutPadding()`).
- Decode: `split(":", -1)` and require exactly 7 fields; validate prefix/version/id/alg;
  decode Base64; construct `Ciphertext`.
- Reject any malformed input with `CiphertextFormatException` (never `IllegalArgumentException`
  leaking internals).
- No schema lookup; keep it pure.

### 2.5 `DefaultStringEncryptor implements StringEncryptor`

- UTF-8 encode/decode.
- Delegates to `Encryptor` + `CipherTextCodec`.
- Reject null plaintext/ciphertext with `Objects.requireNonNull`.

### 2.6 `DefaultEncryptor implements Encryptor`

Constructor: `(KeyProvider keyProvider, CryptoBackend backend, EncryptionCounter counter)`.

- `encrypt`:
  1. `active = keyProvider.activeKey()`.
  2. require `metadata.isUsableForEncryption()`.
  3. `count = counter.incrementAndGet(active.id())`.
  4. if `count > metadata.usageLimit()` → `KeyUsageExceededException`.
  5. `backend.encrypt(...)`.
- `decrypt`:
  1. `key = keyProvider.find(ciphertext.keyId())` or `UnknownKeyException`.
  2. require `metadata.isUsableForDecryption()`.
  3. `backend.decrypt(...)`.
- `algorithmId()` delegates to backend; `activeKeyId()` returns the current active id.
- Thread-safe.
- v1.0 uses one `CryptoBackend`. The envelope records `algorithmId`; a backend registry
  for algorithm migration is a v1.1 addition (no data rewrite needed to add it).

### 2.7 In-memory key provider / manager

- `InMemoryKeyProvider implements KeyManager`:
  - `ConcurrentHashMap<String, KeyHandle>`; one active key (atomic).
  - `create` → PENDING/ACTIVE; `transition` validates the lifecycle; `destroy` requires
    both confirmations and sets `DESTROYED`, best-effort zeroizes a local key's encoded
    bytes (document that `SecretKeySpec` copies cannot be guaranteed zeroed).
  - `activeKey()` throws `CryptoConfigurationException` when none.
  - immutable `list()` snapshot.
- `PropertiesKeyProvider implements KeyProvider`:
  - accepts `Map<String, String>` of Base64url keys and an active id.
  - decode to `SecretKeySpec`; reject keys that are not 32 bytes.
  - always logs a **warning** that property-based keys are for dev/tests only.
  - read-only; rotation via properties requires a restart (document).
- `ReadOnlyKeyManager implements KeyManager` — wraps a `KeyProvider`; `create`,
  `transition`, and `destroy` throw `KeyRotationException("read-only key provider")`.
- Zeroization note: `SecretKeySpec` copies the byte array, so zeroizing the source is
  best-effort only. Document that managed runtimes cannot guarantee erasure; HSM/KMS is
  the high-assurance path.
- `InMemoryEncryptionCounter implements EncryptionCounter`: `ConcurrentHashMap` +
  `AtomicLong`; document single-node only.

## 3. Tests

### 3.1 Known-answer vectors

- Add NIST AES-GCM test vectors (key, IV, AAD, plaintext, ciphertext, tag) from the
  NIST CAVP / SP 800-38D examples. Use a deterministic `IvGenerator` and direct
  `AesGcmBackend` (bypass the random IV).
- At least 3 vectors: empty plaintext, 16-byte block, multi-block with AAD.

### 3.2 Round-trip

- empty, 1 byte, 1 MB, UTF-8 multi-byte, binary with zero bytes.
- AAD present/absent; different AAD fails.
- `StringEncryptor` round-trip.

### 3.3 Security

- Tamper IV/ciphertext/tag/keyId/algorithm → all fail with integrity/format errors.
- Unknown key id → `UnknownKeyException`.
- Key status `DECRYPT_ONLY`/`COMPROMISED` cannot encrypt.
- 128-bit key rejected by default; 256-bit accepted.
- No plaintext in exception messages (assert).
- IV uniqueness: 10k encryptions produce 10k distinct IVs (random source).

### 3.4 Codec

- Round-trip; exact format string; malformed/truncated/extra fields rejected; URL-safe
  characters only; key-id validation.

### 3.5 Counter/provider

- Counter increments; limit exceeded throws.
- Provider active key required; duplicate id rejected; list snapshot immutable.

## 4. Verification

```bash
./gradlew :khezy-crypto:check
```

## 5. Handoff

Record the exact JCE behavior (tag length, AAD, provider), the NIST vector source, and
any key-generation/id constraints. Task 04 builds rotation on these. `graphify update .`.
