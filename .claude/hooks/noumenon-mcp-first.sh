#!/usr/bin/env bash
# PreToolUse hook: enforce MCP-first policy.
# Blocks Read/Glob/Grep until at least one noumenon MCP tool has been called.
set -euo pipefail

INPUT=$(cat)

TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty')
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty' | tr -dc 'a-zA-Z0-9_-' | head -c 128)
TRANSCRIPT=$(echo "$INPUT" | jq -r '.transcript_path // empty')

[ -z "$TOOL_NAME" ] && exit 0
[ -z "$SESSION_ID" ] && exit 0

case "$TOOL_NAME" in
    Read|Glob|Grep) ;;
    *) exit 0 ;;
esac

STATE_DIR="${XDG_RUNTIME_DIR:-${HOME}/.noumenon/tmp}/mcp-sessions"
STATE_FILE="$STATE_DIR/$SESSION_ID"

[ -f "$STATE_FILE" ] && exit 0

if [ -n "$TRANSCRIPT" ] && [ -f "$TRANSCRIPT" ]; then
    if grep -q -m1 "mcp__noumenon__" "$TRANSCRIPT" 2>/dev/null; then
        mkdir -p "$STATE_DIR"
        touch "$STATE_FILE"
        exit 0
    fi
fi

cat >&2 <<'JSON'
{"hookSpecificOutput":{"permissionDecision":"deny","additionalContext":"BLOCKED: Query the Noumenon knowledge graph BEFORE reading files. Call noumenon_status, noumenon_query, or noumenon_ask first. See CLAUDE.md for the required workflow."}}
JSON
exit 2
