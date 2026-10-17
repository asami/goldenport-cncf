# Phase 96 P96-03A — Logical UI static bootstrap admission

Date: 2026-10-03
Scope: PHASE-96 / P96-03 / P96-03A, static provider selection and successful
per-component publication only.
Authoring status: implementation and executable specifications authored;
compilation, focused validation, independent review and Step acceptance pending.

## Governing contract and implementation

[LUI96-S06/S07](../../../spec/logical-ui-generated-abi.md#lui96-s06-provider-selection)
govern selection and atomic static admission. The
[Display runtime design](../../../design/display-model-runtime.md#component-admission-and-explicit-bindings)
keeps structural admission separate from late typed binding readiness. The
[P96-03 checklist](../../../phase/phase-96-checklist.md#p96-03-componentfactory-admission-and-display-projection)
remains open; this Slice does not close the Step.

`ComponentFactoryDisplayPart` selects a component metadata provider first and
considers `component.factory` only when the component has no provider. It reads
the selected getter once and delegates the entire vector to the accepted
`GeneratedLogicalUiAbi.admitC` gate. It preserves the original vector, object
references, order and ABI diagnostic. Selected failures do not trigger fallback.
NonFatal selection/access/getter exceptions use the existing structured
`Consequence.componentInvalid` failure with the original cause. The helper
does not publish Component state.

`Component` holds an internal per-instance
`Option[GeneratedLogicalUiAbi.AdmittedDefinitions]`, initially `None`.
`ComponentFactoryEntityPlanPart` admits Logical UI metadata as the first
generator in the existing bootstrap for-comprehension and publishes its
successful Option immediately before `withCollectionsBootstrapped`, after
the existing bootstrap stages. The pre-existing plan preparation, Workflow,
Provided API, StateMachine, continuation, revision, entity, Aggregate, View,
working-set and event order is retained. A static failure or a subsequent
bootstrap failure leaves UI metadata unpublished. This makes no rollback claim
for unrelated pre-existing subsystem mutations. The already-bootstrapped
fast path returns the same Component without refreshing producer metadata.

The four retained partial production files from the stopped first authoring
attempt are continued under the corrected manifest. Their earlier presence
does not establish compilation, validation or acceptance. No dependency or
build change accompanies the direct ScalaCheck authoring correction.

## Executable scenario inventory

The new
[ComponentFactoryLogicalUiBootstrapSpec](../../../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryLogicalUiBootstrapSpec.scala)
uses AnyWordSpec, Matchers, GivenWhenThen and visible `afterWord` metadata
with executable `in` leaves. Every scenario's Given identifies the canonical
ABI specification, S06/S07 and its E identifier; When and Then accompany the
action and expectations. It reuses the unchanged external hand-written
[ExpectedGeneratedLogicalUiFixture](../../../../src/test/scala/logicalui/ExpectedGeneratedLogicalUiFixture.scala)
and the normal initialized Component/Core/factory fixture pattern. This fixture
is not actual Cozy-generated output.

| Example | Given | When | Then |
| --- | --- | --- | --- |
| E1 | Neither component nor factory has a provider; initialized default services | Normal bootstrap | Same Component, `None`, bootstrapped true, unchanged default services and no `display` service |
| E2 | Component offers the complete accepted fixture with a counted getter | Normal bootstrap | Exact vector/order/object identities retained; selected getter once |
| E3 | Only the factory offers the complete accepted fixture with a counted getter | Normal bootstrap | Exact vector/order/object identities retained; factory provider getter once |
| E4 | Component and factory offer different valid revisions | Normal bootstrap | Component vector retained; shadowed factory getter never evaluated |
| E5 | Component provider with a counting, throwing factory accessor | Direct selector | Exact vector admitted, no factory access, no state publication |
| E6 | Empty, null and malformed selected vectors at component and factory locations; valid shadowed factory for component cases | Normal bootstrap and direct ABI admission | Same structured ABI diagnostic; one selected read, no fallback or published state |
| E7 | Throwing selected getters at both locations; valid shadowed factory for component case | Normal bootstrap | Structured componentInvalid preserves the same exception cause, no fallback/state/bootstrap flag |
| E8 | Valid prefix plus invalid sibling, duplicate screen IDs or broken List-to-Detail target | Normal bootstrap and repeated direct ABI admission | Whole-vector rejection, deterministic diagnostic including code/path, no admitted prefix or sibling |
| E9 | Independent instances reuse screen IDs with different valid revisions and a third has invalid metadata | Bootstrap each successful instance, then the failing instance | Exact independent vectors/wrappers survive third-instance failure; third remains empty |
| E10 | A selected provider changes its return value after successful bootstrap | Bootstrap twice | Same Component, Option, wrapper and definition references; getter once, no hot reload |
| E11 | Valid Logical UI plus selected empty generated Workflow definitions | Direct UI selection, then normal bootstrap | Selector succeeds without publication; unchanged Workflow diagnostic fails bootstrap, UI stays `None` and flag false |
| E12 | Generated valid opaque non-ASCII screen IDs and both complete List/Detail order permutations, against different factory definitions | Prop.forAll selector law and Test.check | Exact selected order/identity, one selected read, no fallback or publication; `passed` and at least 100 successful cases required |
| E13 | One invalid definition at varying positions among unique valid definitions | Prop.forAll selector/direct-ABI deterministic diagnostic law and Test.check | Exact deterministic invalid code/path, no partial admission or publication; `passed` and at least 100 successful cases required |

E12 and E13 use the already-declared `org.scalacheck.{Gen, Prop, Test}` APIs,
each with `Test.Parameters.default.withMinSuccessfulTests(100)`, separate
`result.passed shouldBe true` and `result.succeeded should be >= 100`
expectations, and diagnostic clues. No implication/discard-based generator
is used to turn invalid samples into vacuous success. The success counts
above are executable requirements, not observed results at authoring time.

## Coverage and pending obligations

This authoring covers BOOT-01's absent/component/factory/both precedence and
selected empty/invalid/throwing no-fallback behavior. It covers BOOT-02 only
for whole-vector structural admission, per-instance isolation and publication
after successful existing bootstrap. The pure selector does not certify runtime
binding readiness and the internal admitted Option is not a usable Display
registry.

P96-03B still owns explicit typed source/value/action bindings, the post-View
binding join, pure deterministic List/Detail projection and focused handwritten
fixture proof before Display exposure. P96-03C still requires the producer-owned
actual Cozy-generated source and equivalent CNCF compilation/admission/projection
proof. LUI-ABI-01, LUI-GEN-01 and LUI-ACCEPT-01 remain pending. No service,
lookup, converter inference, public API, runtime CML parsing, global registry
or Cozy implementation dependency is introduced in A.

The parent-selected focused ten-suite validation accumulator from the frozen
Cycle Plan has not been run by this implementation worker. Parent mechanical
checks, focused execution, independent review and subsequent PLAN for B/C
remain outstanding. There is no compilation, passing-property, actual generator,
Slice/Step acceptance, checklist closure or Phase closure claim in this record.

Modified Scala headers retain established `@since` history and the Oct. 3,
2026 source-update date. New Scala declarations use the repository's naming
rules; no unrelated cleanup, new debt disposition, private workflow record
mutation, build/version change or commit is part of this authoring.
