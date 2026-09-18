# Phase 63.1 Checklist - StateMachine Generation and Atomic CNCF Execution

status=complete
phase=[Phase 63.1 - StateMachine Generation and Atomic CNCF Execution](phase-63.1.md)

This ledger owns only `SMR-04` and `SMR-05`. Phase 63 is the required
contract/normalization predecessor; Phase 63.2 owns post-commit delivery,
observability, and final acceptance.

## SMR-04: SimpleModeler Generation and ABI

Stage Status:
- Current status: DONE; independent full review passed, release candidate prepared.
- Owner: SimpleModeler and generated-component maintainers
- Entry rule: Phase 63 is closed.
- Completion rule: Generated code carries executable typed transition
  definitions and stable metadata without required no-op/raw-string behavior.

- [x] Generate canonical transition definitions with stable identities,
  ordering, machine version, initial/final states, hierarchy/history, typed
  trigger context, and source location.
- [x] Generate typed predicate programs and named guard/action references.
- [x] Generate executable local actions or explicit resolver bindings instead
  of placeholders.
- [x] Preserve source/target/event/priority/declaration order through metadata,
  Help, Record, JSON, and ABI/version admission.
- [x] Reject generation when required transition semantics are unsupported.
- [x] Add deterministic generated-source, metadata, ABI, compile, Record, and
  JSON Executable Specifications.

Evidence:
- SimpleModeler Step commit `8e716c7915bbfd93a7d60fdfbea24bbe37c96519`
  (`Phase 63.1 SMR-04: generate typed state machine definitions`).
- CNCF Step commit `e3398c73f5ecad81e71e8bb9ab313eaa7729738d`
  (`Phase 63.1 SMR-04: project generated state machine definitions`).
- The accepted SMR-04 review and focused re-review ledger covers the generated
  source, metadata, Record/JSON projection, and typed provider bootstrap
  acceptance surface.

#### Nonblocking focused consumer compatibility diagnosis

This is a separately authorized Goldenport producer follow-up, not an
unfinished SMR-04 completion item. It remains intentionally open and does not
change this Phase's closure scope.

- [ ] Repair the Goldenport local Ivy descriptor for the existing
  `goldenport-scala-lib` development dependency, republish only its local
  SNAPSHOT artifact, and rerun the `state-machine-history-runtime` Cozy
  fixture. The two SmartDox local-publish attempts left the same failure:
  Goldenport itself lacks Ivy configuration `master`. SmartDox source was
  restored cleanly. This follow-up requires separate authorization because it
  changes the Goldenport producer; it excludes version changes, remote
  publication, SmartDox behavior, and Phase 63.2 work.

## SMR-05: Atomic CNCF Transition Execution

Stage Status:
- Current status: DONE; independent full review passed, release candidate prepared.
- Owner: CNCF StateMachine, Aggregate, persistence, and UnitOfWork maintainers
- Entry rule: SMR-04 is DONE.
- Completion rule: One CNCF path plans and commits an admitted transition
  exactly once with atomic local state/effect behavior.

- [x] Reuse canonical core selection, load current state, validate source
  applicability, evaluate guards, and construct exactly one candidate plan.
- [x] Construct candidate entity state and run exit/transition/entry local
  effects without mutating persisted state before admission.
- [x] Place candidate state, local effects, persistence, named shallow-history
  writes, and outcome staging in one UnitOfWork.
- [x] Roll back state and staged success data for action, persistence,
  cancellation, or interruption failure.
- [x] Enforce the machine or reject an unsafe bypass on create, update/save,
  patch, command, direct/unversioned, retry, and compatibility paths.
- [x] Add exactly-once, atomicity, ordering, retry, replay, cancellation, and
  rollback specifications through the real generated-provider bootstrap path.

Evidence:
- CNCF Step commit `4aba61351526bdd13c389fbf6fe7a3aff8b5926e`
  (`Phase 63.1 SMR-05: make detached transition persistence atomic`).
- Focused acceptance passed on the committed candidate tree:
  `sbt --batch testOnly org.goldenport.cncf.unitofwork.UnitOfWorkStateMachineHookSpec`.
- The accepted focused re-review bundle
  `fb3bba603eefe4287c5f9070b02cec72a6aebef1dc579cb6697750c2b89c7560`
  records no remaining Current Boundary Blockers.

## Release Readiness

- Independent full Phase review: CLEAN, recorded as
  `review-disposition-sha256-ad3060218a20877d5df7a6ee4ab46526d2d960ee0dc93d59ad75c0e4f6a465b3`.
- Full-suite ownership is aggregate-deferred to Phase 63.2. No Phase 63.1
  repository full suite is claimed or run.
- The Goldenport Ivy-descriptor follow-up remains a separately authorized,
  nonblocking producer compatibility item and is not part of this Phase's
  closure scope.
- The final local release record binds the compatible legacy Phase 63 aggregate
  predecessor handoff. This is workflow-history evidence, not an SMR-04 or
  SMR-05 behavior blocker.

## Forced Release Exception

- Release disposition: `forced / exceptions-recorded`.
- Exception: the ordinary release adapter requires a duplicate SimpleModeler
  producer full suite even though this sealed `final-only` Phase delegates the
  single aggregate full suite to Phase 63.2.
- Preserved assurance: both Step commits, the focused
  `UnitOfWorkStateMachineHookSpec` pass, and the independent full Phase review.
- Deliberately not claimed: a Phase 63.1 repository full suite or ordinary
  aggregate-predecessor acceptance. Phase 63.2 owns the aggregate suite and
  must make any forced-predecessor acceptance explicit.
