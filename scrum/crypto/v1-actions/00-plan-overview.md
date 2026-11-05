# KHEZY Crypto v1 — Action Plan (Master Context)

> Read this file first. The design is `../crypto-library-design.md`. Every task in
> `scrum/crypto/v1-actions/` references this document.

## 1. Goal

Ship a reusable encryption utility for sensitive data at rest with **first-class key
rotation**, so every KHEZY library uses one safe implementation instead of private
cipher classes.

Core promise:

```java
Ciphertext ct = encryptor.encrypt(bytes, CipherContext.of("users.ssn", "tenant=acme"));
byte[] pt = encryptor.decrypt(ct, CipherContext.of("users.ssn", "tenant=acme"));
```

Rotation promise:

```java
RotationResult result = rotationService.rotate(RotationRequest.builder()
        .mode(RotationMode.GRADUAL)
        .reason(RotationReason.SCHEDULED)
        .retainOldFor(Duration.ofDays(90))
        .build());
// new writes use result.newKeyId(); old ciphertext still decrypts
```

## 2. Scope

In scope:

- Contract API (`Encryptor`, `StringEncryptor`, `Ciphertext`, `CipherContext`,
  `CipherTextCodec`, `KeyProvider`, `KeyManager`, `CryptoBackend`, rotation types).
- Default AES-256-GCM implementation, key ring/provider, usage counter.
- Key lifecycle, rotation strategies (gradual/eager/envelope/compromise), re-encryption
  executor.
- Optional Spring Boot starter (properties, beans, events, health, scheduler/lock).
- Integration: `api-security` legacy crypto migration.
- Tests: NIST vectors, tamper, rotation, concurrency, config validation.

**Deferred:** `a2a-store-jpa` integration. That module does not exist yet (it is
A2A plan task `A2A2-05`). The crypto library is designed so the A2A store can adopt it
later without API changes; see `crypto-library-design.md` §8.2.

Out of scope (v1):

- KMS/Vault/JDBC key providers (v1.1; SPI is ready).
- Post-quantum algorithms.
- Password hashing (use OWASP Password Storage guidance).
- TLS/transport crypto.
- A UI or admin console.
- Automatic data discovery: the consumer tells us which records to re-encrypt.

## 3. Module layout

| Path | rootProject.name | Plugin |
|---|---|---|
| `security/crypto/khezy-crypto` | `khezy-crypto` | `khezy.java-library` |
| `auto-configurations/security/crypto-spring-boot-starter` | `crypto-spring-boot-starter` | `khezy.springboot-library` |

Root `settings.gradle`:

```groovy
includeBuild("security/crypto/khezy-crypto")
includeBuild("auto-configurations/security/crypto-spring-boot-starter")
```

Coordinates:

- `io.github.khezyapp:khezy-crypto:1.0.0`
- `io.github.khezyapp:khezy-crypto-spring-boot-starter:1.0.0`

Base package:

- `io.github.khezyapp.crypto.*`
- `io.github.khezyapp.crypto.spring.*`

## 4. Verified environment facts

- Java 17 target, JDK 21 toolchain, Checkstyle 13.1.0.
- JCE provides `AES/GCM/NoPadding` on all supported JDKs. 256-bit AES requires unlimited
  strength policy (default since Java 8u161).
- `SecureRandom` is the only randomness source allowed for keys/IVs.
- Spring Boot 4.1.0 for the starter; core must not depend on Spring.
- Gradle composite build: each module has its own `settings.gradle` including
  `build-logic`; register in root via `includeBuild(...)`.
- No `java-library` directly; use `khezy.*` plugins.
- Existing `api-security` crypto uses Spring Security `Encryptors.delux` with a
  `DefaultEncryptorRegistry`; `getTextEncryptor` has an inverted cache condition and must
  not be copied.

## 5. Task graph

| # | File | Task | Prerequisites |
|---|---|---|---|
| 1 | `01-module-skeleton.md` | Create two modules, build files, root registration, README stub | `00` |
| 2 | `02-contracts.md` | Contract API, records, exceptions, codec | `00`, `01` |
| 3 | `03-aes-gcm-defaults.md` | AES-GCM backend, key generator/ring/provider, default encryptor, counter, vectors | `00`, `02` |
| 4 | `04-rotation-and-migration.md` | Key lifecycle, rotation policies/service, re-encryption executor, audit | `00`, `03` |
| 5 | `05-spring-starter.md` | Properties, auto-config, health, events, scheduler/lock | `00`, `03`, `04` |
| 6 | `06-integrations-and-release.md` | `api-security` adapter/migration, docs, release | all |

## 6. Shared conventions

1. `final` on all parameters/locals (`final var x`), 4 spaces, 120 cols, Egyptian braces.
2. No comments in code unless required by Checkstyle/Javadoc; public API gets Javadoc.
3. No custom cryptography. Only JCE primitives and well-reviewed constructions.
4. Never log plaintext, keys, IVs, or full ciphertext.
5. All public types are in `contract/` unless they are explicitly defaults/rotation.
6. Defensive copies for all `byte[]` in records/classes.
7. Fail closed: unknown key, tampered data, unusable key, and missing active key throw.
8. Tests must inject `SecureRandom`/IV source where determinism is needed; production uses
   `SecureRandom`.

## 7. Verification

```bash
./gradlew :khezy-crypto:check
./gradlew :crypto-spring-boot-starter:check
./gradlew unittest
./gradlew checkstyleMains checkstyleTests
```

## 8. Handoff protocol

- On finishing a task, append one entry to `99-handoff-log.md` using its template.
- Do not paste the handoff into chat; chat replies are one line: "task complete, log
  entry written".
- On starting a task, read this file, your task file, and the last entry per prerequisite
  task.

## 9. Implementation status

| Task | Status |
|---|---|
| CRYPTO-01 module skeleton | completed |
| CRYPTO-02 contracts | completed |
| CRYPTO-03 AES-GCM defaults | completed |
| CRYPTO-04 rotation + re-encryption | completed |
| CRYPTO-05 Spring starter | completed |
| CRYPTO-06 api-security integration + docs | completed |
| `a2a-store-jpa` integration | deferred (module does not exist yet) |

Verified with `:khezy-crypto:check`, `:crypto-spring-boot-starter:check`,
`:api-security:check`, `unittest`, and `checkstyleMains checkstyleTests`.

## 10. Definition of Done

1. AES-GCM round-trip, tamper, AAD, and NIST-vector tests pass.
2. Rotation: gradual and eager paths tested; old ciphertext readable; new writes use the
   new key; destruction is guarded.
3. Starter boots with property keys; health reports key age/usage; no key material in
   logs or actuator output.
4. `api-security` uses the library (or a documented adapter) and keeps backward
   compatibility for existing ciphertext. `a2a-store-jpa` adoption is deferred.
5. Docs explain key storage, cryptoperiods, rotation runbooks, and the OWASP/NIST basis.
