# Phase 55 Hygiene Follow-up Ledger

## HYG-P55-001 — Subsystem public constructor parameter naming

- Status: RESOLVED
- Repository and location: `cloud-native-component-framework`,
  `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`, primary
  constructor parameter labels.
- Evidence: the established public labels `scopecontext`, `httpdriver`,
  `runmode`, and `operationevaluationcrosssinkpolicyoption` do not meet the
  canonical public-parameter camelCase rule.
- Category and risk: compatibility/naming hygiene; source callers can use these
  labels as named arguments, so an uncoordinated rename is a source-compatibility
  break.
- Phase boundary: GCF-09H changes the SystemNode configuration authority. Its
  target-file whole-file check found this debt, but correcting it requires
  coordinated caller migration beyond the same-file incidental-cleanup limit.
- Proposed boundary: a dedicated compatibility-preserving constructor API
  migration that inventories named-argument callers and provides an explicit
  deprecation/migration plan before renaming the canonical labels.
- Resolution reference: `cncf-goal-task-cncf-hygiene-20260811-01`.
- Resolution evidence: `cncf-goal-task-cncf-hygiene-20260811-01` applies the
  source-compatible `@deprecatedName` constructor migration and updates the
  admitted factory callsites. Focused validation passed 126/126 tests in
  invocation `15908-20260811T071918Z`; the final focused re-review passed. The
  task commit is gated on the repository full `test`, so this resolution is not
  persisted if final validation fails.

## HYG-P55-002 — CollaboratorRepository naming hygiene

- Status: RESOLVED
- Repository and location: `cloud-native-component-framework`,
  `src/main/scala/org/goldenport/cncf/backend/collaborator/CollaboratorRepository.scala`,
  private parameters and local values.
- Evidence: private parameters/local values including `jarPath`,
  `collaboratorApiUrl`, `classNames`, `className`, `entryName`, and
  `withoutExtension` do not use the required private-parameter/local flatcase
  naming form.
- Category and risk: naming hygiene; no GCF-09J behavior or validation defect
  is known.
- Phase boundary: the file is outside the frozen GCF-09J target-program set;
  incidental cleanup is therefore not admitted to this review-fix.
- Proposed boundary: a dedicated Phase 55 hygiene task that reviews and repairs
  the complete file without mixing it into collaborator bootstrap behavior.
- Resolution reference: `cncf-goal-task-cncf-hygiene-20260811-01`.
- Resolution evidence: `cncf-goal-task-cncf-hygiene-20260811-01` completes
  the private/local flatcase migration within `CollaboratorRepository` only.
  Focused validation passed 126/126 tests in invocation
  `15908-20260811T071918Z`; the final focused re-review passed. The task commit
  is gated on the repository full `test`, so this resolution is not persisted
  if final validation fails.

## HYG-P55-003 — Retired configuration symbols in dormant source comments

- Status: RESOLVED
- Repository and locations: `cloud-native-component-framework`,
  `src/main/scala/org/goldenport/cncf/component/Component.scala` and
  `src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala`, dormant
  commented-out configuration blocks.
- Evidence: the comments reference deleted
  `org.goldenport.cncf.config.model.Config` and `ResolvedConfig` symbols.
- Category and risk: non-executable source hygiene; misleading to future
  maintainers but not a GCF-09K retirement safety defect.
- Phase boundary: both large files are outside the frozen GCF-09K target set.
- Proposed boundary: a dedicated hygiene task that reviews the complete dormant
  blocks and removes them or updates any retained example to generic
  configuration terminology.
- Resolution reference: `cncf-goal-task-cncf-hygiene-20260811-01`.
- Resolution evidence: `cncf-goal-task-cncf-hygiene-20260811-01` deliberately
  removes the complete dormant construction/configuration comment blocks from
  `Component.scala` and `ExecutionContext.scala`, including adjacent obsolete
  overload sketches and `_resolve_core`, while retaining executable
  configuration paths. Focused validation passed 126/126 tests in invocation
  `15908-20260811T071918Z`; the final focused re-review passed. The task commit
  is gated on the repository full `test`, so this resolution is not persisted
  if final validation fails.

## HYG-P55-004 — ArtScene test-support public parameter naming

- Status: OPEN (discovered 2026-08-04 during GCF-10D RE_REVIEW)
- Repository and location: `textus-art-scene`,
  `src/test/scala/org/simplemodeling/textus/artscene/ArtSceneTestSupport.scala`,
  public `componentUnderTest` and `fixture` parameter labels and their named
  callers.
- Evidence: public helper labels including `subsystemname`, `datastorepath`,
  `facilitymasterrecords`, `notificationhandoff`, `httpdriver`,
  `securitycontext`, and `runtimecontext` do not use canonical camelCase;
  named-argument callers extend beyond the frozen GCF-10D target set.
- Category and risk: test-support naming/compatibility hygiene; renaming only
  the helper declarations would break source-level named arguments across the
  wider ArtScene specification suite.
- Phase boundary: GCF-10D repaired runtime subject/admission behavior in a
  seven-file corrective Step. Completing this naming migration requires a
  coordinated cross-file caller update that is not needed for Phase 55
  behavior or trustworthy release validation.
- Proposed boundary: a dedicated ArtScene test-support naming migration that
  inventories every named caller, renames the complete public helper surface,
  and validates the full ArtScene specification suite.
- Resolution reference: none.
