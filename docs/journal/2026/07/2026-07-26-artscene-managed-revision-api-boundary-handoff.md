# ArtScene Managed Revision API Boundary Handoff

date = 2026-07-26
status = handoff
source = textus-art-scene compile investigation
related_phase = CNCF Phase 50

## Purpose

This handoff records an application-facing API mismatch exposed by rebuilding
Textus ArtScene against the current CNCF `0.5.1-SNAPSHOT`.

The intended boundary is:

- CNCF owns the `SimpleEntity.revision` lifecycle;
- ordinary application updates do not reconstruct or pass an expected
  revision;
- an ingress route that requires a previously observed revision selects that
  strict behavior explicitly; and
- application and generated code use managed Entity/Aggregate DSL operations
  rather than constructing revision-aware UnitOfWork primitives directly.

ArtScene does not intentionally opt into an application-supplied OCC contract.
Its compile errors must therefore not be fixed by adding revision parameters to
ArtScene commands.

## Reproduction

Repository:

```text
/Users/asami/src/dev2026/textus-art-scene
```

Build dependency:

```yaml
org.goldenport::goldenport-cncf:0.5.1-SNAPSHOT
```

Regenerate and compile:

```bash
sbt --batch cozyGenerate
sbt --batch compile
```

The compile currently reports 17 revision-related API errors:

- 2 in Cozy-generated Aggregate update adapters;
- 5 in handwritten ArtScene calls to the former
  `aggregate_update(name, id, command, action)` shape; and
- 10 in handwritten direct construction of the former
  `EntityStoreUpdateById(id, patch, persistent)` shape.

Representative generated failure:

```scala
r <- aggregate_update(
  "facility",
  id,
  "updateFacility",
  current.updateFacility(action.entity.toRecord())(using executionContext)
)
```

The current CNCF method interprets the fourth argument as
`expectedRevision: EntityRevision`, so the aggregate command result is rejected
as the wrong type.

Representative handwritten failure:

```scala
UnitOfWorkOp.EntityStoreUpdateById(
  id,
  patch,
  summon[EntityPersistentUpdate[FacilityUpdate]]
)
```

The current low-level operation requires
`expectedRevision: Option[EntityRevision]` before the persistent codec.

## Confirmed CNCF Behavior

The ordinary protected Entity DSL already has the required application-facing
semantics:

```scala
entity_update(id, patch)
```

It constructs `EntityStoreUpdateById` with `expectedRevision = None`. The
Entity/UnitOfWork/DataStore boundary then owns managed revision acquisition,
policy selection, comparison when required, and advancement.

The Aggregate DSL also has a managed command route:

```scala
aggregate_command(name, id, command) { aggregate =>
  aggregate.update(...)
}
```

This route resolves the aggregate root together with its authoritative
revision and persists the updated root with that revision. The application
command does not receive or provide it.

By contrast, the current `aggregate_update` API requires an explicit
`EntityRevision`. Its name does not communicate that it is the explicit
revision route, and existing Cozy/Application callers selected it as the
ordinary Aggregate update operation.

## Important Policy Distinction

The accepted Phase 50 direction separates:

```text
revision lifecycle
  framework-managed for every admitted SimpleEntity

concurrency policy
  None or Optimistic

revision precondition source
  Managed or ObservedRequired
```

Application-visible opt-in concerns `ObservedRequired`: an adapter carries a
revision previously observed by the caller. The ordinary `Managed` route must
not add revision to business parameters.

The current source sets `EntityConcurrencyPolicy.default` to `Optimistic`, not
`None`. This is compatible with application-transparent managed revision, but
it is not literally an opt-in concurrency-policy default. CNCF design must keep
this distinction explicit when deciding whether "OCC is opt-in" means:

- observed revision transport is opt-in; or
- the concurrency policy itself defaults to `None`.

Do not resolve that terminology by exposing `expectedRevision` to ordinary
application code.

## Additional ArtScene Model Finding

The regenerated ArtScene descriptors currently classify all seven Entities as:

```scala
revisionModelKind = Some(EntityRevisionModelKind.NonSimpleEntity)
revisionRepresentation = None
```

Generated `Facility` and `Exhibition` classes extend `EntityPersistable`, but
not `org.simplemodeling.model.SimpleEntity`. Their CML Entity declarations do
not currently declare:

```text
extends = ["SimpleEntity"]
```

This is a separate model/generator issue. If ArtScene Entities are intended to
use the canonical `SimpleEntity` path, Cozy/CML must emit that inheritance and
the corresponding Embedded revision representation. CNCF must not silently
reinterpret a declared `NonSimpleEntity` with no revision representation as a
`SimpleEntity`.

Adding inheritance changes the persisted model contract and must not be used
as an unreviewed compile workaround.

## Required CNCF Work

### 1. Fix the managed application-facing API boundary

- Keep ordinary Entity update available as `entity_update(id, patch)` without
  an application revision parameter.
- Keep ordinary Aggregate command available without an application revision
  parameter.
- Ensure both routes obtain and propagate managed revision internally.
- Do not require generated business operation inputs to contain
  `expectedRevision`, `cncfRevision`, or a replacement alias.

### 2. Make explicit revision APIs unambiguous

Review the public/protected naming and visibility of:

```text
aggregate_update
aggregate_command
EntityStoreUpdateById
```

An operation requiring an application-supplied revision must be clearly
distinguishable from the managed route. Possible directions include:

- reserve `aggregate_command` as the only ordinary Aggregate mutation route
  and prevent generators/applications from selecting the explicit primitive;
  or
- give the explicit route an observed/expected-revision-specific name.

Do not restore a broad compatibility overload that silently bypasses the
managed revision contract.

### 3. Provide a complete protected DSL

ArtScene directly constructed UnitOfWork operations because reusable logic
needed typed Entity patch updates inside an ActionCall program.

Confirm that every supported application mutation has a protected DSL method
that preserves:

- canonical Entity id handling;
- component datastore selection;
- authorization;
- managed revision policy;
- write policy;
- UnitOfWork execution; and
- structured failure behavior.

If the existing `entity_update` methods already cover this use case, document
and test that route. If helper placement prevents shared ActionCall logic from
using it, fix the protected DSL boundary instead of encouraging direct
`UnitOfWorkOp` construction.

### 4. Reject invalid revision representation early

- A `SimpleEntity` must use Embedded managed revision.
- A non-`SimpleEntity` may use Detached revision only through explicit
  declaration.
- A non-`SimpleEntity` Aggregate requiring revision semantics but declaring no
  representation must fail during generation/assembly admission with an
  actionable diagnostic.
- It must not first appear as an unrelated Scala argument-count error in a
  downstream CAR.

## Cozy Responsibility

Cozy currently emits:

```scala
current <- aggregate_load[Aggregate](id)
r <- aggregate_update(name, id, command, current.update(...))
```

It must generate the CNCF-managed Aggregate command route instead. The
generated adapter must not transport an application revision.

Cozy also has an in-progress Phase 25 change removing retired
`cncfRevision`, `snapshot.token`, and `EntityMutationExpectation` from standard
SimpleEntity CRUD generation. That work does not yet update the Aggregate
template above.

Required Cozy regression coverage:

- generated ordinary Entity CRUD compiles against current CNCF without a
  revision business parameter;
- generated Aggregate command/update compiles against current CNCF without a
  revision business parameter;
- generated metadata classifies explicit `SimpleEntity` inheritance as
  Embedded;
- generated metadata does not classify an ordinary Entity as SimpleEntity by
  guesswork.

## ArtScene Responsibility

After CNCF and Cozy contracts are fixed and locally available:

- regenerate ArtScene;
- replace handwritten `aggregate_update` calls with the managed Aggregate
  command DSL;
- replace direct `EntityStoreUpdateById` construction with the protected
  managed Entity update DSL;
- decide explicitly whether each persisted ArtScene Entity extends
  `SimpleEntity`;
- handle any resulting datastore migration as an ArtScene model migration;
  and
- retain explicit revision only for a separately designed strict ingress or
  conditional-transition use case.

## Acceptance Criteria

- A generated ordinary SimpleEntity update compiles without an
  `expectedRevision` business parameter.
- A handwritten ordinary Entity patch update compiles through the protected
  DSL without constructing `EntityStoreUpdateById` directly.
- A generated Aggregate command compiles and acquires the authoritative root
  revision inside CNCF.
- Managed optimistic execution, when selected, compares and advances revision
  without application revision plumbing.
- `ObservedRequired` remains an explicit strict adapter/route contract.
- Invalid non-SimpleEntity revision representation fails at
  generation/assembly admission with a structured diagnostic.
- ArtScene `cozyGenerate` followed by `compile` passes against one aligned CNCF
  and Cozy development toolchain.
- No compatibility overload is added solely to make stale generated or
  application low-level calls compile.

## Relevant Source Locations

```text
CNCF:
  src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala
  src/main/scala/org/goldenport/cncf/entity/EntityMutationPolicy.scala
  src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala

Cozy:
  src/main/scala/cozy/modeler/Modeler.scala
  src/test/scala/cozy/modeler/ModelerScalaGenerationSpec.scala

ArtScene:
  src/main/cozy/textus-art-scene.cml
  src/main/scala/org/simplemodeling/textus/artscene/impl/ComponentFactory.scala
  target/scala-3.3.8/src_managed/main/org/simplemodeling/textus/artscene/ArtSceneComponent.scala
```

## Decision Update: Keep Update and Command Separate

The initial handoff proposed moving ordinary generated Aggregate updates to
`aggregate_command`. Subsequent review found that this would collapse two
different application mutation styles into one API. That proposal is therefore
superseded by the following direction.

`aggregate_update` and `aggregate_command` remain separate:

```text
aggregate_update
  = CRUD/Form/REST-style mutation where the updated Aggregate value is already
    constructed

aggregate_command
  = domain-command mutation where CNCF resolves the authoritative Aggregate
    and applies a command to its current state
```

OCC is an independent policy axis and must not determine whether an application
uses update or command:

```text
mutation style
  = update | command

concurrency policy
  = None | Optimistic

revision precondition source
  = Managed | ObservedRequired
```

The ordinary protected DSL is revision-transparent:

```scala
aggregate_update(name, id, commandName, updated)

aggregate_command(name, id, commandName) { current =>
  command(current)
}
```

Neither ordinary method accepts an application revision parameter. CNCF
continues to initialize and advance managed revision independently of whether
ordinary OCC is enabled.

Observed revision is an explicit strict route. If a protected DSL entry is
required for framework adapters, its name must expose that contract:

```scala
aggregate_update_observed(name, id, commandName, expectedRevision, updated)

aggregate_command_observed(name, id, commandName, expectedRevision) { current =>
  command(current)
}
```

These observed methods are not the standard generator/application path. They
are intended for a route or adapter that has deliberately selected
`ObservedRequired`, such as an ETag or previously rendered strict edit form.

The ordinary concurrency-policy default changes to `None`. Applications opt
into `Optimistic` explicitly at Entity or collection scope. This does not
disable the revision lifecycle: successful persistent mutations still advance
the authoritative revision. It only prevents the framework from making the
large majority of ordinary applications participate in an OCC contract they
did not select.

## Classification: Phase 50 Specification Bug

This correction is classified as a Phase 50 specification bug, not as an
ArtScene compatibility request.

Phase 50 correctly established that:

- revision lifecycle is framework-managed;
- ordinary application business parameters do not carry revision; and
- observed revision is an explicit strict ingress concern.

However, the accepted Phase 50 contract also:

- changed the existing ordinary `aggregate_update` protected API into a
  revision-requiring API;
- retained `Optimistic` as the ordinary default even though OCC is expected to
  be an explicit application choice; and
- did not preserve update and command as separate mutation semantics
  independent of concurrency policy.

Those choices contradict the application-transparent revision boundary and
caused ordinary generated and handwritten ArtScene updates to fail at compile
time. Restoring a revision-free `aggregate_update`, retaining a revision-free
`aggregate_command`, making OCC opt-in, and naming observed-revision routes
explicitly are therefore corrections to the Phase 50 specification itself.
They are not compatibility overloads for stale application code.

The CNCF full suite did not expose this defect because the closure boundary did
not compile the affected ArtScene Aggregate and handwritten patch-update
surfaces against the final CNCF API. The post-close correction must add that
cross-repository compile and smoke boundary to prevent the same class of
specification/API mismatch from passing closure again.

Phase 50 remains closed as a historical execution record. Its dashboard,
checklist, completed-history entry, design, and specification receive dated
post-close correction annotations that identify this specification bug and
the evidence that closes it. The original historical statements are not
silently rewritten.

## Correction Implementation Plan

This work is a post-close correction to the Phase 50 application API boundary.
Phase 50 remains a historical closed phase; its dashboard and checklist receive
a dated correction annotation rather than having their original execution
record rewritten.

### 1. Freeze the Executable Specification

Add failing-first CNCF Executable Specifications that establish:

- ordinary `aggregate_update` accepts no revision and preserves authorization,
  UnitOfWork, datastore selection, revision maintenance, write policy,
  observability, and structured failure behavior;
- ordinary `aggregate_command` accepts no revision, resolves the authoritative
  Aggregate, applies the command, and persists through the same managed
  revision boundary;
- update and command remain behaviorally distinct mutation styles;
- `EntityConcurrencyPolicy.None` is the deterministic ordinary default;
- explicit `Optimistic` policy performs managed revision comparison without
  adding revision to business parameters;
- `ObservedRequired` is available only through an explicitly selected strict
  adapter/DSL route;
- missing or stale observed revision fails structurally;
- revision advances under both `None` and `Optimistic`; and
- application code does not construct `EntityStoreUpdateById` directly when
  the protected patch-update DSL covers the operation.

Extend property-based coverage across:

```text
update | command
  x
None | Optimistic
  x
Managed | ObservedRequired where valid
```

Keep `None + ObservedRequired` invalid unless the static specification is
deliberately revised to define a meaningful strict last-write-wins contract.

### 2. Correct the CNCF Protected DSL

- Restore `aggregate_update` as the canonical revision-free ordinary update
  method.
- Retain `aggregate_command` as the canonical revision-free domain-command
  method.
- Rename the current revision-requiring Aggregate update route to an explicit
  observed-revision name rather than retaining an ambiguous overload.
- Add an observed command route only when an ingress adapter has a proven use
  case and executable acceptance.
- Keep revision-aware persistence helpers below the protected application DSL;
  do not expose raw UnitOfWork construction as the recommended component API.
- Confirm that `entity_update(id, patch)` is usable from shared ActionCall
  behavior and document it as the replacement for direct
  `EntityStoreUpdateById` construction.
- Change the ordinary concurrency default to `EntityConcurrencyPolicy.None`
  while preserving framework-managed revision initialization and advancement.
- Preserve Conditional Transition's unconditional authoritative revision
  comparison; this correction does not weaken its exactly-one-winner contract.

### 3. Correct Generation and Admission

Cozy generation must select the mutation API from operation semantics, not from
OCC:

- generated CRUD/Aggregate update uses `aggregate_update`;
- generated domain command uses `aggregate_command`;
- neither ordinary generated request contains `expectedRevision`,
  `cncfRevision`, or a replacement alias;
- a generated strict adapter uses an observed route only when its descriptor
  explicitly selects `ObservedRequired`; and
- generated `SimpleEntity` metadata remains Embedded while non-`SimpleEntity`
  metadata is never guessed.

CNCF assembly admission must reject an Entity/Aggregate that declares revision
semantics but has no valid Embedded or Detached representation. An ordinary
non-`SimpleEntity` that declares no revision semantics remains unversioned and
must not be rejected merely for lacking a representation.

### 4. Migrate and Verify ArtScene

- Regenerate ArtScene with the corrected Cozy generator.
- Keep generated CRUD-style Aggregate updates on `aggregate_update`.
- Keep handwritten domain behavior on `aggregate_command` where the operation
  genuinely represents a domain command.
- Replace direct `EntityStoreUpdateById` construction with
  `entity_update(id, patch)`.
- Decide explicitly whether each ArtScene Entity extends `SimpleEntity`; do not
  use inheritance as a compile workaround.
- Run `cozyGenerate`, compile, focused tests, and ArtScene application smoke
  against one aligned CNCF/Cozy development toolchain.

### 5. Promote Verified Results to Design and Specification

After implementation behavior is verified, update or add canonical documents:

- add `docs/design/entity-and-aggregate-mutation-api.md` for the architectural
  separation of update, command, revision lifecycle, concurrency policy, and
  observed transport;
- add `docs/spec/entity-and-aggregate-mutation-api.md` for the normative method
  contracts, policy matrix, defaults, invalid combinations, and generator
  requirements;
- update
  `docs/design/entity-conflict-and-conditional-transition.md` and
  `docs/spec/entity-conflict-and-conditional-transition.md` where their
  current `Optimistic` default or transport wording is superseded;
- annotate `docs/notes/aggregate-method-implementation-strategy.md` with the
  confirmed update/command distinction rather than silently rewriting its
  historical reasoning;
- update the Component developer guide with the protected Entity/Aggregate DSL
  selection rules and prohibition on application-side UnitOfWork primitive
  construction; and
- append a dated post-close correction note to the Phase 50 dashboard and
  checklist, including exact CNCF, Cozy, and ArtScene evidence.

The phase correction is not complete while the latest contract exists only in
this journal, implementation code, or executable tests.

## Correction Completion Criteria

- Ordinary Aggregate update and command compile without revision business
  parameters.
- `aggregate_update` and `aggregate_command` remain separate and have
  documented mutation semantics.
- Ordinary concurrency defaults to `None`; `Optimistic` is explicit opt-in.
- Revision initialization and advancement remain framework-managed under both
  concurrency policies.
- Strict observed revision behavior is explicitly named and selected.
- Cozy generates update versus command from operation semantics.
- ArtScene regenerates, compiles, and passes its selected smoke boundary without
  direct revision-aware UnitOfWork construction.
- Canonical design/specification and the Component developer guide reflect the
  verified implementation.
- Phase 50 contains a post-close correction annotation with exact evidence and
  does not rewrite its original historical execution record.

## Implementation Status: 2026-07-26

CNCF and Cozy now implement the corrected ordinary Aggregate boundary:

- `aggregate_update` and `aggregate_command` accept no revision business
  parameter;
- `EntityConcurrencyPolicy.None` is the ordinary default;
- explicitly observed Aggregate routes and explicit expected-revision
  primitives select optimistic comparison;
- Web Form and strict REST adapters select optimistic comparison for the
  strict mutation attempt without changing the collection default; and
- the provider preserves the explicit effective execution policy.

The focused CNCF Entity/OCC/Web regression passed 361 tests in 7 suites. The
CNCF full validation passed 2523 tests in 363 completed suites with a 4 GB
maximum heap and no failures. Cozy focused generator validation passed 30
tests, the Cozy full suite passed 662 tests in 59 suites, and regenerated
ArtScene compiled 167 Scala sources against locally published CNCF and Cozy
snapshots. The ArtScene focused correction boundary passed all 60 tests in
`ArtSceneManualExhibitionSpec`, `ArtSceneCandidateFilteringSpec`,
`ArtSceneFacilityMasterDataSpec`, and `ArtSceneUpdateFetchSpec`.

The corrected ordinary patch-by-id UnitOfWork route now returns a
revision-transparent `Record`. A separate observed operation returns
`EntityRecordSnapshot` only when the caller supplies an observed revision.
Executable coverage also proves that an ordinary patch succeeds for a
non-`SimpleEntity` collection without a revision binding and does not create a
detached revision field.

CNCF also canonicalizes generated runtime-plan Entity names against Aggregate
metadata. This lets ArtScene's generated `Facility` runtime descriptor resolve
the canonical `facility` collection without an application alias.

ArtScene's handwritten shared update helpers now use `ActionBehavior` with the
originating `ActionCall.Core`. The reusable behavior calls
`entity_update_internal`, so the update stays inside the protected
authorization, UnitOfWork, and observability boundary. Direct
`EntityStoreUpdateById` construction and application-local authorization
reconstruction have been removed.

ArtScene fixture setup now uses an explicitly system-authorized
`EntityStoreSaveUnversioned` with `SeedImport`. This keeps test-data seeding
outside the ordinary application update contract and avoids treating an
unmanaged non-`SimpleEntity` fixture as a versioned save.

The correction is not yet closed because read-only review, any resulting
review-fix, and clean re-review remain outstanding.
