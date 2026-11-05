# KHEZY Crypto — Design & Plan Index

| Document | Purpose |
|---|---|
| [`crypto-library-design.md`](crypto-library-design.md) | **Design.** OWASP/NIST research, contract API, AES-256-GCM defaults, key lifecycle, rotation strategies, Spring starter, integrations. |
| [`v1-actions/00-plan-overview.md`](v1-actions/00-plan-overview.md) | **Execution plan.** Modules, task graph, conventions, definition of done. |
| [`v1-actions/`](v1-actions/) | Agent-executable tasks CRYPTO-01 … CRYPTO-06. |
| [`RELEASE-NOTES-v1.md`](RELEASE-NOTES-v1.md) | v1.0.0 release notes, migration, known gaps. |

**Status:** v1.0.0 implemented and verified — `khezy-crypto`,
`crypto-spring-boot-starter`, and the `api-security` compatibility adapter.
`a2a-store-jpa` integration is deferred until that module exists.

## One-line summary

A Spring-free utility that makes **encryption at rest** and **key rotation** a shared,
safe default: AES-256-GCM, versioned `keyId` ciphertext, pluggable key providers
(properties / JDBC / Vault / KMS / HSM), gradual or eager rotation, and a guarded
key-destruction lifecycle.

## Start here

1. Read `crypto-library-design.md` (§1 for OWASP/NIST rationale, §4 for the API, §5 for
   rotation).
2. Read `v1-actions/00-plan-overview.md`.
3. Execute tasks in order, appending a handoff entry after each.

## Consumers in this repo

- `rest-api/api-security` — legacy `CustomEncryptor` becomes a compatibility adapter.
- Any future KHEZY module or external project — depend on
  `io.github.khezyapp:khezy-crypto`.
- `ai/a2a/a2a-store-jpa` — **deferred** until that module exists (A2A task
  `A2A2-05`); the intended adoption is documented in the design §8.2.
