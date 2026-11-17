#!/usr/bin/env python3
"""Summarise PIT's mutations.xml: score, per-class table, surviving mutants.

usage: mutation-report.py <mutations.xml> <threshold%> <out-markdown> <out-survivors-markdown>
Writes step outputs: score, total, killed, survived, passed (true/false).
Score = detected (KILLED/TIMED_OUT/MEMORY_ERROR...) / all mutants, like PIT's own "mutation score".
"""
import os
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

xml_path, threshold, out_md, out_survivors = sys.argv[1], float(sys.argv[2]), sys.argv[3], sys.argv[4]
muts = ET.parse(xml_path).getroot().findall("mutation")

per_class = defaultdict(lambda: {"total": 0, "killed": 0, "survived": 0, "no_cov": 0})
survivors = []
for m in muts:
    cls = m.findtext("mutatedClass", "")
    status = m.get("status", "")
    detected = m.get("detected") == "true"
    c = per_class[cls]
    c["total"] += 1
    if detected:
        c["killed"] += 1
    elif status == "NO_COVERAGE":
        c["no_cov"] += 1
    else:
        c["survived"] += 1
    if not detected:
        survivors.append({
            "class": cls,
            "file": m.findtext("sourceFile", ""),
            "line": m.findtext("lineNumber", ""),
            "method": m.findtext("mutatedMethod", ""),
            "mutator": m.findtext("mutator", "").rsplit(".", 1)[-1],
            "description": m.findtext("description", ""),
            "status": status,
        })

total = len(muts)
killed = sum(c["killed"] for c in per_class.values())
score = round(100.0 * killed / total, 1) if total else 100.0
passed = score >= threshold

lines = [
    f"**Mutation score: {score}%** ({killed}/{total} mutants killed) · threshold {threshold:g}%",
    "",
    "| Class | Mutants | Killed | Survived | No coverage | Score |",
    "|---|---:|---:|---:|---:|---:|",
]
for cls, c in sorted(per_class.items()):
    s = round(100.0 * c["killed"] / c["total"], 1) if c["total"] else 100.0
    lines.append(f"| `{cls.rsplit('.', 1)[-1]}` | {c['total']} | {c['killed']} | {c['survived']} | {c['no_cov']} | {s}% |")
open(out_md, "w").write("\n".join(lines) + "\n")

srv = ["| # | Location | Method | Mutation | Status |", "|---|---|---|---|---|"]
for i, s in enumerate(survivors, 1):
    srv.append(f"| S{i} | `{s['file']}:{s['line']}` | `{s['method']}` | {s['description']} | {s['status']} |")
open(out_survivors, "w").write("\n".join(srv) + "\n" if survivors else "No surviving mutants. 🎉\n")

with open(os.environ["GITHUB_OUTPUT"], "a") as fh:
    fh.write(f"score={score}\ntotal={total}\nkilled={killed}\nsurvived={total - killed}\npassed={'true' if passed else 'false'}\n")
print(f"Mutation score {score}% ({killed}/{total}), threshold {threshold}% -> {'PASS' if passed else 'FAIL'}")
