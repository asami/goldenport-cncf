# Luna High Gradual Workload Migration Plan

Date: 2026-08-03

Status: active migration-management journal

## Purpose

This journal manages a gradual experiment in moving selected Codex workflow
roles from GPT-5.6 Sol to GPT-5.6 Luna high where Luna can provide an acceptable
quality/cost balance.

The objective is not to migrate every eligible role. The objective is to move
one bounded workload class at a time, observe the result, and stop at the point
where further migration no longer provides a convincing practical benefit.
Stopping before the last candidate is a successful outcome.

The final adoption criterion is the user's experiential `OK / NG / Hold`
verdict. Mechanical quality, convergence, cost, and time evidence supports that
judgment but does not replace it. A verdict is meaningful only when the exact
agent type, configured model, reasoning effort, returned agent ID, and any
fallback/escalation are visible.

This is a chronological, non-normative management record. It does not change a
skill or agent contract by itself. Each actual migration requires a separate,
explicit skill/configuration update and its own validation.

## Current Baseline

The working baseline at the start of this journal is:

| Workload | Current execution |
| --- | --- |
| PLAN | fresh GPT-5.6 Sol high subagent |
| ordinary full REVIEW | fresh GPT-5.6 Sol medium by default |
| focused RE_REVIEW | fresh GPT-5.6 Sol medium |
| independent architecture/specification investigation | GPT-5.6 Sol high through `cncf-investigate` |
| implementation and review-fix | parent task at the task's selected reasoning mode |
| frozen Commit Manifest execution | GPT-5.6 Luna medium `cncf_commit_runner` |
| serialized SBT execution | GPT-5.6 Luna medium `cncf_command_runner` |
| reviewed one-shot script/launcher execution | GPT-5.6 Luna low `cncf_runtime_runner` |
| long-lived runtime-session execution | GPT-5.6 Luna medium `cncf_runtime_session_runner` |

The existing Luna command runners are not migration candidates for Luna high.
Their work is deliberately mechanical, and higher reasoning would normally add
cost without improving the contract.

## Migration Principle

Migrate bounded semantic work before broad semantic work.

A suitable early Luna-high workload has all of these properties:

- its input scope is frozen and small;
- its acceptance or rejection criteria are explicit;
- it can return evidence without mutating external state;
- a parent or later review can detect an error before release; and
- the work is long enough for subagent startup overhead to be amortized.

Do not use model price alone as the migration criterion. Evaluate total cost,
elapsed time, parent correction effort, extra review passes, and the consequence
of a missed finding together.

## Protected Sol Workloads

The following workloads remain on Sol unless later evidence justifies a
separate decision:

- cross-repository PLAN and task decomposition;
- architecture or specification investigation with an open problem boundary;
- public API, CML, SPI, persistence, serialization, identity, authorization,
  concurrency, ClassLoader, or lifecycle contract review;
- a clean full review spanning multiple repositories or interacting contract
  surfaces; and
- any review whose failure could admit a high-impact change without another
  independent semantic gate.

This list is a conservative starting boundary, not a requirement to eventually
migrate these workloads.

## Migration Stages

Stages are gates, not a delivery roadmap. Only one new workload class should be
under evaluation at a time. A later stage is optional even when the earlier
stage succeeds.

### Stage LH-1: Focused Re-review

Candidate:

- focused RE_REVIEW after admitted findings have been fixed;
- input includes the stable finding ledger, reviewed diff identity, exact fix
  delta, newly touched files, and directly affected integration edges; and
- unchanged settled areas remain outside the re-review scope.

Proposed migration:

- change the focused RE_REVIEW subagent from Sol medium to Luna high;
- retain fail-closed escalation to Sol medium when the baseline is invalidated,
  the fix expands scope, or a new public-contract concern appears; and
- keep clean initial REVIEW behavior unchanged.

This is the first candidate because the problem boundary and convergence
contract are already explicit.

### Stage LH-2: Other Focused Review

Candidate:

- focused review after test-fix or another bounded repair;
- review of an exact changed-file set with known acceptance criteria; and
- review that does not require a new architecture interpretation.

Proposed migration:

- use Luna high by default for the bounded focused review;
- escalate to Sol medium when the reviewer discovers cross-file or
  cross-contract implications; and
- treat escalation as a normal routing result, not a Luna failure.

### Stage LH-3: Bounded Read-only Investigation

Candidate:

- failure localization with a known repository and observable symptom;
- code/history lookup answering one concrete question;
- verification of an already proposed dependency or call route; and
- other independent read-only investigation with a frozen question.

Possible implementation:

- add a dedicated `cncf-focused-investigate` skill or an explicit focused mode
  to `cncf-investigate` using a fresh Luna high explorer;
- retain current Sol high `cncf-investigate` for architecture, specification,
  suspected design weakness, and open-ended cross-repository investigation;
  and
- require the parent to distinguish focused localization from architectural
  investigation before delegation.

### Stage LH-4: Bounded Review-fix or Implementation Pilot

Candidate:

- one admitted finding with a frozen repair boundary;
- a local implementation slice whose Sol-high plan, target paths, behavioral
  acceptance criteria, and focused validation are already fixed;
- mechanical adapter, projection, fixture, or documentation synchronization
  work that still needs code-level reasoning; and
- work large enough to amortize subagent startup.

Conditions:

- the parent retains scope, architecture, and integration decisions;
- Luna high may edit only the admitted files and may not expand the plan;
- focused validation and an independent review remain mandatory; and
- public-contract or cross-repository expansion returns to the parent rather
  than being solved inside the worker.

This stage is a pilot, not a presumed destination. It should not be enabled
until the review and investigation stages provide useful evidence.

### Stage LH-5: Routine Full Review, Optional

Candidate:

- a single-repository, bounded full review with no protected contract surface.

This stage is intentionally optional and has no target date. It should be
considered only if earlier stages show that Luna high maintains review quality
while materially reducing total cost. Broad clean review remains on Sol when
the evidence is inconclusive.

## Trial Protocol

For each workload stage:

1. Freeze the workload definition and escalation triggers before changing a
   skill.
2. Assign a stable Trial ID and record the exact skill, agent role/type/name,
   previous model, configured trial model, reasoning effort, returned agent ID,
   fallback route, and trial start date.
3. Immediately disclose the target agent after spawn and disclose the result
   with the same identity after completion. A trial without complete agent
   evidence is invalid for adoption.
4. Use an independent Sol audit when practical. The audit
   must review the same frozen evidence without being primed by the Luna result.
5. Record findings and outcomes in the Trial Ledger below.
6. Ask the user once for `OK / NG / Hold` as a non-blocking verdict. If the user
   does not answer, record `awaiting-user-verdict`; do not wait, poll, repeat the
   question, or block the enclosing development workflow.
7. Apply `adopt`, `hold`, `rollback-requested`, or `stop-successfully` only from
   attributable user input. Opening the next stage requires separate explicit
   user direction.
8. Change only one workload class between evaluation points so that regressions
   remain attributable.

There is no mandatory trial count. Gather only as much natural evidence as the
user needs to form a verdict. Do not manufacture work to satisfy a numeric
gate.

A trial may be evaluated without a shadow Sol audit when the subsequent normal
workflow provides an equivalent independent semantic gate. Record which gate
provided the comparison evidence.

## Evaluation Axes

Record evidence on these axes:

### Quality

- P0/P1/P2 findings found by Luna;
- actionable findings missed by Luna and later found by Sol, the parent, tests,
  or release validation;
- false positives or findings outside the frozen scope;
- incorrect clean result;
- unjustified scope expansion; and
- quality of concrete file/line/evidence attribution.

### Convergence

- number of review/fix/re-review passes;
- whether the stable finding ledger converged;
- parent corrections required before the result was usable; and
- escalation count and reason.

### Cost and Time

- observed token/cost category when available;
- elapsed time;
- subagent startup overhead;
- parent coordination and correction time; and
- duplicated Sol audit cost during the trial period.

### Operational Fit

- whether the workload could be frozen clearly;
- whether the agent respected repository and mutation boundaries;
- whether the result contract was complete;
- whether task resumption remained simple; and
- whether a model escalation decision was obvious.

Do not claim a cost benefit when model-level accounting is unavailable. Record
the available proxy and mark the conclusion as an inference.

## Agent Visibility and Adoption Criterion

Every trial must expose:

- Trial ID and workflow stage;
- logical role and agent type/name;
- exact configured model and reasoning effort;
- returned target agent ID;
- fallback or escalation route, including comparison-agent identity;
- observable result and comparison gate; and
- whether runtime identity was directly observable or only configured by the
  delegation contract.

Use this compact execution disclosure after spawn:

```text
Agent Start: trial=<id> stage=<stage> role=<role> type=<agent-type> name=<name> model=<configured-model> reasoning=<effort> id=<returned-agent-id> fallback=<none|route>
```

Use the same identity in the result disclosure. Do not combine target Luna and
comparison Sol evidence under one agent label. A fallback run is `hybrid` and
does not count as a pure Luna trial.

The user's verdict is authoritative:

- `OK`: accept the observed trial experience/result;
- `NG`: reject it and request rollback or redesign of the assignment; and
- `Hold`: leave the decision open and gather natural evidence.

No clean result, passing test, trial count, cost estimate, or automated score
may be interpreted as user approval. Supporting evidence may inform the user
and may trigger a safety recommendation, but it cannot override the verdict.

If agent identity is incomplete, record `invalid-agent-evidence` and do not ask
the user to judge that attempt as a migration trial.

## Hold, Rollback, and Successful Stop

Use these dispositions:

- `continue`: the current stage remains under trial;
- `adopt`: the user has accepted the current assignment as the default;
- `hold`: retain the current stage assignment and gather natural evidence
  without opening a new stage;
- `rollback-requested`: the user rejected the assignment; restore the previous
  model through an explicit skill/configuration update;
- `stop-successfully`: keep all accepted migrations, leave later candidates on
  their current models, and close the migration program; and
- `superseded`: replace this plan with a later explicit model-routing policy.

Choose `stop-successfully` when any of the following applies:

- the next candidate has a materially higher semantic risk;
- savings have become small or unobservable;
- startup, audit, correction, or escalation overhead consumes the expected
  benefit;
- the current mix already provides a satisfactory cost/quality balance;
- representative work for the next stage is too rare to justify another role;
  or
- maintaining additional routing rules would cost more than they save.

Reaching `stop-successfully` does not require attempting LH-4 or LH-5.

When a Luna assignment produces an incorrect clean result, misses a high-impact
finding, expands scope without authority, or requires substantial correction,
present that evidence prominently with a rollback recommendation. The user
still supplies the attributable `NG`, `Hold`, or other disposition.

## Escalation Rules

Escalate a Luna-high task to Sol rather than forcing it to finish when:

- the frozen baseline is invalidated;
- the issue crosses a repository or public contract boundary;
- a new specification or architecture decision is required;
- persistence, identity, security, concurrency, lifecycle, or compatibility
  semantics become material;
- the evidence is contradictory or incomplete; or
- the Luna agent cannot state a bounded, evidence-backed conclusion.

An appropriate escalation counts as successful routing behavior. Record it so
that repeated escalation can reveal that the workload should remain on Sol.

## Trial Ledger

Add one row per trial. Keep evidence links repository-relative where possible.

| Trial | Stage | Date | Workload | Role | Agent type/name | Model/effort | Agent ID | Fallback/escalation | Result | Comparison gate | Supporting evidence | User verdict | Disposition |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| — | LH-1 | — | Not started | — | — | — | — | — | — | — | — | — | `continue` |

## Stage Decision Ledger

| Stage | Status | Decision date | Decision | Evidence summary | Skill/configuration change |
| --- | --- | --- | --- | --- | --- |
| LH-1 focused re-review | proposed | — | Begin here when explicitly authorized | Most bounded semantic review role | — |
| LH-2 other focused review | waiting | — | Evaluate only after LH-1 | Requires a clear focused-review classifier | — |
| LH-3 bounded investigation | waiting | — | Evaluate only after earlier evidence | Likely needs a separate focused skill or mode | — |
| LH-4 bounded fix/implementation | optional | — | No presumption of adoption | Higher mutation risk | — |
| LH-5 routine full review | optional | — | May remain unattempted | Highest review-risk candidate in this plan | — |

## Current Decision

- Keep the current model assignments unchanged until LH-1 is explicitly
  started.
- Use focused RE_REVIEW as the first Luna-high migration candidate.
- Do not combine the first migration with another role change.
- Keep PLAN on Sol high and broad or contract-sensitive review/investigation on
  Sol.
- Treat stopping at any stable, cost-effective stage as the intended operating
  model, not as an incomplete migration.

## Update Procedure

When this journal is used for ongoing management:

1. disclose the complete agent identity during execution;
2. append the trial evidence to the Trial Ledger or mark
   `invalid-agent-evidence`;
3. ask once for the non-blocking user verdict and update the same row when it
   arrives;
4. update the applicable Stage Decision Ledger row only from attributable user
   input;
5. record any actual skill/configuration change by path and date;
6. record rollback or escalation evidence without rewriting the earlier trial;
7. keep unresolved model-quality claims explicitly marked as inference; and
8. set the top-level status to `stopped-successfully` when the user decides
   that further migration is no longer worthwhile.

Use `cncf-luna-migration-observation` as the common execution-observation
contract. It may update this journal only when the CNCF repository is already
inside the enclosing task's allowed update roots. Otherwise it returns an exact
`pending journal sync` row and does not broaden task scope.
It also returns `pending journal sync` when an immediate journal edit would
invalidate an already reviewed or frozen product tree; migration observation
must not delay product release.

Because this journal is non-normative, an adopted routing decision must also be
reflected in the applicable Codex skill and agent configuration before it is
operational.
