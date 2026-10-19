# Khezy Crypto Spring Boot Starter

Spring Boot auto-configuration for [Khezy Crypto](../../../security/crypto/khezy-crypto).

## Add the dependency

```groovy
implementation "io.github.khezyapp:khezy-crypto-spring-boot-starter:1.0.0"
```

## Development configuration

```yaml
io:
  github:
    khezyapp:
      crypto:
        active-key-id: "2026-10"
        keys:
          "2026-10": "<base64url-32-byte-key>"
          "2026-07": "<base64url-32-byte-key>"
```

Property keys are **for development/tests only**. The starter warns outside `dev`/`test`
profiles; set `fail-on-insecure-key-source: true` to make it fatal.

## Beans

All beans are `@ConditionalOnMissingBean`:

| Bean | Default |
|---|---|
| `KeyManager` | properties (read-only) or in-memory |
| `CryptoBackend` | `AesGcmBackend` |
| `EncryptionCounter` | in-memory |
| `Encryptor` / `StringEncryptor` | `DefaultEncryptor` / `DefaultStringEncryptor` |
| `CipherTextCodec` | `KhezyCipherTextCodec` |
| `KeyRotationService` | `DefaultKeyRotationService` |
| `ReEncryptor` | `DefaultReEncryptor` |
| `RotationPolicy` | time + usage composite |
| `RotationLock` | `NoopRotationLock` (single instance) |
| `CryptoHealthIndicator` | when Actuator is present |
| `CryptoAuditEventPublisher` | Spring `ApplicationEvent`s |

Provide your own `KeyManager` (for JDBC/Vault/KMS) to override key storage and rotation.

## Rotation

```yaml
io:
  github:
    khezyapp:
      crypto:
        rotation:
          mode: GRADUAL
          cryptoperiod: P365D
          usage-threshold: 0.8
          retain-old-for: P90D
          auto: false
          check-interval: PT1H
```

- `auto: true` enables a scheduled policy check.
- Multi-instance: provide a shared `RotationLock` (DB/advisory lock) or run auto-rotation
  on one instance.
- `auto: true` with the read-only properties provider fails at startup.

## Health and events

- `CryptoHealthIndicator` reports active key id/status/age/usage; **no key material**.
- `CryptoKeyEvent` is published for `ROTATED`/lifecycle audit events.

## Security notes

- AES-256-GCM, random 96-bit IV, 128-bit tag.
- Do not log plaintext, key material, IVs, or full ciphertext.
- Use AAD to bind ciphertext to its row/tenant.
- Back up keys and retain them through the backup window before destruction.
