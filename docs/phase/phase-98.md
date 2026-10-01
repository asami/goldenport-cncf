# Phase 98: Online Experiment Routing in ActionEngine

status=planned
planned_at=2026-10-01
depends_on=[Phase 48](phase-48.md)
related=[Phase 97](phase-97.md)
consumer=textus-experiment
checklist=[Phase 98 Checklist](phase-98-checklist.md)

## Purpose

Extend the existing CNCF ActionEngine operation-execution chokepoint from offline Operation Evaluation capture to online A/B experimentation.

An online Experiment binding is resolved before physical operation execution. CNCF selects an Experiment Arm according to an admitted assignment policy, resolves the Arm execution plan, enriches ExecutionContext with Experiment correlation, and then continues through the normal ActionEngine/Provider/Observability/Operation-Evaluation path.

Operation implementations and callers remain Experiment-transparent.

## Existing foundation

Phase 98 reuses the existing:
- ActionEngine and Provider execution path;
- ExecutionContext;
- OperationEvaluationContext and OperationEvaluationAssignment;
- ExperimentEvaluationCorrelation;
- ExperimentEvaluationSink and OperationEvaluationDeliveryRuntime;
- CallTree/Observability;
- textus-experiment Experiment/Arm/Run/Observation/Summary model.

It does not introduce a parallel Experiment-specific execution engine.

## Runtime shape

~~~text
Logical Operation Invocation
        |
        v
ActionEngine execution chokepoint
        |
        v
Online Experiment Binding?
   no --+--> normal execution
   yes
        |
        v
Experiment Assignment Resolver
        |
        v
Experiment / Run / Arm
        |
        v
Arm Execution Plan Resolution
        |
        +--> same Operation + different effective parameters/configuration
        |
        +--> different physical Operation
        |
        +--> AI-backed or deterministic implementation
        |
        v
ExecutionContext correlation
        |
        v
normal ActionEngine / Provider execution
        |
        +--> Observability
        +--> Operation Evaluation / Experiment Observation
        +--> AI Audit when the selected implementation uses AI
~~~

## Logical and physical operation

The caller invokes a logical Operation. Experiment routing may resolve a different physical execution target without changing the caller contract.

The execution record must preserve both logical and physical operation identities plus Experiment/Run/Arm correlation.

## Assignment

Assignment policy is owned by the Experiment domain and consumed through an admitted CNCF-facing contract. Phase 98 must support deterministic/stable assignment suitable for online traffic while avoiding application-specific identity semantics in CNCF.

Assignment unit and key derivation are explicit policy inputs. Random reassignment on every invocation is not the default for user/session/entity scoped experiments.

## Execution plan

An Arm identifies an execution plan rather than only a provider or parameter value. The plan may:
- override admitted effective parameters/configuration for the same Operation;
- route to another compatible Operation;
- select a deterministic versus AI-backed implementation;
- later support broader Workflow execution shapes without changing the initial Operation contract.

Compatibility, authorization, input/output contract and recursion/loop protection must fail closed before dispatch.

## Correlation and evidence

ExecutionContext carries Experiment, Run and Arm correlation automatically. Existing Operation Evaluation records the online observation. If the selected execution invokes AI, Phase 97 AI Audit independently records the AI Interaction and inherits the same correlation; the AI runtime is not Experiment-aware.

Observability records compact routing/assignment facts and correlation, not Experiment payloads.

## Offline/online boundary

Existing Corpus-driven reproducible comparison remains the offline path. Online routing does not require a Corpus case. Both modes converge on the same Experiment Arm/Run/Observation/Summary concepts where applicable.

## Non-goals

- Replacing textus-experiment as Experiment authority.
- Making Action/Operation implementations branch on Arm identifiers.
- Embedding provider/model selection into generic CNCF Experiment routing.
- Requiring AI for Experiment execution.
- Automatically declaring a winning Arm or promoting it to production policy.
