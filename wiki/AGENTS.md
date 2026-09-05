# AGENTS — Project Wiki Schema

This document describes the structure, conventions, and workflows for the project wiki.
The wiki lives at `<project-root>/wiki/` and is version-controlled alongside the code.
This file is generated from the `project-wiki` skill and mirrors its conventions exactly.

## Directory Structure

```
wiki/
├── AGENTS.md                  # This file — wiki schema and agent instructions
├── index.md                   # Catalog of all pages, organized by category
├── log.md                     # Chronological, append-only log of all operations
├── pages/
│   ├── project-overview.md    # Tech stack, architecture, folder structure, key concepts
│   ├── build-deploy.md        # Build, test, lint, and deploy instructions
│   ├── specs/                 # Feature specifications (pre-implementation)
│   │   └── 01-<feature-slug>.md
│   ├── plans/                 # Implementation plans (pre-implementation)
│   │   └── 01-<feature-slug>.md
│   ├── tickets/               # Post-implementation summaries
│   │   └── 01-<ticket-slug>.md
│   └── research/              # Research, analysis, investigation pages
│       └── 01-<topic-slug>.md
```

## Development Workflow

The wiki supports the full lifecycle, replacing `.design-specs/` and `.opencode/plans/`:

1. **Spec** — define what to build (requirements, scope, design, constraints)
2. **Plan** — define how to build it (steps, files, migrations, tests)
3. **Implement** — do the work (outside the wiki)
4. **Ticket** — document what was done (summary, decisions, files changed)

Spec and plan pages share the same slug so they cross-link naturally (e.g., `specs/01-dual-price-feature.md` ↔ `plans/01-dual-price-feature.md`). The ticket page links back to both.

## Page Conventions

Every page MUST have YAML frontmatter:

```yaml
---
created: YYYY-MM-DD
type: overview | build-deploy | spec | plan | ticket | research
tags: [tag1, tag2]
related:
  - "../plans/01-dual-price-feature.md"
---
```

- **`created`** (required): ISO 8601 date. Never changes after page creation — this preserves history.
- **`type`** (required): Page category.
- **`tags`** (optional): Freeform tags for cross-referencing and searching.
- **`related`** (optional): Relative markdown paths to connected pages, as a YAML list. Every path MUST be a **quoted string**.

> **DO NOT use wikilinks in frontmatter.** A value starting with `[` is parsed as a
> YAML flow sequence and breaks the page (VS Code: `Failed to parse frontmatter —
> Unexpected flow-seq-start`):
>
> ```yaml
> # WRONG — triggers "Unexpected flow-seq-start"
> related: [[specs/dual-price-feature]] [[plans/dual-price-feature]]
>
> # CORRECT — quoted strings, YAML block list
> related:
>   - "../specs/01-dual-price-feature.md"
>   - "../plans/01-dual-price-feature.md"
> ```

### Links (clickable in VS Code)

Use **relative markdown links** — never wikilinks. Paths are always relative to the file that contains the link:

| Current file | Target file | Link |
|---|---|---|
| `wiki/index.md` | any page | `pages/<folder>/01-<slug>.md` |
| `wiki/pages/<sub>/x.md` | `wiki/pages/<other>/y.md` | `../<other>/01-y.md` |
| `wiki/pages/<sub>/x.md` | `wiki/pages/root-page.md` | `../root-page.md` |
| `wiki/pages/root-page.md` | `wiki/pages/<folder>/01-y.md` | `<folder>/01-y.md` |

When editing an existing page that still uses wikilinks, convert them to relative markdown links and fix the `related` frontmatter. When updating a page, append changes under an `## Updates` section — never alter the `created` date.

### Sequence numbering

Pages in `specs/`, `plans/`, `tickets/`, and `research/` get a **numeric filename prefix** (`01-`, `02-`, …) so each folder reads in order and sorts naturally:

- Numbers are assigned by **creation order** within the folder and are **permanent** — new pages get the next free number, never renumber existing ones.
- Filenames: `pages/plans/02-category-emojis.md`.
- The H1 headline includes the number too: `# 2. Category Emojis — Implementation Plan`.
- Root pages (`project-overview.md`, `build-deploy.md`) get **no** prefix.

## Operation Workflows

### Creating a Spec Page

When starting a new feature:
1. Create `pages/specs/01-<feature-slug>.md` (next free number, e.g., `01-dual-price-feature.md`).
2. Content: feature name, summary, requirements, scope, design decisions, constraints, UI/UX.
3. Add YAML frontmatter with `type: spec`, `created` date, `related` as a quoted list: `- "../plans/01-<same-slug>.md"`.
4. Add entry to `index.md` under `## Specs`.
5. Append to `log.md`: `## [YYYY-MM-DD] spec | <feature-name>`.

### Creating a Plan Page

Before implementing (after spec is created):
1. Create `pages/plans/01-<feature-slug>.md` — same slug as the spec, next free number.
2. Content: link to spec, step-by-step plan, files to touch, DB migrations, test checklist, risk assessment.
3. Add YAML frontmatter with `type: plan`, `created` date, `related` as a quoted list linking to `../specs/01-<same-slug>.md`.
4. Add entry to `index.md` under `## Plans`.
5. Append to `log.md`: `## [YYYY-MM-DD] plan | <feature-name>`.

### Creating a Ticket Page

After implementation:
1. Create `pages/tickets/01-<slug>.md` (e.g., `01-issue-11-dual-price.md`).
2. Content: ticket ID, title, what was implemented, deviations from plan, decisions, files touched. Link to spec and plan pages.
3. Add YAML frontmatter with `type: ticket`, current `created` date, quoted `related` list linking spec and plan.
4. Add entry to `index.md` under `## Tickets`.
5. Append to `log.md`: `## [YYYY-MM-DD] ticket | <title>`.

### Creating a Research Page

When completing research/analysis:
1. Create `pages/research/01-<slug>.md` (e.g., `01-kmp-feasibility.md`).
2. Content: topic, context, findings, conclusions, recommended actions.
3. Add YAML frontmatter with `type: research` and the current `created` date.
4. Add entry to `index.md` under `## Research`.
5. Append to `log.md`: `## [YYYY-MM-DD] research | <title>`.

### Updating a Page

When knowledge changes:
1. Edit page content directly.
2. Append `## Updates` section with dated bullets: `- [YYYY-MM-DD]: Description of change.`
3. Never change `created` date.
4. Append to `log.md`: `## [YYYY-MM-DD] update | <page> — <note>`.

### Query

When asked about the project:
1. Read `index.md` first to locate relevant pages.
2. Drill into specific pages.
3. Synthesize the answer with relative markdown link citations (from the project root when answering outside the wiki).
4. Offer to file new knowledge as a research page.

### Lint

Periodically check:
- Orphan pages (not linked from `index.md` or other pages).
- Missing frontmatter (`created`, `type`).
- Frontmatter that fails YAML parsing — unquoted wikilinks in `related`.
- Dangling links — links or `related:` entries whose target file does not exist.
- Leftover wikilinks `[[…]]` anywhere.
- Missing or out-of-order sequence numbers.
- Specs without a corresponding plan (warning only; standalone plans are allowed).
- Stale content (outdated commands, removed files, old versions).
- Missing cross-references in spec → plan → ticket chains.
- Suggest new pages for undocumented topics.

## Slug Conventions

- Specs and plans: `<feature-name>` (e.g., `dual-price-feature`). Same slug for both so they cross-link. 2-5 lowercase hyphen-separated words. Filenames carry a numeric prefix: `01-dual-price-feature.md`.
- Tickets: `issue-<number>-<brief>` (e.g., `issue-11-dual-price`). 2-4 lowercase hyphen-separated words.
- Research: `<topic>` (e.g., `kmp-feasibility`). Natural topic name, lowercase, hyphens.

## log.md Format

All entries: `## [YYYY-MM-DD] <action> | <description>`
Valid actions: `init`, `spec`, `plan`, `ticket`, `research`, `update`, `lint`.

## Relationship to Existing Docs

The wiki **supersedes** `.design-specs/` and `.opencode/plans/` for new work. Existing content in those directories may be migrated into wiki pages or kept as reference. The wiki is the primary source for project documentation going forward.