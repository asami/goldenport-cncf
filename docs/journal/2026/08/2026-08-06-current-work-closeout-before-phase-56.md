# Current Work Closeout before Phase 56

date = 2026-08-06
status = handoff-ready
next_phase = [Phase 56 - Namespace-qualified Component Identity and Derived Coordinates](../../../phase/phase-56.md)

## Intent

Bring the currently active CNCF configuration/runtime follow-up and Textus
User Account upgrade work to a coherent stopping point. Do not partially
implement the new Component identity model inside that work. Full
`namespace + id` adoption starts as Phase 56.

## Current Work Boundary

The admitted current scope is the behavior already under development:

- complete the typed Component-parameter path, initialization, resolution,
  runtime projection, datastore/Entity routing, diagnostics, and associated
  documentation/tests in CNCF;
- complete the User Account follow-up needed to build and run against the
  currently selected CNCF/Cozy generation versions; and
- keep existing artifact publication, CAR filename, Maven dependency, and Web
  route behavior stable unless the active work already requires a narrowly
  scoped compatibility correction.

The following work is explicitly deferred to Phase 56:

- adding canonical `namespace` and `id` project/descriptor fields;
- changing `ComponentId` into a namespace-qualified typed identity;
- deriving Maven organization, artifact name, JVM package, generated class,
  CAR filename, repository keys, or routes from the new identity;
- deleting or globally reinterpreting existing `name`, `component`,
  `className`, `scalaPackage`, or organization fields;
- broad first-party CAR, launcher, repository, CBD, BoK, or Web-path
  migration; and
- introducing new normalization heuristics for `UserAccount`,
  `textus-user-account`, or `textus-`-prefixed names.

## Temporary Interpretation of User Account Metadata

If the current User Account upgrade needs
`project.component.name: UserAccount` and the current-format CAR descriptor
`"component": "UserAccount"` to match the existing generated/runtime
contract, those values are a bounded current-schema compatibility setting.
They may close with the current work, but they are not the Phase 56 canonical
schema and must not be documented as the final identity model.

Until Phase 56 starts, retain:

```text
artifact:  textus-user-account
CAR:       textus-user-account-0.6.0-SNAPSHOT.car
existing Maven coordinates and dependency declarations
existing admitted Web paths
```

Do not add a second workaround that copies the same identity into more fields.

## Clean Stopping Point

The current work is ready to hand off when:

- its already-admitted runtime/configuration and User Account upgrade
  scenarios pass at the intended versions;
- source, tests, and documentation agree on the existing identity APIs used
  by that bounded work;
- no Phase 56 schema/type/derivation change is mixed into the diff;
- any unavoidable legacy-name handling is identified as compatibility, not a
  new canonical ID;
- the remaining identity issue is linked to the Phase 56 plan/checklist; and
- review can treat the current change as complete without requiring the
  namespace-qualified redesign.

The current task may then close at that boundary. It should not continue into
CAR-wide renaming, descriptor redesign, or runtime-ID migration merely to
anticipate Phase 56.

## Phase 56 Entry

The next task starts with CID-01 inventory and failing-first contract freeze.
It must inspect the current work as evidence, but it must not assume that any
temporary `project.component.name` value or legacy descriptor field is the
new source of truth.

CID-01 must also freeze the CAR migration cohort by effective version. Every
admitted SNAPSHOT CAR enters the mandatory Phase 56 migration set. An admitted
non-SNAPSHOT CAR remains unchanged for its current release and enters the
lint-visible next-version deferral ledger.

The accepted official Textus example for that next task is:

```text
namespace: org.simplemodeling.textus
id:        UserAccount

Component ID: org.simplemodeling.textus.UserAccount
artifact:     textus-user-account
JVM package:  org.simplemodeling.textus.useraccount
Scala API:    UserAccountComponent
```
