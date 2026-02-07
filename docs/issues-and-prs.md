# Issues and PRs

This document defines the recommended format for issues and pull requests in this repo.

The goal is to make collaboration easy **without bloating always-on context** (see `AGENTS.md`).

## Issues

### Bug report

Please include:

- **Title**: concise and specific (what broke)
- **Summary**: 2–5 sentences
- **Steps to reproduce**: numbered list
- **Expected behavior**
- **Actual behavior**
- **Environment**: OS, browser/runtime, versions (Node, Electron, etc.) if relevant
- **Logs / screenshots**: redact secrets

### Feature request

Please include:

- **Problem / motivation**
- **Proposed solution (optional)**
- **Alternatives considered (optional)**
- **Scope / constraints**
- **Acceptance criteria**

## Pull Requests

Please include:

- **What / why**: what problem this PR solves
- **Scope**: keep it small; one concern per PR when possible
- **Key changes**: bullets + pointers to important files
- **How to verify**: exact commands and/or a short manual checklist
- **Risk notes**: compatibility, migrations, rollout concerns

## Terminal posting (avoid broken Markdown)

If you create/edit issues or PR descriptions from a terminal:

- Prefer **real multiline input** (file or heredoc) rather than embedding literal `"\n"` sequences.
- Preview formatting before posting when possible.

Examples:

```bash
# Use a file
gh issue create --body-file /path/to/body.md

# Or use a heredoc
gh issue create --body-file - <<'EOF'
## Title
...
EOF
```
