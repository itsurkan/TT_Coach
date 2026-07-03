# Claude project memory (version-controlled)

These are the persistent memory files Claude Code uses for this project. They live
in the repo (instead of the default `~/.claude/projects/.../memory/`) so they travel
with `git push` and are available on every machine you clone to.

- `MEMORY.md` — the index loaded into Claude's context each session (one line per memory).
- `*.md` — one fact per file (user prefs, feedback, project direction, references).
- `project_git_push_auth.md` — **local-only, gitignored** (contains account-specific details).

## How it works

The Claude Code harness reads memory from a fixed path under `~/.claude/projects/`.
That path is a **symlink** into this directory, so auto-memory writes land here and
get committed like any other change.

## After cloning on a new machine

The symlink is machine-local and is not part of the repo. Recreate it once:

```bash
bash .claude/memory/setup-symlink.sh
```

Then restart Claude Code so it picks up the linked memory.
