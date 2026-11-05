# KHEZY A2A — documentation index

| Document | Purpose |
|---|---|
| [Design](a2a-design-v2.md) | Architecture and module map |
| [Action plan](v2-actions/00-plan-overview.md) | Task graph and conventions |
| [Handoff log](v2-actions/99-handoff-log.md) | Per-task status and lessons |
| [SDK 1.4 adoption notes](sdk-1.4-adoption.md) | Reference |

Start with the design, then the action plan. Every task handoff lives in the log, not in
chat.

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
