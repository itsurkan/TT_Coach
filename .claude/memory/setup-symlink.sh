#!/usr/bin/env bash
# Re-establish the Claude Code memory symlink on a fresh machine.
#
# Claude Code reads project memory from a per-project dir under ~/.claude/projects/.
# This repo keeps the canonical memory files in .claude/memory/ (version-controlled)
# and points the harness dir at them via a symlink so auto-memory writes land in-repo.
#
# Run once after cloning:  bash .claude/memory/setup-symlink.sh
set -euo pipefail

REPO_MEMORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$REPO_MEMORY/../.." && pwd)"

# Claude Code encodes the project path by replacing / _ . with -
ENCODED="$(printf '%s' "$REPO_ROOT" | sed 's/[/_.]/-/g')"
HARNESS="$HOME/.claude/projects/$ENCODED/memory"

mkdir -p "$(dirname "$HARNESS")"

if [ -L "$HARNESS" ]; then
  echo "Symlink already exists: $HARNESS -> $(readlink "$HARNESS")"
  exit 0
fi

if [ -e "$HARNESS" ]; then
  echo "WARNING: $HARNESS exists and is not a symlink."
  echo "Move any unique files into $REPO_MEMORY, remove the dir, then re-run."
  exit 1
fi

ln -s "$REPO_MEMORY" "$HARNESS"
echo "Linked: $HARNESS -> $REPO_MEMORY"
