# CRYPTO-06 — Ecosystem integration, docs, release

**Prerequisites:** all previous tasks.
**Design:** §8, §13.

## 1. Goal

Prove the library in the existing KHEZY modules, migrate the legacy crypto code, document
key storage/rotation runbooks, and prepare the release.

## 2. Integrate `api-security`

Path: `rest-api/api-security/src/main/java/io/github/khezyapp/api/security/crypto`.

- Add `implementation "io.github.khezyapp:khezy-crypto:1.0.0"`.
- `CustomEncryptor` → `@Deprecated`, delegate to `StringEncryptor`:
  - `encrypt(version, plainText)`: resolve the key id from the version mapping and call
    `stringEncryptor.encrypt(plainText, context)`.
  - `decrypt(cipherText)`: detect the `kzy:` prefix → new decryptor; otherwise use the
    legacy Spring Security `TextEncryptor` path.
- Keep `CipherTextFormatter`/`EncryptorRegistry` for source compatibility but mark
  deprecated; implement them over `KeyProvider`/`CipherTextCodec` where possible.
- **Fix the inverted cache bug** in `DefaultEncryptorRegistry#getTextEncryptor` during the
  adapter rewrite.
- Add `LegacySpringCipherTextCodec` (or equivalent) for old
  `formatterId.version.cipherText` values; document a migration job that re-encrypts rows
  to `kzy:v1:...`.
- Tests: old value decrypts; new value uses the new format; mixed rows work; deprecated
  API still compiles.

## 3. Deferred: `a2a-store-jpa`

Do **not** integrate `a2a-store-jpa` in this task. That module does not exist yet (it is
A2A plan task `A2A2-05`). The crypto library is designed so it can adopt
`StringEncryptor` later without API changes; the expected AAD scheme is documented in
`crypto-library-design.md` §8.2.

## 4. Documentation

- `security/crypto/khezy-crypto/README.md`:
  - purpose, non-goals (not KMS/password hashing/TLS);
  - quick start (property keys for dev, KMS/Vault for prod);
  - AAD guidance;
  - rotation modes and when to use each;
  - lifecycle and destruction rules;
  - multi-instance caveats;
  - OWASP/NIST references.
- `crypto-spring-boot-starter/README.md`:
  - property table;
  - bean overrides;
  - health/events;
  - scheduler + `RotationLock`;
  - actuator metadata.
- `scrum/crypto/README.md` — design + plan index.
- Migration guide for `api-security` legacy ciphertext.
- Add a runbook section: "compromise rotation" and "scheduled rotation" checklists.

## 5. Release

- Version `1.0.0` for both modules.
- POM name/description.
- `RELEASE-NOTES-v1.md`:
  - features, rotation strategies, security properties;
  - baseline Java 17;
  - known gaps (KMS/Vault/JDBC providers are SPI + docs in v1.0, implementations in v1.1;
    HSM/FIPS requires an external provider);
  - migration from `api-security` crypto.
- Register both modules in root `settings.gradle`.
- Run the full verification gate.

## 6. Full verification

```bash
./gradlew :khezy-crypto:check :crypto-spring-boot-starter:check
./gradlew :api-security:check
./gradlew unittest
./gradlew checkstyleMains checkstyleTests
```

## 7. Handoff

Record the final API version, bean names, integration points, migration status, and any
deferred provider implementations. `graphify update .`.
