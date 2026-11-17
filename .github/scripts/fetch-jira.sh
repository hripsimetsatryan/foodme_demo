#!/usr/bin/env bash
# Find the Jira key (e.g. TEST-11) in the PR branch name or title and download the ticket.
# Writes .jira/ticket.json and sets step outputs: key, found (true/false), reason.
# env: JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN, HEAD_REF, PR_TITLE
set -uo pipefail
mkdir -p .jira
key="$(printf '%s\n%s\n' "$HEAD_REF" "$PR_TITLE" | grep -oE '[A-Z][A-Z0-9]+-[0-9]+' | head -n1 || true)"
echo "key=$key" >> "$GITHUB_OUTPUT"
if [ -z "$key" ]; then
  echo "found=false" >> "$GITHUB_OUTPUT"
  echo "reason=No Jira key (like TEST-11) in the branch name or PR title." >> "$GITHUB_OUTPUT"
  exit 0
fi
if [ -z "${JIRA_EMAIL:-}" ] || [ -z "${JIRA_API_TOKEN:-}" ]; then
  echo "found=false" >> "$GITHUB_OUTPUT"
  echo "reason=JIRA_EMAIL / JIRA_API_TOKEN repository secrets are not set." >> "$GITHUB_OUTPUT"
  exit 0
fi
status="$(curl -sS -o .jira/ticket.json -w '%{http_code}' -u "$JIRA_EMAIL:$JIRA_API_TOKEN" \
  -H 'Accept: application/json' \
  "$JIRA_BASE_URL/rest/api/2/issue/$key?fields=summary,description,status,issuetype,labels")"
if [ "$status" != "200" ]; then
  echo "found=false" >> "$GITHUB_OUTPUT"
  echo "reason=Jira returned HTTP $status for $key." >> "$GITHUB_OUTPUT"
  exit 0
fi
echo "found=true" >> "$GITHUB_OUTPUT"
echo "Fetched $key: $(jq -r '.fields.summary' .jira/ticket.json)"
