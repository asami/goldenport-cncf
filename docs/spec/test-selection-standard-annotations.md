# Test selection with standard annotations

Date: 2026-10-31
Status: approved target contract; producer/consumer migration pending.

## One custom classification

ADMISSION is the only custom test classification label. Retire SMOKE, FOCUSED,
FULL, HEAVY and ON_DEMAND purpose declarations and their active query values.
Do not map Slow back into a renamed HEAVY purpose or introduce aliases for retired
labels. Existing evidence remains readable as historical facts, not active input.
This supersedes earlier multi-purpose and ON_DEMAND authoring guidance; it does
not claim that current binaries have already migrated.

| Concern | Authoring / selection |
| --- | --- |
| Short basic acceptance coverage | Custom ADMISSION declaration |
| Ordinary full regression coverage | Normal discovery/selection, no FULL label |
| Long-running tests | ScalaTest `@org.scalatest.tags.Slow`, or per-test `taggedAs org.scalatest.tagobjects.Slow` |
| Low-frequency or special-environment suite | ScalaTest `@org.scalatest.DoNotDiscover`; explicit suite selection when required |
| Changed-scope validation | A concrete focused selection of suites/tests, features and applicable framework tag filters; no FOCUSED label |
| Duration estimate | Existing expectedDuration metadata, separate from classification |

FULL and focused may remain user-facing run modes and result-scope descriptions;
they are not test memberships. A request for heavy tests means selecting the
standard Slow tag, not consulting another purpose or maintained class list.
An existing SMOKE case with basic acceptance value may receive ADMISSION after
coverage assessment; do not bulk relabel every SMOKE case as ADMISSION.

## Selection and prerequisites

Normal test/FULL excludes Slow and does not automatically discover DoNotDiscover
suites. ADMISSION also retains these ordinary exclusions; an explicit specialized
selection may include them but must report its actual scope rather than silently
broadening routine admission. The ADMISSION target remains at most one minute of
aggregate test-body time, excluding preparation/cleanup; it is not a timeout.

Selecting the Slow tag does not by itself opt into DoNotDiscover suites. Name
those suites explicitly through the supported framework path; selecting a named
suite without a case/tag restriction can run all its cases. Focused may select
Slow tests or explicit-only suites when the affected requirement needs them.
Record exact selected operations and standard tags, without inventing purpose
membership. A generic wildcard/feature query must not re-enroll excluded suites.
Do not add a manual discovery registry to circumvent the framework annotation.

Inspect enough metadata to select before resource preparation; exclude a suite
before its lifecycle setup can start a service. Keep external-environment and
Docker suites explicit-only, with actual prerequisites separate from Slow.
An external-service dependency alone is not a duration classification. SQLite
and small same-JVM test-owned loopback fixtures remain eligible for normal FULL.
The annotations do not provision resources or grant execution permissions.

CNCF owns common metadata, discovery/explicit resolution and framework bindings;
framework adapters retain standard tag/annotation facts rather than a duplicate
purpose vocabulary. Keep feature, expectedDuration, execution requirements,
logical identities, provenance and result attribution. Do not force a framework
without ScalaTest annotations into a fabricated cross-framework HEAVY category.
Address concrete adapter needs through existing bindings.

## Low-frequency maintenance

DoNotDiscover suites are active execution targets, not disabled tests or archives.
Keep their explicit invocation, prerequisites, expected results and cleanup usable
for occasional or selected nighttime execution. Fix environment/specification
drift when exercised; distinguish actual product regressions and do not weaken
assertions to hide them. Preserve old results, original failures and repaired
reruns with their versions. No catalogue-wide continuous qualification, scheduler,
AI polling or automatic AI repair is required. Reserve scripted for the actual
sbt scripted mechanism; retain genuine scripts and their execution history.

## Migration and evidence

Remove obsolete source/config declarations and update their real call sites,
CML mapping, generated metadata, framework selection and documentation together.
No compatibility aliases or silent reinterpretation of obsolete active labels.
A supported adoption changes future selection; it does not rewrite captured live
checks, restart Sessions, replay accepted Submit, reset counts or erase evidence.
Classify former HEAVY/ON_DEMAND cases by their real duration and explicit-only
needs; they are not mechanically identical to Slow/DoNotDiscover in every case.
Preserve assertions, workloads and prior accepted commits.

Acceptance: bounded normal/ADMISSION/Slow/explicit-suite/named-focused selection
checks, class and per-test tags, both annotations together, inherited metadata,
preparation after final selection, a harmless real dispatch and accurate results.
Use the existing execution owner. Do not run external services or expensive
self-update catalogues just to prove exclusion. Previously passed 22/13 and
7/77/16 tests retain their original scopes, not proof of this replacement contract.
