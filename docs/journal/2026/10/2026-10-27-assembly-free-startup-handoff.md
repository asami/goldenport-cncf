# CNCF assembly-free startup correction — 2026-10-27

Status: direction adopted; implementation and qualification pending.
Specification: [startup contract](../../../spec/assembly-free-standalone-startup.md).
Plan: [owned correction checklist](../../../phase/assembly-free-startup-correction.md).

## Observed incident and limits

CBD Phase 12 initially failed before Human Submit/Advance because standalone
execution lacked fixed-user wiring. A subsequent attempt incorrectly selected
the assembly through textus.subsystem.file and was rejected as an external
subsystem source. Explicit textus.assembly.descriptor selection then allowed the
same Session to continue. These results are retained; they do not prove that a
consumer assembly is required.

The user confirmed that skill-side mandatory assembly creation was a previously
removed defect. Restore the ordinary startup contract rather than reinstating
that prerequisite. The deployed common helper no longer contains its fixed-file
loop, but sm-workflow/scripts/native-goal-command.py (observed line 993) and
sm-workflow-dev/scripts/native-goal-command.py (observed line 1022) still do.
Installation documentation also retains the older assumption. Current source
and deployed artifacts must be distinguished during qualification.

CNCF CncfRuntimeInstanceLifecyclePart._fixed_profile_for_launch gates HOME
profile resolution on descriptor.authentication.localSubject.
Subsystem.executionProfileForUserModeC also requires effective localSubject;
ResolvedSecurityWiring.resolve derives it from the descriptor. The alternate
RuntimeStandaloneUserProfileAdmission path requires descriptor-owned stable
identity. Therefore creating a HOME profile alone is not a demonstrated fix.
Neither HOME user-profile.yaml file existed on the inspected machine. The
observation establishes source-level residual coupling, not a claim that every
installed version is identical or that the cleanup introduced the runtime rule.

## Delivery order and responsibility

1. CNCF: connect admitted standalone intent and normal identity/profile resolution
   to effective fixed-user wiring and stable runtime identity before validation.
   Cover preflight and final construction together. Retain typed configuration
   precedence and distinguish identity from authorization.
2. sm-workflow: integrate installed composition/setup, reconcile helper source
   copies and current documentation, and preserve the deployed no-mandatory-file
   behavior. No dummy assembly or second configuration resolver.
3. Qualify an actual installed Operation in an isolated fixture without assembly
   or descriptor override. Verify unchanged security semantics and accurate
   missing/conflicting identity diagnostics; retain intentional override,
   multi-user and controlled-test behavior.
4. Deliver actual source/artifact/check evidence and a supported same-Session
   adoption procedure. Only then replace the temporary consumer configuration,
   retaining effective subsystem, user, storage and grants.

No blanket repeated FULL or AI gate is added. Reuse evidence at its actual scope;
run the checks required by the changed boundary and installed integration.

## Active-work preservation and handoff

CBD Session `0e18bbc58dc2fbfbe3fec1246d91b931` retained accepted Stage 12.1/12.2,
EDIT12-03 and repair counts 0/10 at the investigated continuation. These are
historical locators, not permission to assume the current frontier is unchanged.
The owner must use its actual current state when adopting the eventual delivery.

Keep ongoing implementation, accepted work, all history, repair counts, successful
validation and the currently working temporary assembly selection. Do not delete
the assembly now, move RuntimeRoot, silently change subsystem/storage identity,
reset counters, re-Start, re-Submit accepted work or replay successful checks.
The correction must not become a new prerequisite for CBD's ongoing implementation.

Both correction plans are OPEN and independent of active Phase acceptance.
This update records specifications and plans only: no runtime code, live settings,
Session state, publication or tests were changed/executed. Share this handoff with
sm-workflow development and textus-cbd-support development. Product implementation
and live adoption remain with their existing owners.
