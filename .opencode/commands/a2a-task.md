---
description: "Execute one A2A v2 plan task in this fresh session (usage: /a2a-task 01)"
---

You are executing **exactly one** task from the KHEZY A2A v2 plan: `A2A2-$ARGUMENTS`.

Work from `/mnt/data/khezylib/khezy-boot`. Do not start any other task.

## Read only this context (in order)

1. `scrum/a2a/v2-actions/00-plan-overview.md` — master context, conventions, task graph.
2. `scrum/a2a/v2-actions/$ARGUMENTS-*.md` — the task file (glob to resolve its name).
3. Only the sections of `scrum/a2a/a2a-design-v2.md` cited by that task file. Do not read
   the whole design.
4. `scrum/a2a/v2-actions/99-handoff-log.md` — the **last entry for each prerequisite
   task**, plus its "Deviations" and "Gotchas for next tasks".

Then inspect only the source files the task file tells you to touch or create. Do not
re-explore the repository, the archived v1 plan, or unrelated modules.

## Execute

- Follow the shared conventions in `00-plan-overview.md` §6 and `AGENTS.md`.
- Respect the task's scope exactly; no opportunistic refactors.
- Run every verification command in the task file. Fix failures until green.
- When done, append a new entry to `scrum/a2a/v2-actions/99-handoff-log.md` using the
  template at the top of that file, and update its Progress table row.
- Run `graphify update .`.

## Reply

Reply with one line only: `<task id> <completed|partial> — <verification result>`.
If blocked, write the `partial` handoff entry and reply
`<task id> BLOCKED — <one-sentence reason>`.
