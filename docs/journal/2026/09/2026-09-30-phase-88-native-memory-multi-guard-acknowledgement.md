# Native memory multi-guard acknowledgement

Date: 2026-09-30
Status: internal same-owner staged acknowledgement passed focused-310; S12 open

## Change

InMemoryDataStore now has a package-limited
conditionalMultiGuardAcknowledgedSameOwner seam. It captures complete ordered
root/successor/additional-guard/Create post-images from its staged native state
before the BeforePublish checkpoint, and returns Committed only after the one
state replacement. A failed checkpoint returns neither staged writes nor an
acknowledgement. NotMatched remains a non-publication result.

The previous same-owner two-record method projects the native acknowledgement
back to its existing result. Ordinary conditional transitions reuse the same
staging path and retain their public result. There is no new public factory,
caller callback, owner grant, automatic retry or production capability opt-in.
The verified route fixture now takes the provider acknowledgement rather than
reconstructing expected post-images with independent preloads.

Two new executable examples cover retained native post-images after a later
storage mutation and no acknowledgement/no writes at all four pre-publication
failure checkpoints. Readback now has nine examples. Cross-owner stays denied;
Workflow/DataStoreSpace admission and coherent readback remain separate checks.

## Validation

SMWF-CNCF-NATIVE-ACK-DIRECT-20260930-310 passed 144 tests in 18 suites, including
the prior 14 suites and four existing conditional update/Create/transition
regressions. Zero failed, aborted, canceled, ignored or pending.
SBT exit=0, wrapper exit=0, lock=released. Parent execution kept the shared
serial wrapper, fresh metadata and scoped platform review under the user's
simplified execution instruction.

Log:
/tmp/skill.cncf.d/cncf-sbt-c7723cf53c26d4b289301759551b7ff1cbfe9571de42105029d69c509ca3ffde-bdcb5c45e5676c8a4ed4d6bb401dc4fa/14754-20260930T053845Z.log

Validated source SHA-256:
- DataStore.scala: 81cba1cb282a7b402b4e685c5f316472d914b4a1e929dade46618190323378e0
- DataStoreMultiGuardReadbackSpec.scala: ef97b6c6e7e85fdc748d5efc843d8cc4974885b5c80aef2b7cff4fc8eef44aad

This is focused evidence, not a typed Phase/PLAN acceptance receipt, independent
review, full-suite validation or production Workflow/dispatch acceptance.
No commit/publication was performed. SQL native multi-guard staging/snapshot,
cross-owner managed admission/atomicity and selected cursor/index/event delivery
integration remain open. Do not mark S12/P2-U15/P2-U16/P2-U05 complete.
