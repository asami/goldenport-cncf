# Phase 57.3 AES-06R Runtime Compatibility Retirement Plan

status=non-normative
phase=[Phase 57.3](../phase/phase-57.3.md)
checklist=[Phase 57.3 Checklist](../phase/phase-57.3-checklist.md)

## Purpose

This accepted execution inventory records the AES-06R compatibility-retirement
work without changing the approved Phase 57.3 goal, gate, closure, or
non-goals. It is a ledger, not closure evidence.

## Work A — Compatibility Inventory and Retained Authority

| Compatibility surface | Authority classification | AES-06R disposition |
| --- | --- | --- |
| Component descriptor schemas 1/2, CAR ABI v1, component repository index v1, unqualified Component identity, legacy names, aliases, deferred-release, and silent fallback | Unreleased development/runtime compatibility; no retained authority | Retire at the owning runtime-consumer boundary and fail closed. |
| Component repository index v2, descriptor schema 3, CAR ABI v2, assembly `namespace`/`id`/`version`, qualified Component identity | Canonical Phase 56 authority | Admit only after canonical validation. |
| Stable Web-path aliases and published artifact filenames | Published/production presentation authority only | Retain presentation compatibility where separately owned; never use it as identity authority or a runtime admission fallback. |
| Cozy/sbt-cozy packager, publisher, repository writers, and warehouse reconstruction | Producer/operations authority | Excluded from this Phase; an operator owns backup and rebuild. |

## Stable Slice Ledger

| Slice | Scope and dependency | Acceptance and focused validation | Status |
| --- | --- | --- | --- |
| AES-06R-A | Launcher repository-index v2 consumer and Phase execution ledger; depends on the framework `ComponentRepositoryIndex` v2 authority. | Invalid, v1, and unsupported index state fails closed with the operator procedure; valid CAR namespace/id and SAR entries preserve development precedence and six-column output. Accepted launcher invocation `20523-20260812T133116Z` (`CncfLauncherSpec` OK) and clean full review. | ACCEPTED |
| AES-06R-B | Framework runtime descriptor and ABI compatibility retirement; depends on Work A inventory. | Canonical descriptor schema 3 and ABI v2 admission only; accepted framework exact accumulator `36781-20260812T231307Z` and clean full review. | ACCEPTED |
| AES-06R-C | Framework assembly, repository, and qualified Component identity retirement; depends on AES-06R-B. | Canonical assembly and qualified identity admission only; accepted framework exact accumulator `36781-20260812T231307Z` and clean full review. | ACCEPTED |
| AES-06R-D | Runtime/launcher remaining legacy-name, alias, deferred-release, and fallback retirement; depends on AES-06R-B/C inventory findings. | Each retained branch has published/production authority or is removed; accepted framework exact accumulator `36781-20260812T231307Z` and clean full review. | ACCEPTED |
| AES-06R-E | Integrated evidence normalization and Step handoff; depends on AES-06R-A through D accepted evidence. | Documentation normalization is implemented and static validation passed; independent documentation review remains pending. | IMPLEMENTED / STATIC VALIDATION PASSED / REVIEW PENDING |

## Accepted Evidence

- Launcher invocation `20523-20260812T133116Z`: `CncfLauncherSpec` OK, with
  clean full review.
- Framework exact 17-suite accumulator `36781-20260812T231307Z`: 668/668,
  with clean full review.
- Independent Terra-xhigh full review accepted the framework 72-source-path
  delta and launcher four-path delta.

No Step commit, Phase full suite, or Phase closure is claimed here. HYG-P57.3-001
is persisted and resolved in the canonical Phase Hygiene Journal by Slice E.

## Operator Boundary

An invalid active warehouse index is not migrated in runtime. The recovery
procedure is operator-owned: stop warehouse users, preserve the whole active
warehouse in a timestamped forensic sibling, create an empty active warehouse,
republish canonical artifacts, retry, and verify the v2-only listing. No
launcher or runtime code in this plan backs up, merges, migrates, deletes, or
rebuilds warehouse state.

## Phase 57.4 Exclusions

Phase 57.4 owns producer-side packaging, publishing, repository-writing
migration, and any approved presentation compatibility work. This plan neither
imports forensic backups nor changes development `-SNAPSHOT` versions, and it
does not assert validation, review, commit, Step completion, or Phase
completion.
