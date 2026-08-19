# Hygiene Resolution Task Handoff

Status: CLOSED (SUPERSEDED)
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repository: /Users/asami/src/dev2025/cozy
Superseded By: `docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md`

## Purpose

Contain SAR publication staging artifacts within Cozy's repository work
boundary without changing SAR publication semantics.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P56-004
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-08-19

## Included Hygiene

| ID | Source | Evidence | Target | Risk | Required outcome |
| --- | --- | --- | --- | --- | --- |
| HYG-P56-004 | `docs/journal/2026/08/2026-08-07-phase-56-hygiene-follow-up.md` | `CozySarPublisher.scala` creates `cozy-publish-sar-` files through system `Files.createTempFile` around line 25. | `src/main/scala/cozy/archive/CozySarPublisher.scala`; `src/test/scala/cozy/CozySarPublisherSpec.scala` | P3 staging-artifact containment. | Move only the temporary work location under the approved repository artifact boundary and preserve output behavior. |

## Frozen Boundary

- Allowed repositories: `cozy` only.
- Allowed target programs/files: the named SAR publisher and its directly
  covering specification; mechanically required local fixtures only.
- Allowed behavior change: temporary work-file location only.
- Prohibited expansion: SAR format, publication coordinate, remote upload,
  CAR transaction, cleanup of unrelated temporary files, and other Hygiene.

## Required Validation

1. Run the directly covering SAR publisher specification and prove temporary
   artifacts use the approved repository work boundary.
2. Review output naming, cleanup, and failure behavior for compatibility.
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

- Altering SAR contents, publishing, or remote credentials.
- Broad temporary-directory cleanup.
