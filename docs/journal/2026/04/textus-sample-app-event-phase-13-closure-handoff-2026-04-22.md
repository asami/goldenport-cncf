# Handoff: `textus-sample-app-event` After CNCF Phase 13 Closure

Target sample:
- `/Users/asami/src/dev2026/textus-sample-app-event`

Related CNCF repo:
- `/Users/asami/src/dev2025/cloud-native-component-framework`

Relevant CNCF commits:
- `651f7f4`
- `4c58db1`
- `78267fe Close Phase 13 runtime contract and bookkeeping`

## 1. Current CNCF Status

Phase 13 is now closed on the CNCF side.

The sample should treat the current framework/runtime contract as stable rather
than provisional.

The following are now fixed on the framework side:

- same-subsystem sync-inline reception semantics
- same-subsystem async/new-job continuation baseline
- child job submission after outer commit
- event/job/admin inspection visibility baseline
- typed policy provenance and precedence
- bundle/participant construction model
- formal runtime componentlet identity
- deterministic malformed-bundle rejection
- companion bundle-factory discovery path

## 2. Runtime Contract the Sample May Rely On

### Construction

The construction model is now explicit:

- one `BundleFactory`
- one `PrimaryComponentFactory`
- zero or more `ComponentletFactory`

The sample should continue to treat:

- `Sample`
- `public-notice`
- `notice-admin`

as real runtime participants created from the bundle/participant model.

The sample must not fall back to:

- flat `Vector[Component]` construction
- root alias substitution for componentlet identity

### Runtime identity

The formal rule is now fixed:

- real runtime componentlets are first-class runtime `Component`
- metadata-only componentlets are not runtime participants
- source/target identity uses the resolved runtime participant name
- root alias must not replace runtime componentlet identity

This rule is now part of the framework contract, not just a currently passing
behavior.

### Event semantics

The sample may rely on these event/runtime semantics being framework-owned:

- same-subsystem sync-inline continuation stays in one job / one runtime / one
  `UnitOfWork` / one transaction
- same-subsystem sync failure rolls back source and target together
- async/new-job continuation remains post-outer-commit
- source event records remain immutable after commit
- target dispatch runs using the target participant base execution context

### Inspection semantics

The authoritative detailed surfaces remain:

- builtin `event`
- builtin `job_control`

The discovery/entry surface remains:

- `admin.execution.diagnostics`

Field naming should now be treated as stable.

Event-side baseline fields:

- `reception-rule`
- `reception-policy`
- `policy-source`
- `failure-policy`
- `failure-disposition-base`
- `dispatch-kind`
- `dispatch-status`
- `source-subsystem`
- `source-component`
- `target-subsystem`
- `target-component`

Job-side baseline fields:

- `reception-rule`
- `reception-policy`
- `policy-source`
- `job-relation`
- `saga-relation`
- `failure-policy`
- `failure-disposition`
- `source-subsystem`
- `source-component`
- `target-subsystem`
- `target-component`

### Policy selection precedence

The precedence rule is now fixed:

1. explicit rule
2. compatibility mapping
3. subsystem default

Future ABAC belongs inside explicit-rule matching and does not become a
separate override tier.

## 3. What the Sample Should Do Next

The sample should now act as the acceptance driver on top of the closed CNCF
contract.

Recommended focus:

1. keep the existing sync-inline and async/new-job acceptance flows green
2. verify bundle bootstrap/discovery remains correct under real app packaging
3. use builtin event/job/admin surfaces as the operator-facing evidence path
4. avoid app-side compensation for framework-owned behavior

## 4. What Must Not Return in the Sample

Do not reintroduce any of the earlier compensation patterns.

The sample should not depend on:

- manual source-side `commit()` to make sync reception work
- accepted-body direct update to compensate for framework dispatch ordering
- direct source-to-target `EventReception` dispatch
- app-side manual source/target/policy metadata injection
- root-level alias rewriting of runtime componentlet identity

If any of these seem necessary again, treat that as a framework regression, not
as a valid sample-side workaround.

## 5. Acceptance Checks for the Sample

The sample should continue to prove these points against the current framework.

### Sync-inline

- client submits command through the job interface
- source component emits event
- target componentlet follow-up runs synchronously inside the started job body
- source and target state changes commit once
- rollback semantics hold on failure
- persisted event metadata is framework-owned
- no child job is created

### Async new-job

- source event is committed first
- child job is submitted after outer commit
- lineage and disposition are visible through builtin job inspection
- event surface shows dispatch contract/base disposition rather than pretending
  to know final child-job outcome

### Identity and inspection

- `Sample`, `public-notice`, and `notice-admin` remain visible as real runtime
  participants
- event metadata uses runtime source/target names
- job lineage uses runtime source/target names
- admin discovery points to the authoritative event/job inspection surfaces

## 6. Relevant CNCF References

- [phase-13-closure-result-2026-04-22.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-closure-result-2026-04-22.md)
- [phase-14-candidates-from-phase-13-2026-04-22.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-14-candidates-from-phase-13-2026-04-22.md)
- [textus-sample-app-event-bundle-factory-handoff-2026-04-22.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/textus-sample-app-event-bundle-factory-handoff-2026-04-22.md)
- [phase-13-textus-sample-app-event-handoff-2026-04-21.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-textus-sample-app-event-handoff-2026-04-21.md)

## 7. Scope Boundary

This handoff is for returning development-driver responsibility to
`textus-sample-app-event` after CNCF Phase 13 closure.

It is not a request to reopen Phase 13 baseline work.

If the sample finds gaps now, those should be judged as either:

- a regression against the closed Phase 13 contract
- or a true next-phase candidate beyond the current closure line
