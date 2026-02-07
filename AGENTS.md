# AGENTS.md — How to work in this repo (for coding agents + humans)

This file is the repo’s **always-on working agreement**. If you are an agent, follow it by default unless a task explicitly overrides it.

## North Star

- **Make small, reviewable changes.** Prefer tiny diffs that are easy to reason about.
- **Keep workflows repeatable.** If a step matters, document it or script it.
- **Don’t leak secrets.** Never commit credentials, tokens, API keys, or shared test accounts.

## Issues and PRs (format)

Keep issues/PRs concise to reduce always-on context usage.

For detailed guidance and examples, see:

- `docs/issues-and-prs.md`

## Read Before You Act

Before changing anything, locate the source of truth:

1. **Docs / run commands**
   - `README.md`
   - `scripts/` (preferred entrypoints when present)
2. **Build configuration**
   - `shadow-cljs-dev.edn` (browser dev/build config)
   - `electron/` (Electron runtime/packaging)
   - `package.json` (JS deps)
   - `deps.edn` (Clojure/ClojureScript deps + aliases)

If a task involves build output paths, **read the relevant config first** and avoid “guess changes”.

## Repo Map (high level)

This section is a convenience only. If it gets out of date, trust the repo tree.

- `src/` — ClojureScript source
- `scripts/` — helper scripts / dev workflows
- `resources/` — static assets (and/or build outputs depending on config)
- `electron/` — Electron packaging/runtime
- `shadow-cljs-dev.edn` — build configuration
- `package.json` / lockfile — JS dependencies
- `deps.edn` — Clojure/ClojureScript deps & aliases

## Default Workflow Expectations

- Prefer a **single obvious command** for common tasks (dev, build, electron). If it doesn’t exist, propose it.
- When updating tooling/docs, keep backward compatibility unless the task is explicitly “upgrade X”.
- Use existing scripts where possible; improve them rather than adding new one-off commands.

## Hard Rules (agents)

- **No dependency upgrades**, formatting sweeps, or refactor-only changes unless explicitly requested.
- Avoid touching unrelated files “while you’re there”. If you must, explain why.

## Change Hygiene

- **One concern per PR/commit** where possible (docs-only, build-only, UI-only).
- Avoid drive-by refactors. If you touch unrelated code, explain why.
- Always include:
  - what problem you’re solving
  - what changed
  - how to verify

## Verification (minimum bar)

For any change that affects build/runtime behavior, provide at least one:

- A command that should succeed (e.g. a release compilation step), or
- A short manual checklist (what to click, what should happen).

If tests don’t exist, don’t invent fake ones. Write “How to verify” instead.

## Dependency Policy

- Don’t add dependencies unless necessary.
- Prefer small, well-supported libraries.
- If you add a dependency, note briefly:
  - why existing deps aren’t enough
  - any bundle size/security considerations

## Build Output Safety

- Build outputs may be configured to write outside the repo.
- **Do not change output paths casually.** If you must change them:
  - document the impact
  - provide a migration note

## Documentation Standards

- README must not contain credentials or shared “test accounts”.
- Keep docs concise and actionable:
  - requirements
  - commands
  - troubleshooting bullets

## Communication / Outputs

When responding in PRs/issues or proposing changes:

- Prefer bullet points.
- Include exact commands and file paths.
- End with a short **How to verify** section.

## Write It Down

If you discover a recurring pitfall (build steps, output-path gotchas, Electron packaging quirks), add 1–3 actionable lines to the most relevant doc.

## When in Doubt

- Ask for clarification on expected behavior and supported environments.
- Default to the smallest safe improvement that’s easy to review.
