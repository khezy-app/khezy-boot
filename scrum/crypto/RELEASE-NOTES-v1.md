# Khezy Crypto v1.0.0 — Release Notes

Application-level encryption for sensitive data at rest, with first-class key rotation.

## Modules

| Coordinates | Summary |
|---|---|
| `io.github.khezyapp:khezy-crypto:1.0.0` | Contract API, AES-256-GCM, key providers, lifecycle, rotation, re-encryption |
| `io.github.khezyapp:khezy-crypto-spring-boot-starter:1.0.0` | Properties, beans, health, audit events, optional scheduled rotation |

## Features

- **AES-256-GCM** with 96-bit random IV, 128-bit tag, optional AAD.
- **Versioned envelope**: `kzy:v1:<algorithm>:<keyId>:<iv>:<ciphertext>:<tag>`.
- **Key lifecycle**: `PENDING`, `ACTIVE`, `DECRYPT_ONLY`, `COMPROMISED`, `DESTROYED`.
- **Rotation strategies**: `GRADUAL` (default), `EAGER`, `COMPROMISE`.
- **Atomic active-key swap** (`KeyManager.swapActive`).
- **Re-encryption helper** (`ReEncryptor`, `ReEncryptionExecutor`) with batching.
- **Usage limits** for AES-GCM (2^32 per key) via `EncryptionCounter`.
- **Key providers**: in-memory and properties (dev/tests), with a `KeyProvider` /
  `CryptoBackend` SPI for JDBC, Vault, KMS, and HSM.
- **Spring starter**: property binding, health indicator, audit events, optional
  scheduled rotation with a `RotationLock` SPI.

## Security properties

- Keys/IVs use `SecureRandom`; no custom cryptography.
- Fail closed on unknown key, tampered ciphertext, unusable key, or missing active key.
- AAD is authenticated but not stored; bind ciphertext to its row/tenant.
- Destruction requires explicit confirmations (no references, backup retention elapsed).
- No plaintext or key material in logs/actuator output.
- Based on OWASP Cryptographic Storage/Key Management guidance and NIST SP 800-57 /
  SP 800-38D.

## Migration from `api-security` crypto

`io.github.khezyapp.api.security.crypto.CustomEncryptor` is now a deprecated adapter:

- new values use `kzy:v1:...` when constructed with a `StringEncryptor`;
- legacy `formatterId.version.cipherText` values remain readable via the old
  `EncryptorRegistry` path;
- the inverted-cache bug in `DefaultEncryptorRegistry#getTextEncryptor` is fixed;
- migrate stored values with `ReEncryptionExecutor` and then remove the legacy formatter.

## Known gaps

- `ENVELOPE_REWRAP` is defined but reserved for a future release.
- JDBC/Vault/KMS key providers are SPI + documentation only in v1.0; implementations are
  planned for v1.1.
- FIPS/HSM requires an external `Provider` or `CryptoBackend`; the default JCE provider is
  not FIPS-validated.
- `a2a-store-jpa` integration is deferred until that module exists.
- No post-quantum key establishment (AES-256 at rest remains symmetric).

## Verification

```bash
./gradlew :khezy-crypto:check
./gradlew :crypto-spring-boot-starter:check
./gradlew :api-security:check
./gradlew unittest
./gradlew checkstyleMains checkstyleTests
```
