# Workflow Invocation Policy ABI Contract

Status: S66.3 Step accepted; full Phase closure pending
Authority: Cozy PHASE-66 / S66.3 / S66.3A
Updated: 2026-10-02

## Consumer ownership

[WorkflowInvocationPolicyAbi](../../src/main/scala/org/goldenport/cncf/workflow/WorkflowInvocationPolicyAbi.scala)
is a separate pure metadata receiver in `org.goldenport.cncf.workflow`:

```scala
parseC(rawJson: String, sourceResource: String): Consequence[Artifact]
admitC(artifact: Artifact): Consequence[Artifact]
```

`legacySchemaVersion` is `cozy.cml.statemachine-workflow-abi.v1`;
`schemaVersion` is `cozy.cml.statemachine-workflow-abi.v2`. The existing
`GeneratedWorkflowAbi` stays a closed, fixture-bound v1 receiver.
`CandidateWorkflowAbi` keeps its separate Candidate-Admission authority.
Neither is widened or replaced. ComponentFactory, WorkflowInstancePersistence,
WorkflowEngine and runtime registration do not yet consume this receiver's v2
values. Compiled generated bootstrap equivalence is metadata evidence only.

Cozy owns CML parsing, normalization and generated products. CNCF receives
JSON, then admitted immutable `Artifact` / `Workflow` / `InvocationPolicy`
values. This receiver adds no file access, execution context, provider,
transport, persistence, timer, runtime Required SPI metadata or default policy.

## Closed wire and typed values

Every object requires exactly the following keys, including nullable fields:

| Object | Keys |
| --- | --- |
| Root | `schemaVersion`, `workflows` |
| v1 Workflow | `identity`, `version`, `source`, `states`, `actions`, `requiredSpi` |
| v2 Workflow | all v1 keys plus required `invocationPolicies` |
| Workflow source | `root`, `definition` |
| SourceIdentity | `line` |
| State | `name`, `source` |
| Action | `identity`, `kind`, `operation`, `inputBinding`, `source` |
| Operation | `service`, `name`, `inputType`, `resultType`, `source` |
| Required SPI | `capability`, `actionIdentity`, `operation`, `source` |
| Invocation policy | `identity`, `target`, `actionIdentity`, `retry`, `executionTimeoutMillis`, `source` |
| Target | `kind`, `identity` |
| Nonnull retry | `maximumAttempts`, `fixedRetryDelayMillis` |

Root and nested objects must be objects; collections must be arrays. A v1
Workflow rejects even an empty policy field. Its typed policy vector is empty.
An empty v1 collection is valid. v2 requires at least one policy across the
collection; metadata-free members contain explicit `[]`. Workflow and vector
order is retained. There is no workflow identity uniqueness, workflow-count,
nonempty state, Action or SPI requirement introduced by this metadata receiver.

All data records are final immutable case classes. `ActionKind` represents
`OPERATION`, `JUDGMENT`, `ADMISSION` with exact `.value` tokens.
`InvocationTarget` is a sealed abstract conceptual trunk with `Action(identity)`
and `Participant(identity)` cases. Each retains the declared target spelling.
`Workflow` exposes source correlation, ordered states, Actions, Required SPI
and policies; its `invocationPolicies` defaults to `Vector.empty` for typed
construction only. `Artifact` has a package-scoped constructor and exactly
`schemaVersion`, `sourceResource`, `rawJson`, `workflows`, `sha256`.

Required strings are nonblank JSON strings with their original contents
preserved; source version is opaque. `inputType`, `resultType` and `inputBinding`
are explicit null or nonblank strings. Missing fields never imply absence.
All source lines are explicit null or positive exact Int. Root, definition,
state, Action, Operation, capability and policy source positions retain `None`
as well as defined locations.

## Exact numbers, absence and target correlation

Numbers are unquoted unsigned base-10 JSON integer tokens. Strings, booleans,
negative values, fractions, decimal `1.0`, exponent `1e0` and overflow fail.
No floating conversion, truncation, unit conversion or re-normalization occurs.

| Value | Range | Meaning |
| --- | --- | --- |
| `maximumAttempts` | 1..2147483647 | Includes the initial attempt |
| `fixedRetryDelayMillis` | 0..9223372036854775807 | Delay before each later attempt |
| `executionTimeoutMillis` | 1..9223372036854775807 | Bound on each attempt |
| `source.line` | 1..2147483647, or null | Producer source correlation |

Retry is null or a complete pair; timeout is null or a valid Long. At least
one control must be present. Absence remains `None`; there is no implicit retry,
timeout, backoff or delay. `Long.MaxValue` survives exactly.

Declared aliases resolve with `value.toLowerCase.filter(_.isLetterOrDigit)`.
State names, Action identities, capability identities, policy identities and
resolved policy Action identities must be unique under that comparison.
`actionIdentity` must equal the declared canonical Action identity exactly.
An `ACTION` target resolves to exactly one Action. A `PARTICIPANT` target
resolves to exactly one Required SPI capability and through its canonical
Action identity to the same Action; it never names a provider.

Operation source equals its owning Action source. Every Required SPI references
an existing exact canonical Action identity and has a full Operation value
(including source) equal to that Action's Operation. Required SPI entry source
remains separate. Targeted Actions may be OPERATION or JUDGMENT; targeted
ADMISSION is rejected. Untargeted ADMISSION remains represented. These checks
do not reinterpret Candidate-Admission semantics or supply execution metadata.

## Reparse equality, exact bytes and failures

`sha256` hashes the original `rawJson` UTF-8 bytes. Raw JSON and nonblank caller
resource labels are preserved exactly. Whitespace changes the hash; the receiver
never trims, strips newlines, canonicalizes JSON or reads CML.

`admitC` reparses the artifact's original raw JSON and resource label and compares
the entire reparsed Artifact using value equality. Copied schema, hash, Workflow
or policy facts that diverge fail. Changed malformed JSON retains the parse
failure. Changing raw bytes requires the hash and all facts to agree with those
bytes. A caller-changed resource label alone remains admissible: the label is
correlation, not cryptographically authenticated producer or CML provenance.
The pinned fixture evidence below supplies this Step's actual attribution.

Invalid inputs return `Consequence.Failure(Conclusion.from(exception))` with
`WorkflowInvocationPolicyAbiException.diagnostic`; invalid input does not throw
that exception. Null artifact, raw JSON and null/blank resource fail safely.
Blank/malformed JSON and duplicate keys anywhere fail InvalidJson through
`JawnParser(allowDuplicateKeys = false)`. JSON null and member type errors fail
InvalidShape; canonical numeric and nonblank value rules use InvalidValue.

| Diagnostic case | Stable code |
| --- | --- |
| InvalidJson | `CWF-INVOCATION-POLICY-ABI-001` |
| InvalidShape | `CWF-INVOCATION-POLICY-ABI-002` |
| UnsupportedSchema | `CWF-INVOCATION-POLICY-ABI-003` |
| InvalidValue | `CWF-INVOCATION-POLICY-ABI-004` |
| DuplicateIdentity | `CWF-INVOCATION-POLICY-ABI-005` |
| TargetMismatch | `CWF-INVOCATION-POLICY-ABI-006` |
| IneligibleAction | `CWF-INVOCATION-POLICY-ABI-007` |
| DefinitionMismatch | `CWF-INVOCATION-POLICY-ABI-008` |

`Diagnostic.render` starts with `code.value`, followed by sorted `key=value`
context. Malformed inputs include stable `path`/`reason`; binding failures include
Workflow and policy identity where relevant. Unsupported schema includes
`schema`. DefinitionMismatch is `path=artifact reason=facts-diverge`.
Diagnostics include neither raw JSON nor unstable parser throwable text.

## Actual producer provenance and executable evidence

Producer: Cozy commit `e3262754023e0afc42bb5fcd3aeecf1cca0ab78e`, parent
`358ae6d1a233557b3e45087558ecff89e420f4af`. Both public `modeler-scala` and
`modeler-scala-value` routes emitted identical normal products at accepted S66.2.
The legacy sidecar is the independent actual v1 legacy route product.

| Tracked Cozy input or generator | SHA-256 |
| --- | --- |
| [workflow-invocation-policy.cml](../../../cozy/src/test/resources/modeler/workflow-invocation-policy.cml) | `4b61b539f6beae2ffb1c92c48e93350aa62ed43b741f0d41a16d83d852ac812a` |
| [workflow-invocation-policy-legacy.cml](../../../cozy/src/test/resources/modeler/workflow-invocation-policy-legacy.cml) | `c1bb5acb021b14de4935c329a1e4c7982c77845f0cdd98f80cd9077583e8e795` |
| [StateMachineWorkflowAbiGenerator.scala](../../../cozy/src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala) | `fbc5cd5649165d521c225f38f3e98d4477f4a7759432b882f9c0d9c9e5080186` |

| Exact consumed artifact | SHA-256 |
| --- | --- |
| [v2 JSON](../../src/test/resources/workflow/invocation-policy-producer-abi.json) | `bbbca0dad63e18ce4f12def1c42edd7d94ede03a3b7b2736b8028b2a6bb422d1` |
| [Legacy v1 JSON](../../src/test/resources/workflow/invocation-policy-legacy-producer-abi.json) | `c1ec6d672e76f060b1047880e50db38627ea54f80b8833e1f7a590c06ea79626` |
| [Shared generated Scala ABI](../../src/test/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala) | `a4e33514d6962ed8946df789c92ecf3ad9643bb58d8fb69db94bfd1cb26f0626` |
| [Generated bootstrap](../../src/test/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala) | `c0acadc0ef83e5dbcd875d38ac5172a5a3b97d8d08a1b8de7918d96c9dbab483` |
| [Generated Workflow descriptor](../../src/test/scala/domain/statemachine/workflow/InvocationControlledWorkflowStateMachineWorkflow1.scala) | `54029bac67b33ce3790319247fabd3766e3ba8bf81b205f404c0627a6435cc52` |

All five products are byte copies, including package `domain.statemachine.workflow`.
The three Scala artifacts are ordinary Test sources referenced by
[WorkflowInvocationPolicyAbiSpec](../../src/test/scala/org/goldenport/cncf/workflow/WorkflowInvocationPolicyAbiSpec.scala).
It specifies actual JSON admission, every compiled descriptor/bootstrap field,
legacy and mixed collections, exact numeric endpoints and at least fifty
ScalaCheck round trips, malformed numeric/shape/string/duplicate-key input,
alias/correlation/eligibility rules, copied facts, raw-byte hash, null handling
and typed deterministic diagnostics. Fixture hashes occur within actual consumed
value-equivalence behavior. No Markdown, source-text or phase-closure tests apply.

Accepted producer receipts: `P66-S662-REVIEW-001` PASS;
`P66-S662A-VAL-001` 152 passed / 0 failed; `P66-S662A-VAL-002` three actual
Scala 3.3.8 source files compiled into 93 classes, SBT exit 0, lock released.
S66.3 Step acceptance: normal CNCF receipt `P66-S663A-VAL-005` records
40 passed / 0 failed across three suites, SBT exit 0, wrapper exit 0 and
`lock=released`. The accumulator includes the 21 invocation-policy behaviors,
existing closed-v1/Candidate compatibility, compiled real generated Scala values,
and all five exact producer copies.

Independent protected focused review `P66-S663-REVIEW-001` found no functional
decoder defect and one action-placement authoring blocker. The three prescribed
`When`/`Then` relocations retain every request and expectation; parent-verified
`P66-S663-M0-WAIVER-001` closes that blocker using exact final bytes and
whitespace-diff evidence. The passing tests precede this mechanical arrangement
change; no post-repair test execution or second review is claimed.
This change records accepted S66.3 work; its local commits are attributable
through the Cozy/CNCF Git histories. Full Phase review, final full suites,
release commit and Phase acceptance remain pending.
See the [Cozy handoff](../../../cozy/docs/phase/phase-66-cncf-handoff.md), canonical
[source](../../../cozy/docs/spec/workflow-retry-timeout-source-contract.md),
[v2 producer](../../../cozy/docs/spec/workflow-retry-timeout-abi-contract.md)
and [v1 producer](../../../cozy/docs/spec/statemachine-workflow-generated-abi-contract.md) contracts.

## Phase81 runtime handoff

[CNCF Phase81](../phase/phase-81.md) owns future ComponentFactory/runtime adoption
and execution enforcement after Phase80 WorkflowExecutionProtocolRuntime:
maximum attempts including initial, fixed delay before later attempts,
per-attempt timeout, durable attempt/retry readiness, outcome/history/diagnostics,
restart-safe retry/timeout, and minimum late-completion protection against
repeated transition. It receives admitted values without reparsing CML.

Deadline, general Timer/Wait, Cancellation, FailurePolicy, general idempotency,
backoff/jitter, dedicated Iteration, Phase71/75, publication and pushes are
outside this contract. This document updates no older CNCF Phase66 progress.
