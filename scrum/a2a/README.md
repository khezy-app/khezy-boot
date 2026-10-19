# KHEZY A2A — Design & Plan Index

| Document | Purpose |
|---|---|
| [`a2a-design-v2.md`](a2a-design-v2.md) | **Current design.** SDK-first A2A layer: server seam, client ergonomics, JPA + encryption, Spring AI tooling. |
| [`sdk-1.4-adoption.md`](sdk-1.4-adoption.md) | **SDK 1.4 research.** What changed since 1.3.1 and what we adopt (multi-tenancy, SSE config, HTTP client SPI, push semantics, hooks, OTel, TCK). |
| [`v2-actions/00-plan-overview.md`](v2-actions/00-plan-overview.md) | **Current execution plan.** Verified SDK 1.4 facts, module layout, task graph, cleanup manifest. |
| [`v2-actions/`](v2-actions/) | Agent-executable tasks A2A2-01 … A2A2-09. |
| [`_archive-v1/`](_archive-v1/) | Superseded v1 design and action plan. Historical only — do not implement. |

## One-line difference

- **v1:** re-implemented the A2A protocol (dispatcher, stores, JSON-RPC controllers) on
  top of the SDK types.
- **v2:** keeps the SDK runtime intact and adds Spring assembly, a business-logic seam,
  encryption at rest, client ergonomics, and LLM tooling.

## Related

- Encryption at rest (payload stores, key rotation) is provided by the shared
  [`scrum/crypto`](../crypto/crypto-library-design.md) utility. The A2A store consumes
  `StringEncryptor`; it does not implement its own cipher.

## Start here

1. Read `a2a-design-v2.md`.
2. Read `v2-actions/00-plan-overview.md`.
3. Execute tasks in order, appending a handoff entry to
   `v2-actions/99-handoff-log.md` after each.

## Automated execution

Each task runs in its own fresh session and hands off through the log — never through
chat history. This keeps token cost flat regardless of how many tasks remain.

```bash
# one task, current session (interactive)
/a2a-task 01

# all remaining tasks, one fresh opencode process per task
.opencode/scripts/a2a-run.sh 01 09

# inspect the overlap-free context each task receives
A2A_DRY_RUN=1 .opencode/scripts/a2a-run.sh 01 01
```

The runner skips tasks already marked `completed`, stops on the first failed or `partial`
task, and writes per-task logs to `/tmp/khezy-a2a-runs`.
