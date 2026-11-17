#!/usr/bin/env python3
"""Turn Claude's structured review into a PR comment + a pass/fail quality gate.

usage: claude-gate.py <title> <comment-out-file>
env:   RESULT  - the claude-code-action `structured_output` (JSON, see .github/claude/review-schema.json)
       DETAILS_URL - link to this workflow run

The gate is decided here, from the data, not from Claude's prose:
it FAILS when there is any finding with severity "high", or Claude's verdict is FAIL,
or Claude returned nothing usable (fail closed).
"""
import json
import os
import sys

title, out_path = sys.argv[1], sys.argv[2]
details = os.environ.get("DETAILS_URL", "")
raw = os.environ.get("RESULT", "").strip()

try:
    data = json.loads(raw) if raw else None
except json.JSONDecodeError:
    data = None

if not isinstance(data, dict):
    body = (f"## {title}\n\n**Quality gate: ❌ FAIL**: Claude did not return a review result.\n\n"
            f"Check the [workflow run]({details}) log.")
    open(out_path, "w").write(body)
    print("::error::Claude did not return a structured result")
    sys.exit(1)

findings = data.get("findings") or []
count = {s: sum(1 for f in findings if str(f.get("severity", "")).lower() == s) for s in ("high", "medium", "low")}
# fail closed: anything other than an explicit PASS (missing/odd verdict) fails too
failed = count["high"] > 0 or data.get("verdict") != "PASS"

status = "❌ FAIL" if failed else "✅ PASS"
header = (f"**Quality gate: {status}** · {count['high']} high · {count['medium']} medium · {count['low']} low"
          f"{'  ·  merge blocked until high findings are fixed' if failed else ''}")
body = data.get("comment_markdown") or data.get("summary") or ""
footer = f"\n\n---\n<sub>Gate rule: any **high** finding fails this check. [Workflow run]({details})</sub>"
open(out_path, "w").write(f"{header}\n\n{body}{footer}")

summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
if summary_file:
    with open(summary_file, "a") as fh:
        fh.write(f"## {title}\n\n{header}\n\n{data.get('summary', '')}\n")

for f in findings:
    if str(f.get("severity", "")).lower() == "high":
        print(f"::error title={f.get('id', '')} {f.get('title', '')}::{f.get('location', '')} {f.get('detail', '')}")

sys.exit(1 if failed else 0)
