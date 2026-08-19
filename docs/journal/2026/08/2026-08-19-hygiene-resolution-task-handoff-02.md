# Hygiene Resolution Task Handoff

Status: CLOSED (SUPERSEDED)
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repository: /Users/asami/src/dev2025/cozy
Superseded By: `docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md`

## Purpose

Add focused runtime-integration evidence for Cozy's existing direct HTTP archive
read path, without changing the descriptor/publication contract.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P56-002
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-08-19

## Included Hygiene

| ID | Source | Evidence | Target | Risk | Required outcome |
| --- | --- | --- | --- | --- | --- |
| HYG-P56-002 | `docs/journal/2026/08/2026-08-07-phase-56-hygiene-follow-up.md` | `CozyArchivePackager.scala` performs a direct `URLConnection` read around line 482 but has no separate runtime-integration evidence. | `src/main/scala/cozy/archive/CozyArchivePackager.scala`; `src/test/scala/cozy/CozyArchivePackagerSpec.scala` | P2 runtime/network evidence gap; no reported defect. | Add or refine bounded evidence for the existing read path and preserve its behavior. |

## Frozen Boundary

- Allowed repositories: `cozy` only.
- Allowed target programs/files: the named archive packager and its directly
  covering specification; mechanically required local fixtures only.
- Allowed behavior change: none unless a failing existing-path regression
  proves a local compatibility-preserving repair is necessary.
- Prohibited expansion: descriptor/publication redesign, network-client
  replacement, remote publishing, CAR transaction changes, unrelated archive
  paths, and all other Hygiene records.

## Required Validation

1. Run the directly covering archive-packager specification with a controlled
   local runtime/network seam.
2. Review the complete target source and specification for fixture isolation
   and preservation of existing failure behavior.
3. Run the full Cozy validation required by `cncf-goal-task`.

## Completion Contract

- Resolve every included ID or report it unchanged with evidence.
- Update each source Hygiene record with commit and validation evidence.
- Change source status to RESOLVED only after accepted validation and commit.
- Do not absorb Development Candidates or unrelated Hygiene.

## Dependencies and Ordering

Run after any user-owned Cozy video work is isolated; that work is not part of
this handoff.

## Non-goals

- Changing runtime HTTP semantics or publication behavior.
- Publishing, deploying, or contacting external services.
