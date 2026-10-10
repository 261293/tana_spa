# Third-party agent skills — provenance and licensing

Most skills in this directory are vendored from upstream projects. Both upstream licenses require
that the license text and attribution travel with the copies, which is what this file and the
bundled license files do. Do not strip them.

**First-party, not vendored** — written for this repo and covered by its own license:
`kmp-compose-multiplatform`, `self-documenting-code`, `calibrated-claims`, `no-ai-attribution`.

## Sources

| Source | License | Vendored at | License text |
|---|---|---|---|
| [anthropics/skills](https://github.com/anthropics/skills) | Apache 2.0 | `dbd4588`, 2026-10-09 | `<skill>/LICENSE.txt` |
| [borghei/Claude-Skills](https://github.com/borghei/Claude-Skills) | MIT + Commons Clause | `4a698e8`, 2026-10-07 | `LICENSE-borghei-claude-skills.txt` |
| [terrakok/kmp-awesome](https://github.com/terrakok/kmp-awesome) | CC0-1.0 | 2026-09-30 | distilled, not copied — see below |

The Commons Clause forbids *selling* the borghei skills — that is, offering a product or service
whose value derives substantially from them. Using them internally to build this app is fine;
redistributing them as part of a paid developer-tooling offering is not.

## From anthropics/skills (Apache 2.0)

- `frontend-design` — visual design direction, typography, avoiding templated defaults
- `mcp-builder` — building MCP servers (Python FastMCP, Node/TypeScript SDK)

Chosen over the equivalent community skills because MCP is Anthropic's own specification and
the official skill tracks it.

## From borghei/Claude-Skills (MIT + Commons Clause)

| Skill | Why it is here |
|---|---|
| `security-audit` | language-agnostic source audit: attack classes, web/auth, mobile/IPC, supply chain |
| `env-secrets-manager` | this server is env-driven — `JWT_SECRET`, `DATABASE_URL`, `TWILIO_*` |
| `pr-review-expert` | blast-radius review for changes to `shared/`, auth and migrations |
| `spec-driven-workflow` | requirement IDs and merge-time coverage gates |
| `focused-fix` | scoping a minimal bugfix |
| `doc-drift-detector` | this repo has known stale docs (README module list, server port) |
| `feature-flags-architect` | flag taxonomy, rollout, kill switches, flag debt |
| `migration-architect` | zero-downtime Flyway/Postgres migrations |
| `database-schema-designer` | Postgres schema, indexes, multi-tenancy |
| `design-auditor` | UI review: contrast, design tokens, breakpoints |
| `apple-hig-expert` | iOS expectations for the iOS target |
| `ui-design-system` | design tokens, component docs, developer handoff |

Their bundled Python scripts target Python/TS/JS/Go/Rust ecosystems and will not parse Kotlin or
Gradle. The **methodology** in each `SKILL.md` and `references/` is what carries value here; treat
the scripts as optional and verify any that you run.

### Deliberately not vendored

`dependency-auditor` (reads `package.json`/`poetry.lock`/`go.mod`; Gradle is a roadmap item —
use Dependabot or an OWASP Gradle plugin instead), `monorepo-navigator` (Turborepo/Nx/pnpm),
`performance-profiler` (Node/Python/Go), `senior-frontend` (React/Next/Tailwind), `a11y-audit`
(scans HTML, but Compose web renders to a canvas), `senior-mobile` (overlaps
`kmp-compose-multiplatform`, leans React Native), `release-orchestrator` and
`devops-workflow-engineer` (duplicate the installed `release-manager` and `ci-cd-pipeline-builder`),
`observability-designer` (no metrics stack yet), `codebase-onboarding` (docs already cover it),
`api-design-reviewer` (OpenAPI-centric; this project's contract is Kotlin in `shared/`).

Upstream agents under `.claude/agents/` were also skipped: `git-workflow` enforces conventional
commits, which contradicts this repo's commit style and the attribution rule in `CLAUDE.md`, and
`doc-generator` writes narrating docs against the `self-documenting-code` rule. The rest duplicate
built-in `/code-review` and `/security-review`.

## From terrakok/kmp-awesome (CC0-1.0)

Not vendored. It is a curated link index, so it contains no rules or templates to copy. Its
contents were distilled into two project-specific references instead:

- `kmp-compose-multiplatform/references/libraries.md` — library shortlist filtered to this
  project's five targets, with the already-chosen stack marked as not-to-replace
- `kmp-compose-multiplatform/references/ui-design.md` — token rules, per-target constraints,
  responsive breakpoints, WCAG 2.2 checks, screen templates

## Local modifications

- `.claude/hooks/pre_tool_secret_scan.py` (from borghei `.claude/hooks/`): the tool-name guard was
  widened from `"Bash"` to `("Bash", "PowerShell")`, since PowerShell is the primary shell on the
  development machines here and would otherwise bypass the scan. Registered as a `PreToolUse` hook
  in `.claude/settings.json`, run with `python -I` so the script's own directory cannot be used to
  shadow a stdlib import. It needs `python` on `PATH`; if absent the hook reports a failure and the
  command proceeds, so a teammate without Python is warned rather than blocked.

## Updating

These are vendored copies, not submodules — they do not update themselves. To refresh one, re-copy
it from the upstream commit, re-read the diff, and update the SHA in the table above.
