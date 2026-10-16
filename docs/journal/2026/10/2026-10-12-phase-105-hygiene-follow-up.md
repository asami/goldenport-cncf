# Phase 105 Hygiene Follow-up

Owner repository: cloud-native-component-framework.
Discovery date: 2026-10-12. These are nonblocking, pre-existing maintenance
items, not unfinished Phase105 behavior or an authorization to repair them here.

| Canonical ID | Status | Discovery | Category | Proposed later boundary |
| --- | --- | --- | --- | --- |
| HYG-105-WCS-CORE-001 | OPEN | Step review | Fixture type ownership | Specification fixture conformance |
| HYG-105-FULL-001 | OPEN | Phase full review | Internal naming | Producer naming conformance with existing callers |
| HYG-105-FULL-003 | OPEN | Phase full review | Executable-document authoring | Existing transient codec scenario clause placement |
| HYG-105-PHASEFIX1-001 | OPEN | Phase repair1 focused review | Host fixture naming | Host-spec fixture declarations and existing callers |

Full-review observation HYG-105-FULL-002 shares the AvailableCompletion root
cause with HYG-105-WCS-CORE-001; the latter remains the sole canonical item.
The accepted records below retain their original text. Later work should link
its task and commit and update the status ledger without deleting this history.

<!-- cncf-phase-finding:HYG-105-FULL-001:begin -->
### HYG-105-FULL-001 — Inherited internal naming debt

Status: OPEN. Owner repository: cloud-native-component-framework.

Evidence: src/main/scala/org/goldenport/cncf/workflow/WorkflowResultJsonV1.scala:186,208,223,231 retains the package-visible helpers _decodeHandle, _decodeSnapshot, _decodeReference and _decodeReferences. The unchanged supporting src/main/scala/org/goldenport/cncf/event/ManagedEventObservationRequest.scala:16-22,59-69 retains unprefixed private members and camelCase private helpers. These declarations predate the Phase105 accumulator; Phase105 introduces no new instance of this naming debt.

Risk: inconsistent internal vocabulary increases maintenance cost but does not change the reviewed completion or event behavior.

Proposed maintenance boundary: a separate producer naming-conformance task that updates the selected internal declarations and their existing callers together. Preserve public signatures, serialized names and behavior. This item is nonblocking and is not admitted into the current Phase105 repair boundary.
<!-- cncf-phase-finding:HYG-105-FULL-001:end -->

<!-- cncf-phase-finding:HYG-105-FULL-003:begin -->
### HYG-105-FULL-003 — Inherited transient codec scenario clause placement

Status: OPEN. Owner repository: cloud-native-component-framework.

Evidence: src/test/scala/org/goldenport/cncf/event/EventRecordJsonV1Spec.scala:83-84 constructs and decodes a transient variant after the Then clause in the scenario 'retain exact identity, nanosecond time, lane, flags, long precision and payload types', without a separate action clause. This behavior body is unchanged from the original Phase105 base; the new and materially changed Phase105 codec scenarios have adjacent semantic Given, When and Then clauses.

Risk: executable output does not clearly identify the secondary transient codec action, reducing the specification's readability without weakening the observed assertion.

Proposed maintenance boundary: a separate producer executable-document authoring repair that places the transient setup, codec action and expectations at their semantic boundaries while preserving the existing assertion and coverage. This item is nonblocking and is not an AUTHORING_GATE_FAILURE introduced by Phase105.
<!-- cncf-phase-finding:HYG-105-FULL-003:end -->

<!-- cncf-phase-finding:HYG-105-WCS-CORE-001:begin -->
HYG-105-WCS-CORE-001 — Pre-existing instance-owned fixture data type. WorkflowCompletionDataStorePersistenceSpec.scala:141 declares AvailableCompletion as a private case class inside the specification instance; the same declaration exists at original base 15756431cf31c7172099b8544a049985d201df25. Shared type-modeling rules require instance-independent ownership for fixture data types. No introduced behavior or compatibility defect was established. In a separately scoped fixture-maintenance task, move this data type to a stable object or companion namespace while preserving its fields, fixture behavior and executable coverage. This is nonblocking Hygiene and does not authorize expansion of the current Step repair.
<!-- cncf-phase-finding:HYG-105-WCS-CORE-001:end -->

<!-- cncf-phase-finding:HYG-105-PHASEFIX1-001:begin -->
HYG-105-PHASEFIX1-001 — Inherited host-spec fixture naming. Status: OPEN. EventStorePersistenceHostSpec.scala:27-127 retains unprefixed private members and camelCase fixture helpers such as rootCollection, planFor and selectedRecords. Original-base..HEAD is empty for this specification, and the repair does not modify these declarations. This is nonblocking naming debt, with no demonstrated behavioral defect. Proposed maintenance boundary: a separate host-spec fixture naming task updating declarations and existing callers together while preserving behavior. It is not admitted into this Phase repair.
<!-- cncf-phase-finding:HYG-105-PHASEFIX1-001:end -->
