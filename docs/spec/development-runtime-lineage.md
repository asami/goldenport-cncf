# Development Runtime Source and Artifact Lineage

Spec ID: `development-runtime-lineage`.
Adopted by native Human instruction `01a174f7-e34b-7673-af87-80227d8535eb`
on October 26, 2026.
@since Oct. 26, 2026. @version Oct. 26, 2026.

Non-normative architectural context is in the
[development runtime lineage design](../design/development-runtime-lineage.md).
The accepted read behavior remains defined by the
[Operational Observation Read Model](operational-observation-read-model.md).

## R1. Canonical source integration

Observe the latest GitHub `main`, then integrate it with canonical local `main`
and the necessary accepted Phase 5.1 features. The observed remote commit
`4f9325d29072493b570d66ffb7daeb2614a0ef41` is an ancestor of canonical commit
`7b59ac431adba296d655bc89cebd1b518cb93598`.

Preserve current typed Git V5, default PATH lookup, optional executable override
and grants, FixCheck, the 4 GiB memory minimum and larger project settings,
WorkArea closure, event history and process termination. Integrate only the
missing approved observation read feature and the five correlated stored-row
integration and regression files. Preserve complete stored metadata and the
original event-envelope bytes.

## R2. Normal artifact production

Normal SBT `publishLocal` owns artifact production. Do not replace that route
with manual JAR or manifest copying or private runtime fabrication. One
canonical integrated CNCF source is the normal local publisher. Integrate
phase-specific changes there before publication instead of alternating
publication from divergent dedicated sources.

## R3. Mutable development coordinates

A Maven or Ivy SNAPSHOT coordinate is a mutable development alias, not an
immutable build identity. Differently sourced publishers can legitimately
replace the same coordinate. Coordinate publication and use through the
existing owning chats; do not add a machine-wide build lock, resolver or cache.

SBT native boot, dependency-cache and workspace locks provide their own
exclusion. They neither serialize every publisher of one logical coordinate
nor authenticate a future dependency selection. If incompatible development
lines must coexist, normal distinct-version and dependency selection is a
separately scoped change. No version change is selected here.

## R4. Source and selected runtime evidence

Record the actual source commit and intended working diff, normal publication
result, actual chosen classpath, CAR, runtime manifest and dependency closure,
and effective heap. Distinguish historical results from current acceptance.
Installed images remain independent snapshots managed through the normal
updater and must remain immutable.

## R5. Parent source and binary selection

Report parent source development through `TEXTUS_CNCF_SOURCE_ROOT` and
`.dependsOn` separately from binary, CAR and runtime selection. Do not silently
rebind the parent's original dependency root containing the 69-path work.
Source integration and local runtime qualification alone do not adopt new
inputs into the owner Session.

## R6. Owner adoption and Session preservation

Preserve the original Session, base, counters, failed intents and accepted S1.
The owner adopts inputs through actual public candidate and dependency revision
adoption. Do not substitute checkout HEAD reset, replay of an old source Submit
or storage editing for that adoption.

## R7. External boundaries

This integration authorizes no remote release, push or shared-grant change.
The parent retains ownership of validation, publication, actual runtime
qualification and acceptance.
