#!/usr/bin/env bash
# Create or update ONE comment per gate on the PR, found by a hidden marker,
# so each push edits the same comment instead of spamming new ones.
# usage: upsert-pr-comment.sh <marker-name> <body-file>
# env:   GH_TOKEN, GITHUB_REPOSITORY, PR_NUMBER
set -euo pipefail
marker="<!-- claude-gate:$1 -->"
body="$(printf '%s\n%s\n' "$marker" "$(cat "$2")")"
id="$(gh api "repos/$GITHUB_REPOSITORY/issues/$PR_NUMBER/comments" --paginate \
  --jq ".[] | select(.body | contains(\"$marker\")) | .id" | tail -n1)"
if [ -n "$id" ]; then
  gh api -X PATCH "repos/$GITHUB_REPOSITORY/issues/comments/$id" -f body="$body" > /dev/null
else
  gh api -X POST "repos/$GITHUB_REPOSITORY/issues/$PR_NUMBER/comments" -f body="$body" > /dev/null
fi
