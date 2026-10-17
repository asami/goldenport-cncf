# Managed effect usage classification

Status: normative

## R1 — Explicit observation input

`org.goldenport.cncf.effect.ExternalEffectUsage` describes one supplied source-use
observation: `ProcessCommand(commandName)`, `TypedGit(kind)`,
`TypedSbt(operation)` or `ResourceRead`. It is descriptive input, never an
execution request, permission, grant or proof of source behavior.
`ProcessExternalEffectPolicy.classifyUsageC` performs no I/O, file resolution,
program launch or policy change. It does not scan source bodies or establish
hostile-code containment.

## R2 — Shared classification

`ProcessCommand` delegates to the existing `classifyCommandName`, including its
basename normalization and bounded recognition limits. No separate tool list
is introduced. Typed Git uses `GitOperationKind.permission`: ReadOnly means
GitRead; other permissions mean GitWrite. Typed SBT means Build; ResourceRead
means ResourceRead. These three typed routes are Managed.

The existing `classifyDefinition` and generic-definition admission contracts
remain unchanged. Classification never makes an observed operation executable.

## R3 — Invalid observations

Null usage, null typed Git/SBT operands, and null, empty or NUL-containing
command names fail with structured argument errors. Diagnostics never reflect
the supplied command. No malformed observation falls back to Unclassified.

## Executable examples

`ExternalEffectUsageSpec` pairs these rules with executable examples:

- E1: typed Git read/write classification (R1, R2).
- E2: typed SBT and resource classification (R1, R2).
- E3: process command delegation, including Unclassified observations (R2).
- E4: malformed observations fail without reflecting payloads (R1, R3).

The existing `ProcessExternalEffectPolicySpec` continues to own definition
classification and runtime generic-definition admission examples.
