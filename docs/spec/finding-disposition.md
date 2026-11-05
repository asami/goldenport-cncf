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

## Consumer mapping and invocation

The following are illustrative adapter inputs, not claims that CBD or Cozy has
executed an integration check. The adapter must obtain the exact rule,
occurrence and semantic target from its own structured finding. It must retain
the actual decision reason and provenance rather than inventing them.

```scala
import org.goldenport.cncf.finding.FindingDispositionV1.*
import org.goldenport.cncf.finding.FindingDispositionRepresentation.*

val target = Target("component:orders", "operation:submit")
val key = Key("car.required-operation", "missing-submit", target)
val finding = Finding(key, "The submit operation is required")
val coverage = Set(Coverage(key.rule, target))
val observed = Inspection("model:1", Vector(finding), coverage)

// CBD maps an already admitted, exact decision to the normalized declaration.
val accept = Declaration(key, Kind.Accept, "Submission is owned elsewhere", "decision:orders:1")
val accepted = interpretC(observed, observed, Vector(accept))
// For a different admitted decision, replace Accept and its reason/provenance:
val defer = Declaration(key, Kind.Defer, "Implement in the next milestone", "issue:orders:42")
val deferred = interpretC(observed, observed, Vector(defer))

// Cozy maps its own CML representation to the portable representation.
// These are Scala values, not a prescription of CML property names or syntax.
val cmlProperties = Properties(1, key.rule, key.finding, target.scope,
  target.identity, "defer", "Implement in the next milestone", "issue:orders:42")
val normalized = decodeC(cmlProperties) // Consequence[Declaration]
// For Accept, map kind = "accept" and the actual admitted reason/provenance.
```

`interpretC` returns `Consequence[Report]`; callers must handle failure before
using the report. A successful report for the still-observed exact finding has
status `Accepted` or `Deferred` respectively, and the latter remains in
`deferredBacklog`. For handwritten Scala, the adapter can instead call
`readC(element, target)` and pass its successfully decoded declarations to the
same interpreter. Neither representation establishes Human Admission.

For a real fix, the adapter changes the authoritative source or model, runs the
same rule again, and supplies the actual revisions and a verified reference to
the change. For example, assuming the second inspection truly completed this
rule/target and no longer observed the finding:

```scala
val correctedInspection = Inspection("model:2", Vector.empty, coverage)
val fix = Declaration(key, Kind.Fix, "Add the required operation", "decision:orders:2")
val change = ChangeEvidence(key, "model:1", "model:2", "diff:orders:model-1..model-2")
val result = interpretC(observed, correctedInspection, Vector(fix), Vector(change))
```

Only those verified facts make the result `Corrected`; the example reference
is illustrative. If a check was skipped or failed, omit its completed coverage.
If the finding disappears without completed matching coverage, an exact Fix
decision, and matching change evidence, the result remains `Unresolved`.
The same reinspection boundary applies to `Configure`, with a configuration
change and `Configured` outcome. These examples do not establish actual CBD or
Cozy application, reinspection, artifact selection, or coverage.

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
