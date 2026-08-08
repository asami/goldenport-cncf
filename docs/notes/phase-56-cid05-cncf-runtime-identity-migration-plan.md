# Phase 56 CID-05 - CNCF Runtime Identity Migration Plan

status=accepted/reviewed
date=2026-08-08
phase=[Phase 56](../phase/phase-56.md)
checklist=[Phase 56 checklist](../phase/phase-56-checklist.md)
authority=[CID-01 Component identity inventory and failing-first contract](phase-56-cid01-component-identity-inventory-and-failing-first-contract.md)
step=CID-05

## Step closure criterion

CID-05 closes only when the runtime accepts and carries the exact qualified
`ComponentId` through Core creation, descriptor/factory/loading/dependency/
configuration admission, ComponentSpace/routing/Help/Admin/diagnostics, and
namespace-isolated runtime integration. No runtime consumer may treat a
presentation value, artifact spelling, or compatibility alias as identity.

## Frozen Slice ledger

| Slice | Boundary | Status |
| --- | --- | --- |
| CID-05A | Runtime Core Identity Admission | ACCEPTED/REVIEWED |
| CID-05B | Descriptor/factory/loading/dependency/configuration identity | ACCEPTED/REVIEWED |
| CID-05C | ComponentSpace/routing/Help/Admin/diagnostics identity | ACCEPTED/REVIEWED |
| CID-05D | Namespace-isolated runtime integration | ACCEPTED/REVIEWED |

## CID-05D accepted implementation boundary

Before its static descriptor discovery, closure, and evaluation work,
`SubsystemAssemblyAdmission` promotes only untyped bindings whose exact
`componentName` succeeds through `ComponentId.parseC`; explicit typed values
remain authoritative, parse failure retains legacy behavior, and canonical
instance identity is validated before proceeding. No normalization, display/
artifact/filename derivation, or bare-name promotion is added.

The executable acceptance writes a real descriptor with
`org.alpha.textus.Shared` and `org.beta.textus.Shared`, creates two separate
schema-3 release-`0.6.0` CARs, and uses the normal component-dir repository and
`GenericSubsystemFactory`. It verifies descriptor typed IDs, CAR/ClassLoader/
factory/Core/default-instance/artifact/repository-origin isolation, shared
presentation `Shared`, qualified public Request scalar responses, and bare
ComponentSpace/resolver ambiguity behavior.

Implementation-focused validation passed with invocation
`33307-20260807T220812Z`: 4 suites, 55 succeeded, 1 intentional pending leaf,
exits 0, and lock released. Independent review remained pending at this
implementation checkpoint. The exact command was:

```text
testOnly org.goldenport.cncf.subsystem.Phase56NamespaceIsolatedRuntimeIntegrationSpec org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityMigrationSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityProjectionSpec
```

CID-05D does not implement CID-06 adapters, change CID-01 E4, ComponentId
syntax, repository coordinates/cache behavior, HYG-P56-005, or Phase-56
completion. Phase full validation remains pending.

## Phase 57 handoff

## CID-05D review and closure status

`RF-CID05D-001` through `RF-CID05D-010` are repaired. The prior slice
checkpoint was **REVIEW_FIX_COMPLETE** while focused re-review remained
pending: the frozen repair rejects
whitespace-normalized canonical/binding identities, preserves exact typed
selection, isolates ambiguous display aliases in Web/manual roots, and
transfers successful CAR loader ownership to subsystem shutdown. Validation
repairs `VF-CID05D-001` (replaced the illegal anonymous sealed `Specification`
with existing concrete development-repository specifications) and
`VF-CID05D-002` (corrected lifecycle observation to the Subsystem-owned loader
snapshot without changing lifecycle semantics) are complete. At that
checkpoint, CID-05 and Phase 56 remained incomplete, no review acceptance or
commit was claimed, and
plain-`Action` execution semantics remain Phase 57 ownership. This pass also
applies `RF-CID05D-011` through `RF-CID05D-013`: ComponentDependency retains
its Jul. 30 version entry as history while carrying the current Aug. 8 header;
the ComponentDescriptor method-local helper uses the required leading and
trailing underscore; and all RuntimeComponentDevelopmentWebProjectionSpec
temporary directories are rooted under the deterministic repository-local
target work root and removed with finally-based lifecycle cleanup. The
authoritative focused validation for these repairs is invocation
`75311-20260808T000025Z`, using the exact logical argv:

```text
["--batch", "testOnly org.goldenport.cncf.http.RuntimeComponentDevelopmentWebProjectionSpec"]
```

The final wrapper was
`/Users/asami/.codex/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh --batch 'testOnly org.goldenport.cncf.http.RuntimeComponentDevelopmentWebProjectionSpec'`;
one suite completed with 10 tests succeeded and zero failed, canceled,
ignored, or pending; `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`.
The parent also verified that
`target/cncf-test/work/runtime-component-development-web-projection-spec`
is absent after the suite and that the owned diff-check is clean. Independent
focused re-review closed `RF-CID05D-011` through `RF-CID05D-013`, found no new
actionable item, and returned **PASS** with `FULL_REVIEW_REQUIRED=no` on
reviewed scoped diff
`64fd912685db918b9c92e5b02e556b81b42b39cd83b2450aeb31ec86255b46ec`.
CID-05D is **ACCEPTED/REVIEWED**, and all four CID-05 Slices now satisfy the
Step closure criterion. The Step commit, Phase full validation, and Phase 56
closure remain pending; HYG-P56-005 remains separate.

The authoritative final focused validation is invocation
`59976-20260807T231428Z`, with exact logical argv:

```text
["--batch", "testOnly org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityMigrationSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityProjectionSpec org.goldenport.cncf.subsystem.Phase56NamespaceIsolatedRuntimeIntegrationSpec org.goldenport.cncf.component.ComponentDescriptorSpec org.goldenport.cncf.component.repository.ComponentRepositoryCarSpec org.goldenport.cncf.http.RuntimeComponentDevelopmentWebProjectionSpec org.goldenport.cncf.subsystem.resolver.OperationResolverSpec org.goldenport.cncf.projection.GeneratedHelpProjectionSpec org.goldenport.cncf.subsystem.GenericSubsystemDescriptorSpec org.goldenport.cncf.subsystem.GenericSubsystemFactorySpec"]
```

Eleven suites completed, zero aborted; 243 tests succeeded, zero
failed/canceled/ignored, and one intentional pending remained;
`sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. The smallest lifecycle
correction evidence is invocation `59556-20260807T231330Z`: 1/1 passed, exits
0, and lock released. The final focused re-review result is the PASS recorded
above.

CID-05D execution exposed an adjacent public-contract defect: a successful
plain `Action` was implicitly submitted as a job and returned a generated job
ID instead of its scalar response. The temporary CID-05D fixture uses
`QueryAction` to obey the current runtime contract.

That defect is not part of CID-05 closure and is not hygiene. It is owned by
[Phase 57 - Action Execution Semantics](../phase/phase-57.md), which inventories
compatibility, freezes a failing-first direct/query/command/explicit-async
matrix, makes plain `Action` the simplest synchronous route, and independently
validates the public behavior change.

## CID-05A frozen boundary

CID-05A makes `namespace + local-id`, as represented by the shared
`ComponentId`, the sole runtime Core identity authority. `Component.Core.name`
is a verified materialized projection equal to `ComponentId.name`; no
normalization, alias lookup, or identity guessing is admitted. The Core
constructor and all `create` overloads reject null component or instance
identities, a divergent supplied name, and an instance whose `ComponentId`
differs from Core's exact `ComponentId`. Direct case-class construction and
`copy` retain the same admission boundary.

The same-file Script helper uses exactly `org.goldenport.cncf.Script` for its
name and `ComponentId`, retaining the default instance. Presentation metadata
does not participate in this admission rule.

## CID-05B implementation and review-fix evidence

Authoritative CID-05B focused implementation evidence is invocation
`64956-20260807T141548Z` from `cloud-native-component-framework`. It ran the
three focused suites including `ComponentDescriptorSpec`,
`GenericSubsystemDescriptorSpec`, and `GenericSubsystemFactorySpec`: 59 passed,
one intentional pending, exits 0, and the shared lock was released. This is
implementation evidence, not a future invocation or an acceptance decision.

The initial CID-05B review verdict was **FINDINGS**. Review-fix closes the
shared release-coordinate mismatch, packed and expanded CAR admission,
non-mutating cardinality validation, loader lifecycle, canonical assembly
selection, legacy root-alias rejection, archive I/O provenance, and focused
repository-boundary evidence. Final review-fix invocation
`81149-20260807T150153Z` passed 106 tests with one intentional pending leaf
across nine suite executions; exits were zero and the shared lock was
released. Independent focused re-review closed CID05B-R1 through R7 with no
new finding and returned **PASS** on tracked diff
`4d0f835fab40b5b4e0dfb6c69370e4ca17659f848b32602f3c848abcb5e1b459`;
CID-05B, CID-05C, and CID-05D are accepted/reviewed.

## CID-05C initial full review findings

The initial CID-05C full review verdict is **FINDINGS**. The admitted repair
set is `CID05C-R1` through `CID05C-R9`: nonthrowing Request fallback and
malformed-dot rejection; typed `ComponentId` resolver entries; Web display and
development-directory compatibility; complete legacy-alias ambiguity handling;
HelpModel positional source compatibility; ambiguity-safe help selectors;
resolver/meta/request malformed selector rejection; target-file naming cleanup;
and executable-specification narrative compliance. The first focused re-review
closed R2 through R5 but retained R1 and R6 through R9 for Request/Meta/Help
alias boundaries plus whole-target naming, header, and metadata compliance.
Those findings were repaired and invocation `93982-20260807T202626Z` passed 225
tests across 11 suites. A second focused re-review then found
`RF-CID05C-010` default-instance selection and `RF-CID05C-011` JobControl
`taskId` compatibility. Both are repaired with focused regressions, including
canonical migration of the generated-bundle fixture. Authoritative invocation
`11800-20260707T210921Z` passed 234 tests across 12 suites with one intentional
pending leaf; SBT and wrapper exits were zero and the shared lock was released.
Independent focused re-review returned PASS on reviewed tracked diff
`b5c00333fa8d91d09a03473f8cbcff741dbb5f23877039db034cc4715ac6fd93`;
`RF-CID05C-010`, `RF-CID05C-011`, and validation repairs
`VF-CID05C-012` through `VF-CID05C-015` are closed. CID-05D is
**ACCEPTED/REVIEWED**; CID-05 is complete and Phase 56 remains incomplete.

## Focused validation

CID-05C implementation carries exact qualified runtime identity through
ComponentSpace, resolver and Subsystem routing, Help/Admin records, and
parameter diagnostics. Visible CLI/REST display selectors remain compatibility
routes only and resolve only when unique. Authoritative final repair invocation
`11800-20260707T210921Z` passed 234 tests across 12 suites with one intentional
pending leaf; SBT and wrapper exits were zero and the shared lock was released.
Independent focused re-review returned PASS on reviewed tracked diff
`b5c00333fa8d91d09a03473f8cbcff741dbb5f23877039db034cc4715ac6fd93`.
CID-05C and CID-05D are accepted/reviewed; CID-05 is complete and Phase 56
remains incomplete.

CID-05A accepted/reviewed invocation `44742-20260807T131308Z` passed one suite,
12 executable leaves, 24 matrix operations, and one intentional pending leaf;
all exits were zero and the shared lock was released.

The CID-05D focused Step integration command passed as invocation
`33307-20260807T220812Z` with 4 suites, 55 succeeded, 1 intentional pending
leaf, exits 0, and lock released:

```text
testOnly org.goldenport.cncf.subsystem.Phase56NamespaceIsolatedRuntimeIntegrationSpec org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityMigrationSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityProjectionSpec
```

Repository-wide full suites remain Phase-release-only and pending; neither
focused validation nor Step integration closes that Phase boundary.

## Non-goals and follow-up

CID-05B implements canonical schema-3 descriptor admission, typed descriptor
and artifact identity, exact Core/factory selection, and qualified
configuration target selection. It does not add a compatibility adapter,
change routing, ComponentSpace, Help/Admin, diagnostic projection, or close
the two-namespace runtime integration boundary. Those boundaries are owned by
CID-05C/D; both are accepted/reviewed and CID-05 is complete. Broad
cross-file/public hygiene remains HYG-P56-005 and is separate. E4 compatibility
ambiguity behavior remains pending for CID-06.
