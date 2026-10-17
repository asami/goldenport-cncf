# Phase 103 — Declaration Metadata Interpretation

Date: 2026-10-18
Status: Step P103-S1 accepted locally; S2 ACCEPTED_PENDING_LOCAL_COMMIT; Phase delivery in progress.

Phase 103 began the normalized declaration interpretation Step. The frozen
boundary is an additive public metadata ABI and its pure class/operation
composition. The [specification](../../../spec/validation-test-metadata.md) and
[design](../../../design/validation-test-metadata.md) promote the resolved
semantics from the October 6 planning note.

The authored public API distinguishes missing declarations from explicit empty
declarations through origins. Independent purpose membership, explicit purpose
replacement, scoped feature union, operation-duration normalization and binding
to the existing execution aggregate are represented as typed data. Compatibility
and malformed input fail through structured Consequence policy diagnostics.
Both declarations are admitted before composition, including shadowed defaults.

The singular execution requirement reuses the existing Workflow aggregate and
validator. Its metadata binding canonicalizes capability identities, preserves
the strongest existing reasoning and required review, and rejects differing
opaque risk or incompatible optional execution facts. This does not add a
provider or executor-selection model.

The authored Executable Specification covers declaration presence, inheritance,
purpose overrides, feature permutation/extension, duration units and overrides,
compatibility, null admission, execution composition and deterministic input
preservation. Focused validation passed all 17 declaration scenarios and 16
existing execution-contract regressions across three suites, with zero failures.
The serialized SBT wrapper reported successful terminal completion and released
its lock. An independent protected conformance review accepted the complete
Step accumulator with no blocker, Hygiene or Development Candidate findings.
The parent accepted the Step in local commit
`ac443ed353cc682251e4d17bf4e64a1285955798`. This is not full Phase acceptance.

The parent clarified the finite-duration boundary: values excluded by Scala's
`FiniteDuration` constructor are outside the typed ABI's constructible input
space. The specification exercises the largest positive nanosecond value and
unit normalization, with CNCF domain rejection for constructible zero/negative
values. It does not fabricate duration instances or attribute constructor
exceptions to CNCF admission.

S1 provides no annotation, discovery or invocation support. S2 authors scoped
executable identity/revision provenance, completeness and real scenario discovery.
Typed CNCF invocation, CML mapping and concrete runtime/static consumer fixtures
remain S3/Phase obligations. The [phase](../../../phase/phase-103.md) and
[checklist](../../../phase/phase-103-checklist.md) retain that connected delivery
boundary; this Step does not close Phase 103.

## Step P103-S2 authoring handoff

Frozen P103-S2A authors public Java runtime annotations directly usable from
Scala 3 and a typed `ValidationDeclarationProvider`. Providers are admitted for
subclass, public no-arg construction and matching local artifact provenance
before construction. The annotation interpreter reads each source declaration
once and delegates valid defaults/overrides to the accepted S1 resolver.

The public discovery API takes an explicit local artifact/package scope and
caller-owned ClassLoader. Bounded directory/JAR enumeration obtains actual
ScalaTest Suite registrations, with public no-arg flat FunSuite and WordSpec
fixtures. Stable component/specification/operation references are separate from
artifact/source/operation revisions and typed framework selectors. Complete,
partial and unknown scopes retain bounded diagnostics; invalid metadata rows
remain inspectable and unannotated rows remain valid without inferred purposes.
Duplicate logical references invalidate all colliding rows.

Pure queries implement OR within purpose and feature dimensions, AND between
dimensions, and empty-dimension admission. Exact supplemental references retain
the same metadata and execution aggregate, distinguishing absent, invalid and
ambiguous operations. The authored specifications exercise actual compiled test
artifacts, loader and suite/provider provenance, local JARs, completeness,
determinism, invalid declarations/anchors and a single concrete parameter case
through in-process ScalaTest events. Fixture bodies pass independently of any
invalid metadata they declare.

Parent-selected focused validation passed 36 discovery/selection scenarios and
17 S1 declaration regressions across three suites, with zero failures or aborts.
SBT and its serialized wrapper both completed successfully and released the lock.
Initial independent protected-focused Step review found one descendant-symlink
completeness defect: skipped linked directories/classes could leave a scope
incorrectly COMPLETE. The bounded correction retains nonfollowing traversal,
reports a bounded enumeration gap diagnostic and adds two real directory-link
and class-link regressions. Fresh independent focused review accepted closure
with no remaining blocker, Hygiene or Development Candidate findings. S2 is
ACCEPTED_PENDING_LOCAL_COMMIT; the local Step commit remains pending.
The S1 commit remains a read-only dependency. No consumer source has been changed.
S3 still adapts selectors to the existing CNCF typed execution path and delivers
CML equivalence, shared static fixtures and actual sm-workflow/cbd-support
handoffs. Full connected Phase checks and VM-01..04 remain open.
