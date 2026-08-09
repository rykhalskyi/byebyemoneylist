# AGENTS — Project Wiki Schema

This document describes the structure, conventions, and workflows for the project wiki.
The wiki lives at `<project-root>/wiki/` and is version-controlled alongside the code.

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
│   ├── plans/                 # Implementation plans (pre-implementation)
│   ├── tickets/               # Post-implementation summaries
│   └── research/              # Research, analysis, investigation pages
```

## Development Workflow

The wiki supports the full lifecycle, replacing `.design-specs/` and `.opencode/plans/`:

1. **Spec** — define what to build (requirements, scope, design, constraints)
2. **Plan** — define how to build it (steps, files, migrations, tests)
3. **Implement** — do the work (outside the wiki)
4. **Ticket** — document what was done (summary, decisions, files changed)

Spec and plan pages share the same slug so they cross-link naturally (e.g., `specs/dual-price-feature.md` ↔ `plans/dual-price-feature.md`). The ticket page links back to both.

## Page Conventions

Every page MUST have YAML frontmatter:

```yaml
---
created: YYYY-MM-DD
type: overview | build-deploy | spec | plan | ticket | research
tags: [tag1, tag2]
related: [[page-slug]] [[other-page]]
---
```

- **`created`** (required): ISO 8601 date. Never changes after page creation — this preserves history.
- **`type`** (required): Page category.
- **`tags`** (optional): Freeform tags for cross-referencing and searching.
- **`related`** (optional): Wikilinks to connected pages.

Use wikilinks (`[[Page Name]]`) or relative markdown links for cross-references.
When updating a page, append changes under an `## Updates` section — never alter the `created` date.

## Operation Workflows

### Creating a Spec Page

When starting a new feature:
1. Create `pages/specs/<feature-slug>.md` (e.g., `dual-price-feature.md`).
2. Content: feature name, summary, requirements, scope, design decisions, constraints, UI/UX.
3. Add YAML frontmatter with `type: spec`, `created` date, `related: [[plans/<same-slug>]]`.
4. Add entry to `index.md` under `## Specs`.
5. Append to `log.md`: `## [YYYY-MM-DD] spec | <feature-name>`.

### Creating a Plan Page

Before implementing (after spec is created):
1. Create `pages/plans/<feature-slug>.md` — same slug as the spec.
2. Content: link to spec, step-by-step plan, files to touch, DB migrations, test checklist, risk assessment.
3. Add YAML frontmatter with `type: plan`, `created` date, `related: [[specs/<same-slug>]]`.
4. Add entry to `index.md` under `## Plans`.
5. Append to `log.md`: `## [YYYY-MM-DD] plan | <feature-name>`.

### Creating a Ticket Page

After implementation:
1. Create `pages/tickets/<slug>.md` (e.g., `issue-11-dual-price.md`).
2. Content: ticket ID, title, what was implemented, deviations from plan, decisions, files touched. Link to spec and plan pages.
3. Add YAML frontmatter with `type: ticket` and the current date as `created`.
4. Add entry to `index.md` under `## Tickets`.
5. Append to `log.md`: `## [YYYY-MM-DD] ticket | <title>`.

### Creating a Research Page

When completing research/analysis:
1. Create `pages/research/<slug>.md` (e.g., `kmp-feasibility.md`).
2. Content: topic, context, findings, conclusions, recommended actions.
3. Add YAML frontmatter with `type: research` and the current date as `created`.
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
3. Synthesize answer with wikilink citations.
4. Offer to file new knowledge as a research page.

### Lint

Periodically check:
- Orphan pages (not linked from `index.md` or other pages).
- Missing frontmatter (`created`, `type`).
- Specs without a corresponding plan (and vice versa).
- Stale content (outdated commands, removed files, old versions).
- Missing cross-references in spec → plan → ticket chains.
- Suggest new pages for undocumented topics.

## Slug Conventions

- Specs and plans: `<feature-name>` (e.g., `dual-price-feature`). Same slug for both so they cross-link. 2-5 lowercase hyphen-separated words.
- Tickets: `issue-<number>-<brief>` (e.g., `issue-11-dual-price`). 2-4 lowercase hyphen-separated words.
- Research: `<topic>` (e.g., `kmp-feasibility`). Natural topic name, lowercase, hyphens.

## log.md Format

All entries: `## [YYYY-MM-DD] <action> | <description>`
Valid actions: `init`, `spec`, `plan`, `ticket`, `research`, `update`, `lint`.

## Relationship to Existing Docs

The wiki **supersedes** `.design-specs/` and `.opencode/plans/` for new work. Existing content in those directories may be migrated into wiki pages or kept as reference. The wiki is the primary source for project documentation going forward.
