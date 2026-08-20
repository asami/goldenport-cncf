# Phase 58 RSC01-B Failing-First Acceptance Registry

status = frozen RSC01-B successor handoff
date = 2026-08-20
phase = Phase 58
step = RSC01-B
slice = RSC01-B1-failing-first-acceptance-identities-and-handoff
authority = [Phase 58](../phase/phase-58.md)
architecture_authority = [Component and SubComponent Architecture Implementation Proposal](component-subcomponent-architecture-implementation.md)
resource_authority = [Component Resource SubComponent Implementation Proposal](component-resource-subcomponent-implementation.md)
journal = [RSC-01 contract-freeze journal](../journal/2026/08/2026-08-20-phase-58-rsc01-component-subcomponent-contract-freeze.md)
prior_acceptance = RSC01-A accepted commit `46f276fadaaef73495b9796153f656fe6d970e53`

## Purpose and Boundary

This registry is the durable RSC01-B handoff for the exact future
failing-first acceptance identities owned by Phase 58.1 through Phase 58.8.
It freezes acceptance traceability for RSC-01 without implementing successor
behavior, creating executable specifications, or claiming a red build. The
architecture and resource notes remain provisional and non-normative until
Phase 58.9.

RSC01-B registers the identity that a successor must use: stable group ID,
owner Phase, repository, path, suite or scripted identity, and scenario IDs.
The registry does not select a concrete schema, API, type, wire format,
archive layout, resolver representation, lifecycle mechanism, policy, or
consumer implementation.

## Interpretation Rules

1. The authoritative acceptance unit is one matrix row below. Its group ID,
   owner Phase, repository, path, suite or script identity, and scenario IDs
   are one frozen identity set.
2. A Component ID identifies an independently identified Component. A role
   names the information payload carried by that Component and is separate
   from implementation technology. Payloads are non-authoritative and
   non-executable.
3. Logical release identity, parent membership, physical artifact identity,
   digest, path, source, provenance, authorization, integrity, and
   availability remain distinct according to the twelve RSC-01 invariants.
4. The profile IDs and canonical Component IDs in this registry are shared
   fixture identities. They do not freeze physical filenames, schema fields,
   archive entries, artifact projections, or successor vocabulary.
5. A matrix path is a future executable-specification path. No path in this
   registry is created or changed by RSC01-B.
6. Every future path is relative to the root of its named repository. In a
   multi-repository cell, a repository label maps the following path and is not
   part of that path.

## Authoritative Acceptance Matrix

Each row has the common RED-to-GREEN rule stated in the [failing-first
protocol](#failing-first-protocol). The scenario IDs are stable and must
remain adjacent to the registered suite or scripted identity when a successor
materializes them.

| Stable group ID | Owner Phase | Repository or repositories | Exact future path or paths | Exact suite or scripted identity | Exact scenarios | Applicable frozen invariants | Failure-to-green rule |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RSC02-IDENTITY-CODEC | 58.1 | cloud-native-component-framework | `src/test/scala/org/goldenport/cncf/component/ComponentSubcomponentCompositionCodecSpec.scala` | `org.goldenport.cncf.component.ComponentSubcomponentCompositionCodecSpec` | RSC02-AC-01 canonical parent/child membership round-trips without duplicated identity<br>RSC02-AC-02 role and technology remain separate; logical and physical identities remain separate; external-platform deployment remains explicit; logical and physical provenance evidence are retained<br>RSC02-AC-03 duplicate, cyclic, conflicting, malformed, unsafe, incompatible, and unknown-field cases follow the frozen compatibility boundary | Invariants 1, 2, 3, 4, 5, 6, 7, 10, 11, 12 | Materialize this exact path, suite, and scenarios, capture attributable RED evidence before production implementation, then capture GREEN evidence on the same identity. |
| RSC03-PACKAGING-PUBLICATION | 58.2 | cozy and sbt-cozy | cozy: `src/test/scala/cozy/archive/SubcomponentReleasePackagingSpec.scala`<br>sbt-cozy: `src/sbt-test/cozy/subcomponent-release-publication` | `cozy.archive.SubcomponentReleasePackagingSpec` and scripted identity `subcomponent-release-publication` | RSC03-AC-01 deterministic parent/child CAR and payload packaging with exact post-package integrity evidence<br>RSC03-AC-02 incomplete or invalid required membership is never repository-visible<br>RSC03-AC-03 local/remote publication, repeated-build, and source/archive evidence remain equivalent | Invariants 1, 2, 3, 5, 7, 8, 10, 11, 12 | Materialize both exact identities, capture attributable RED evidence before packaging/publication implementation, then capture GREEN evidence on the same identities. |
| RSC05-MODE-COMPOSITION | 58.4 | cloud-native-component-framework | `src/test/scala/org/goldenport/cncf/cli/ComponentResourceOperationModePolicySpec.scala` | `org.goldenport.cncf.cli.ComponentResourceOperationModePolicySpec` | RSC05-AC-01 Develop/Test/Demo/Production select deterministic runtime-owned composition policies<br>RSC05-AC-02 external-platform activation/deployment is explicit and Production never automatically resolves SourceCode<br>RSC05-AC-03 OperationMode does not enter Component-domain APIs or grant authority | Invariants 1, 2, 3, 5, 6, 8, 9, 12 | Materialize this exact path, suite, and scenarios, capture attributable RED evidence before mode/composition implementation, then capture GREEN evidence on the same identity. |
| RSC06-AUTHORIZATION-INTEGRITY | 58.5 | cloud-native-component-framework | `src/test/scala/org/goldenport/cncf/component/repository/ComponentResourceAuthorizationSpec.scala` | `org.goldenport.cncf.component.repository.ComponentResourceAuthorizationSpec` | RSC06-AC-01 authorization, parent/release evidence, digest, and signature are checked before content exposure<br>RSC06-AC-02 traversal, symlink escape, archive ambiguity, corrupt cache, and unauthorized source reject safely<br>RSC06-AC-03 diagnostics do not leak content/credentials/host paths/secrets and registry membership grants no Operation/MCP/activation authority | Invariants 3, 5, 7, 8, 9, 10, 11, 12 | Materialize this exact path, suite, and scenarios, capture attributable RED evidence before authorization/integrity implementation, then capture GREEN evidence on the same identity. |
| RSC07-LIFECYCLE-OBSERVABILITY | 58.6 | cloud-native-component-framework | `src/test/scala/org/goldenport/cncf/component/repository/ComponentResourceLifecycleSpec.scala` | `org.goldenport.cncf.component.repository.ComponentResourceLifecycleSpec` | RSC07-AC-01 shared immutable artifacts, in-flight resolution, refresh, and invalidation have deterministic concurrent ownership<br>RSC07-AC-02 release/unload/shutdown and cancellation/refresh races preserve actual terminal outcomes<br>RSC07-AC-03 bounded non-sensitive CallTree/metrics/diagnostics isolate unrelated release failures | Invariants 1, 3, 6, 7, 8, 9, 10, 11, 12 | Materialize this exact path, suite, and scenarios, capture attributable RED evidence before lifecycle implementation, then capture GREEN evidence on the same identity. |
| RSC04-RESOLUTION-PROVENANCE | 58.3 | cloud-native-component-framework | `src/test/scala/org/goldenport/cncf/component/repository/ResolvedComponentResourcesSpec.scala` | `org.goldenport.cncf.component.repository.ResolvedComponentResourcesSpec` | RSC04-AC-01 the embedded primary and each independently identified child Component/CAR resolve across embedded/development/expanded/local/cache/remote/offline sources with deterministic precedence and logical plus physical provenance<br>RSC04-AC-02 availability, integrity, and authorization outcomes remain orthogonal<br>RSC04-AC-03 discovery neither activates a child nor deploys an external-platform payload | Invariants 1, 3, 5, 7, 8, 9, 10, 11, 12 | Materialize this exact path, suite, and scenarios, capture attributable RED evidence before resolution implementation, then capture GREEN evidence on the same identity. |
| RSC08-CONSUMER-PROJECTION | 58.7 | cloud-native-component-framework | `src/test/scala/org/goldenport/cncf/projection/ResolvedComponentResourcesConsumerSpec.scala` | `org.goldenport.cncf.projection.ResolvedComponentResourcesConsumerSpec` | RSC08-AC-01 Help and Admin consume identical parent, Subcomponent Component, and Subsystem identity/role/availability/integrity/provenance projections<br>RSC08-AC-02 neither consumer scans CAR/Subcomponent/cache/repository/development paths directly<br>RSC08-AC-03 inventory visibility and authorized content access remain distinct | Invariants 1, 2, 3, 6, 7, 9, 10, 11, 12 | Materialize this exact path, suite, and scenarios, capture attributable RED evidence before consumer implementation, then capture GREEN evidence on the same identity. |
| RSC09-CROSS-REPOSITORY | 58.8 | sbt-cozy, cncf-samples, and textus-sample-apps | sbt-cozy: `src/sbt-test/cozy/component-subcomponent-end-to-end`<br>cncf-samples: `samples/11.f-component-subcomponent-acceptance`<br>textus-sample-apps: `cwitter/subsystem/src/test/scala/cwitter/ComponentSubcomponentCompositionAcceptanceSpec.scala` | scripted identity `component-subcomponent-end-to-end`; sample identity `11.f-component-subcomponent-acceptance`; `cwitter.ComponentSubcomponentCompositionAcceptanceSpec` | RSC09-AC-01 all ten shared profiles cover parent-only, split, restricted, development, repository, offline, production, and consumer behavior<br>RSC09-AC-02 missing/incompatible/duplicate/unsafe/stale/corrupt and lifecycle/concurrency outcomes remain deterministic<br>RSC09-AC-03 cross-repository package/publish/resolve/consume evidence agrees and external deployment has no CNCF fallback | Invariants 1 through 12 | Materialize all three exact identities, capture attributable RED evidence before cross-repository implementation, then capture GREEN evidence on the same identities. |

There are exactly eight authoritative rows: one each for the RSC02 through
RSC09 acceptance groups. The owner Phase in each row owns the corresponding
failing-first evidence and successor implementation within this boundary.
Previously accumulated CNCF/Cozy suites may be selected by Phase 58.8 only as
supplementary validation evidence. They are not part of the RSC09 frozen
executable identity unless this registry is explicitly updated under Change
Control.

## Shared Fixture Identities

The shared fixture namespace and version are fixed for acceptance traceability:

| Fixture field | Frozen value |
| --- | --- |
| Namespace | `org.goldenport.cncf.phase58` |
| Version | `0.1.0-SNAPSHOT` |

| Canonical Component ID | Role |
| --- | --- |
| `org.goldenport.cncf.phase58.RscParent` | parent |
| `org.goldenport.cncf.phase58.RscDocumentation` | Documentation |
| `org.goldenport.cncf.phase58.RscSourceCode` | SourceCode |
| `org.goldenport.cncf.phase58.RscWebPresentation` | presentation |

Payloads are non-authoritative. Role is separate from technology. These
identities do not freeze physical filenames, schema fields, or artifact
projections.

The shared profile IDs are:

1. `embedded-primary`
2. `parent-documentation-source`
3. `parent-external-platform`
4. `restricted-source`
5. `development-override`
6. `repository-online-offline`
7. `production-primary-only`
8. `failure-matrix`
9. `lifecycle-concurrency`
10. `help-admin-consumer`

## Failing-First Protocol

RSC01-B creates no executable tests and makes no red-build claim. Before its
first production implementation edit, each successor Phase must materialize
the exact registered path, suite or script identity, group ID, and scenario
IDs for its matrix row. Scala behavior specifications use the repository
standard AnyWordSpec/Matchers/GivenWhenThen form, with explicit Given/When/
Then adjacent to setup, action, and expectation. Scripted acceptance uses its
repository's executable scripted contract.

The successor captures attributable failing-first evidence for the registered
scenario before behavior implementation, then captures green evidence on that
same identity. Pending, ignored, canceled, `pendingUntilFixed`, source-text
assertions, metadata-only tests, and renamed or unregistered suites are not
failing-first evidence. A required identity or path change returns the
successor to parent PLAN and updates this RSC01-B authority explicitly; no
silent rename or local substitute is allowed.

## Successor Ownership

Phase 58.1 owns identity/composition codec evidence; 58.2 owns packaging and
publication evidence; 58.3 owns resolution/provenance evidence; 58.4 owns
mode/composition evidence; 58.5 owns authorization/integrity evidence; 58.6
owns lifecycle/observability evidence; 58.7 owns Help/Admin projection
evidence; and 58.8 owns cross-repository evidence. Each successor chooses
implementation vocabulary inside the twelve invariants and the exact
registered identity.

## Traceability to the Twelve RSC-01 Invariants

The matrix's invariant column is the authoritative traceability for each
acceptance group. The invariant numbers refer to the twelve frozen decisions
in the [architecture note](component-subcomponent-architecture-implementation.md#frozen-rsc-01-decisions-and-invariants):

1. Every declared Subcomponent is an independent CNCF Component with
   canonical identity and its own CAR.
2. Documentation and SourceCode are initial non-authoritative,
   non-executable payload roles, not Components themselves.
3. Parent membership references child identity without duplicating mutable
   identity or granting authority.
4. Role is separate from implementation technology.
5. External-platform children retain CNCF CAR identity/metadata and require
   explicit platform deployment; CNCF does not silently fall back.
6. Parent relationship is separate from general Subsystem membership.
7. Logical release identity is separate from physical artifact identity,
   digest, path, and provenance.
8. Publication completeness is separate from runtime activation.
9. Discovery/availability is separate from activation.
10. Resolved provenance retains logical identity and physical source evidence.
11. Authorization, integrity, and availability are orthogonal.
12. Concrete schema, APIs, types, wire formats, lifecycle machinery, and
    resolver implementation belong to successors and cannot reinterpret the
    preceding invariants.

## Non-Goals

- No product, source, test, build, or generated edits are part of RSC01-B.
- No canonical docs/design or docs/spec promotion occurs before Phase 58.9.
- No concrete schema, API, type, wire, archive, resolver, lifecycle, or
  policy implementation is selected here.
- No external repository mutation, SBT, validation, review, commit, publish,
  deploy, push, or successor execution is authorized.
- No acceptance identity is renamed or locally substituted.

## Change-Control Rule

This registry is a frozen RSC-01 authority. A new public, schema, API,
repository, packaging, runtime, policy, or successor-vocabulary decision is
outside RSC01-B and requires parent PLAN and explicit authority update. A
required path, suite, script, scenario, group, fixture identity, or profile
identity change must be recorded here before the affected successor proceeds.
Phase 58 remains `status=in_progress` and stops before Phase 58.1 starts.
