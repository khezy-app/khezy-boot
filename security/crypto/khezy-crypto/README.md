# Khezy Crypto

Application-level encryption for sensitive data at rest, with **first-class key rotation**.

It sits between your data and a key source (properties, JDBC, Vault, KMS, HSM). It is not
a KMS, HSM, TLS stack, or password hashing library.

## Why

- **AES-256-GCM** by default: authenticated encryption, 96-bit random IV, 128-bit tag.
- **Versioned ciphertext**: every value carries its key id and algorithm id.
- **Rotation built in**: gradual, eager, envelope-rewrap, and compromise strategies.
- **Pluggable keys**: property keys for dev/tests; `KeyProvider` SPI for production.
- **No Spring in core**: the starter is optional.

Based on OWASP Cryptographic Storage / Key Management guidance and NIST SP 800-57 /
SP 800-38D.

## Quick start

```java
final var provider = new InMemoryKeyProvider();
provider.create(KeyCreateRequest.builder().id("2026-10").activateNow(true).build());

final var codec = new KhezyCipherTextCodec();
final var encryptor = new DefaultStringEncryptor(
        new DefaultEncryptor(provider, new AesGcmBackend(), new InMemoryEncryptionCounter()),
        codec);

final var context = CipherContext.of("users.ssn", "tenant=acme");
final var stored = encryptor.encrypt("123-45-6789", context);
final var plain = encryptor.decrypt(stored, context);
```

`stored` looks like:

```
kzy:v1:AES-256-GCM:2026-10:<base64url(iv)>:<base64url(ciphertext)>:<base64url(tag)>
```

### AAD

`CipherContext.aad` is authenticated but **not stored**. Use it to bind a value to its
row/tenant, for example:

```java
CipherContext.of("a2a-task", "a2a:task:" + taskId)
```

The same AAD must be supplied on decrypt. This prevents moving ciphertext between rows.

## Rotation

```java
final var rotation = new DefaultKeyRotationService(provider, counter, encryptor, codec);

final var result = rotation.rotate(RotationRequest.builder()
        .newKeyId("2026-11")
        .mode(RotationMode.GRADUAL)
        .reason(RotationReason.SCHEDULED)
        .actor("ops")
        .retainOldFor(Duration.ofDays(90))
        .build());

// New writes use result.newKeyId(); old values still decrypt with their key id.
```

| Mode | Behavior |
|---|---|
| `GRADUAL` | New writes use the new key; old keys stay `DECRYPT_ONLY` for reads. |
| `EAGER` | Rotate then re-encrypt existing values in batches, then retire the old key. |
| `ENVELOPE_REWRAP` | Reserved for a future release (KEK/DEK envelope mode). |
| `COMPROMISE` | New key immediately; old key `COMPROMISED`, decrypt-only for migration. |

Re-encrypt stored values with the consumer's own data access:

```java
final var reEncryptor = rotation.reEncryptor("2026-10");
final var migrated = reEncryptor.reEncrypt(stored, context);
```

`ReEncryptionExecutor` provides a batch helper; it does not own transactions or cursors.

### Lifecycle

```
PENDING -> ACTIVE -> DECRYPT_ONLY -> DESTROYED
                  \-> COMPROMISED -> DECRYPT_ONLY -> DESTROYED
```

- Never destroy a key while ciphertext may still reference it.
- `KeyManager.destroy` requires both confirmations (no references, backup retention
  elapsed).
- Keep old keys through the backup retention window.

## Key storage

| Source | Use |
|---|---|
| `InMemoryKeyProvider` | tests, single-node demos |
| `PropertiesKeyProvider` | local development only; logs a warning |
| `KeyProvider` SPI | JDBC, Vault, AWS KMS, Azure Key Vault, GCP KMS, HSM |

Production keys must not be committed to source, embedded in build artifacts, or kept in
plain environment variables. Prefer a KMS/HSM; use `CryptoBackend` if key material must
never leave the module.

## AES-GCM limits

With random 96-bit IVs, limit each key to **2^32** encryption operations. The default
`EncryptionCounter` enforces this per process; use a shared counter or KMS-side limit in
multi-instance deployments. Rotate before the limit.

## What this does NOT do

- Password hashing (use Argon2id/scrypt/PBKDF2 per OWASP Password Storage).
- TLS / data-in-transit encryption.
- Key generation inside an HSM (use a KMS/HSM backend).
- Post-quantum key establishment.
- Automatic data discovery: you tell the re-encryption job which records to migrate.

## References

- OWASP Cryptographic Storage Cheat Sheet
- OWASP Key Management Cheat Sheet
- OWASP Secrets Management Cheat Sheet
- NIST SP 800-57 Part 1 Rev. 5
- NIST SP 800-38D (GCM)
