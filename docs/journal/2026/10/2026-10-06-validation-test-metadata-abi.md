# Validation/Test Metadata ABI producer decision

Date: 2026-10-06
Status: design decision

sm-workflow Test Suite design and cbd-support static-review needs converged on one common requirement: executable specifications/test programs need portable metadata describing validation purpose, feature coverage, expected duration and execution requirements.

The decision is to define this semantic contract in CNCF rather than independently in sm-workflow or cbd-support.

The source of truth is the executable specification/test operation itself. Hand-written Scala uses CNCF annotations; CML uses Cozy-owned properties mapped to the same normalized model; generated Scala is projection when needed. This follows the existing Phase 99 Finding Disposition ABI pattern.

Operation/scenario metadata is the primary execution granularity, while class/specification metadata supplies defaults/shared features. The same operation can participate in several purposes such as SMOKE, ADMISSION and FULL.

The initial common purposes are SMOKE, FOCUSED, ADMISSION, FULL and HEAVY. They are explicit memberships, not a numeric inclusion hierarchy. Consumer policies remain downstream: the one-minute ADMISSION and ten-minute FULL targets belong to sm-workflow, while Test Architecture lint/KPI belongs to cbd-support.

This removes the need for a separate managed TestSuite definition file as the normal source of truth. sm-workflow Test Suites become deterministic queries over CNCF-resolved metadata. cbd-support analyzes the same metadata statically and can detect missing coverage, purpose/feature imbalance and coverage-reducing metadata changes.

CNCF should provide deterministic resolved-metadata interpretation/discovery so consumers do not each parse Scala annotation source or CML syntax independently.
