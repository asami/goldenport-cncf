# Finding Disposition ABI V1

Status: implementation contract (Phase 99)

## Meaning and identity

`org.goldenport.cncf.finding.FindingDispositionV1` is the normalized public
contract for durable static-analysis and review decisions. CAR lint consumes
this pure API without AI, CBD Support or sm-workflow. Rule IDs are open data,
not an enumeration of known lint rules.

A finding key is the exact tuple `(rule, finding, target.scope, target.identity)`.
The producer assigns stable semantic identities, including a stable occurrence
identity where a rule can report multiple findings at one target. Line numbers,
list indexes and mutable warning text alone are not stable identities. No prefix,
wildcard, inherited annotation or neighboring-target match is admitted.

`Declaration` includes ABI version, key, kind, nonblank trimmed reason and
provenance reference. Reasons explain the actual decision; provenance identifies
its source record. These strings are retained unchanged, without control
characters. A provenance string is not evidence of Human permission.

## Dispositions and reinspection

| Kind | Interpretation |
| --- | --- |
| `fix` | Correct the underlying source/model; a declaration alone does not close a finding. |
| `accept` | Explicitly accept this exact still-observed finding for the recorded reason. |
| `defer` | Explicitly retain this exact still-observed finding in follow-up backlog. |
| `configure` | Resolve through admitted tool/lint configuration and reinspection; no blanket suppression. |

`interpretC(before, after, decisions, changes, aliases)` is deterministic and
does not edit or authorize anything. Each `Inspection` identifies the effective
source/model/configuration revision, observed findings, and completed rule/target
coverage. A skipped or failed check must not claim completed coverage. Every
observed finding must belong to that coverage.

The report contains the union of previous and current finding keys in stable
scope/target/rule/finding order. Duplicate findings or decisions, including
duplicates after alias normalization, fail. A current exact `accept` becomes
`Accepted`; an exact `defer` becomes `Deferred` and remains in `deferredBacklog`.
Neither changes the source nor proves the cause was corrected.

A previously observed finding becomes `Corrected`/`Configured` only when it is
absent from current findings, the current inspection covers its rule and target,
the declaration is `fix`/`configure`, and tool-supplied `ChangeEvidence` binds
that exact key to both inspection revisions and an evidence reference. The
revisions must differ. The adapter owns truthful observations and actual
evidence verification. This API cannot verify an arbitrary diff URI or perform
Human Admission. A disappearance without these facts stays `Unresolved`.

The actionable backlog metric is `Report.actionableBacklog`: unresolved finding
keys, **not raw warning count**. Deferred findings remain a separately visible
follow-up backlog; accepted findings remain visible as explicit decisions.
Unmatched decisions remain stored declarations and cannot suppress other keys.

## Scala annotation ABI

Handwritten Scala uses the public runtime-retained JVM annotation:

```scala
import org.goldenport.cncf.finding.annotation.FindingDisposition

@FindingDisposition(
  abiVersion = 1,
  rule = "car.required-operation",
  finding = "missing-submit",
  scope = "component:orders",
  target = "operation:submit",
  kind = "defer",
  reason = "Implement submission in the next milestone",
  provenance = "issue:orders:42"
)
def submit(): Unit = ()
```

The annotation may bind a type, method, constructor or field. Multiple distinct
finding bindings use repeatable `FindingDisposition` annotations (JVM container
`FindingDispositions`). A tool calls
`FindingDispositionRepresentation.readC(element, actualTarget)` with its actual
semantic element binding. A declared target differing from that binding fails.
The reader uses declared annotations only and rejects duplicate keys. Scala
use-site placement must select the actual element the tool inspects.

Names, argument types/defaults, lowercase kind strings and their semantics are
public ABI. `abiVersion` defaults to `1`; all other members are required Strings.
Unknown ABI versions/kinds and incomplete declarations fail instead of silently
accepting or suppressing findings. The pure portable `Properties` encode/decode
API round-trips the same values independently of the annotation representation.

Adding rule IDs needs no ABI change. Rule removal/renaming must retain an
explicit `RuleAlias(previous, current, deprecatedSince)` compatibility record;
consumers canonicalize observed and declared identities with the same map.
Alias chains are allowed; duplicate previous IDs and cycles fail. Removing an
alias is an explicit compatibility decision, not an incidental lint update.
ABI breaking changes require a new version and migration of stored declarations.

## CML and Human boundaries

Cozy owns CML property names, parsing and serialization. Its adapter maps model
properties directly to `Properties`/`Declaration`, with the same exact semantic
key, kind, reason, provenance and version. CNCF does not define a second CML
decision model. Generated Scala is only a projection of that model; it is not
an independent authority. Do not reconstruct authority from generated annotations.

The resolution process remains external:

`Finding -> Resolution Proposal -> Human Admission -> Work -> Re-lint/Review -> Close`

The interpreter consumes decisions already admitted by that external process.
An annotation/property neither records approval workflow nor grants permission
to apply a proposal. No auto-insertion, auto-fix or bulk accept/defer policy is
provided. Tools may propose a disposition but must not insert annotations simply
to lower warning counts. Real fixes change the source/model and re-run the rule.

Executable contract: `FindingDispositionSpec` exercises compiled Scala
annotations, portable property round trips, exact binding, actual model lint
before/after correction, configuration reinspection, compatibility and backlog.
