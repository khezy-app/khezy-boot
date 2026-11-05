#!/usr/bin/env bash
#
# Run the KHEZY A2A v2 plan one task per fresh opencode session.
#
# Each invocation of `opencode run` gets a new context window, reads only the master
# context, its own task file, and the handoff log, then writes its handoff entry back
# to the log. The loop stops on the first failed or blocked task.
#
# Usage:
#   .opencode/scripts/a2a-run.sh [start] [end]
#   .opencode/scripts/a2a-run.sh 01 06
#
# Environment:
#   A2A_MODEL    optional opencode model (provider/model#variant)
#   A2A_AGENT    optional agent name (default: opencode's default build agent)
#   A2A_LOG_DIR  optional run-log directory (default: /tmp/khezy-a2a-runs)
#   A2A_DRY_RUN  set to 1 to print prompts without running
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

START="${1:-01}"
END="${2:-09}"
LOG_DIR="${A2A_LOG_DIR:-/tmp/khezy-a2a-runs}"
HANDOFF="scrum/a2a/v2-actions/99-handoff-log.md"

[[ -f "$HANDOFF" ]] || { echo "missing $HANDOFF" >&2; exit 1; }
mkdir -p "$LOG_DIR"

MODEL_ARGS=()
[[ -n "${A2A_MODEL:-}" ]] && MODEL_ARGS=(--model "$A2A_MODEL")
AGENT_ARGS=()
[[ -n "${A2A_AGENT:-}" ]] && AGENT_ARGS=(--agent "$A2A_AGENT")

task_file() {
    compgen -G "scrum/a2a/v2-actions/${1}-*.md" | head -1
}

is_done() {
    local id="A2A2-$1"
    awk -v id="$id" '
        $0 ~ "^## " id " " { in_entry = 1; status = ""; next }
        in_entry && /^- \*\*Status:\*\*/ { status = $0; exit }
        END { exit (status ~ /completed/ && status !~ /partial/) ? 0 : 1 }
    ' "$HANDOFF"
}

prompt_for() {
    local num="$1" file="$2"
    cat <<EOF
You are executing exactly one task from the KHEZY A2A v2 plan: A2A2-$num.

Work from $ROOT. Do not start any other task.

Read only this context, in order:
1. scrum/a2a/v2-actions/00-plan-overview.md (master context, conventions, task graph).
2. $file (your task file).
3. Only the sections of scrum/a2a/a2a-design-v2.md cited by that task file. Do not read
   the whole design.
4. scrum/a2a/v2-actions/99-handoff-log.md: the last entry for each prerequisite task,
   plus its Deviations and Gotchas for next tasks.

Then inspect only the source files the task file tells you to touch or create. Do not
re-explore the repository, the archived v1 plan, or unrelated modules.

Execute the task:
- Follow the shared conventions in 00-plan-overview.md section 6 and AGENTS.md.
- Respect the task scope exactly; no opportunistic refactors.
- Run every verification command in the task file. Fix failures until green.
- Append a new entry to scrum/a2a/v2-actions/99-handoff-log.md using the template at the
  top of that file, and update its Progress table row.
- Run: graphify update .

Reply with one line only: "A2A2-$num <completed|partial> - <verification result>".
If blocked, write the partial handoff entry and reply "A2A2-$num BLOCKED - <reason>".
EOF
}

for n in $(seq -f "%02g" "$((10#$START))" "$((10#$END))"); do
    id="A2A2-$n"
    file="$(task_file "$n")"
    [[ -n "$file" ]] || { echo "no task file for $id" >&2; exit 1; }

    if is_done "$n"; then
        echo "== $id already completed - skipping"
        continue
    fi

    prompt="$(prompt_for "$n" "$file")"
    echo "== $id starting ($file)"

    if [[ "${A2A_DRY_RUN:-0}" == "1" ]]; then
        echo "$prompt"
        continue
    fi

    if ! opencode run --auto --title "$id" "${MODEL_ARGS[@]}" "${AGENT_ARGS[@]}" \
            "$prompt" 2>&1 | tee "$LOG_DIR/$id.log"; then
        echo "== $id FAILED (opencode error); logs: $LOG_DIR/$id.log" >&2
        exit 1
    fi

    if ! is_done "$n"; then
        echo "== $id STOPPED: no 'completed' handoff entry written." >&2
        echo "   Inspect $LOG_DIR/$id.log and the tail of $HANDOFF." >&2
        exit 1
    fi

    echo "== $id completed"
done

echo "== done (logs: $LOG_DIR)"
