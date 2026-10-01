# Phase 98: Online Multi-Arm Experiment Routing in ActionEngine

status=planned
planned_at=2026-10-01
depends_on=[Phase 48](phase-48.md)
related=[Phase 97](phase-97.md)
consumer=textus-experiment
checklist=[Phase 98 Checklist](phase-98-checklist.md)

## Purpose

Extend the existing CNCF ActionEngine operation-execution chokepoint from offline Operation Evaluation capture to online multi-arm experimentation. A/B testing is the two-arm specialization, not the primary runtime model.

An online Experiment binding is resolved before physical operation execution. CNCF obtains an Arm assignment from an admitted Experiment assignment policy, resolves the Arm execution/experience plan, enriches ExecutionContext with Experiment correlation, and continues through the normal ActionEngine/Provider/Observability/Operation-Evaluation path.

Operation implementations and callers remain Experiment-transparent.

## Existing foundation

Phase 98 reuses ActionEngine/Provider execution, ExecutionContext, OperationEvaluationContext/Assignment, ExperimentEvaluationCorrelation/Sink, OperationEvaluationDeliveryRuntime, CallTree/Observability, and the textus-experiment Experiment/Arm/Run/Observation/Summary model. It does not introduce a parallel Experiment execution engine.

## Multi-arm model

~~~text
Experiment
  +-- Arm A
  +-- Arm B
  +-- Arm C
  +-- ... Arm N
        |
        v
Allocation Policy
        |
        v
Stickiness Policy
        |
        v
Assignment
~~~

Two Arms with a fixed allocation reproduce ordinary A/B testing. The generic contract must not assume exactly two Arms.

Assignment is separated into:
- Allocation Policy: how traffic/opportunities are distributed among eligible Arms. Initial policies may be uniform/fixed-weight; the contract must admit later adaptive policies such as Thompson Sampling or UCB without ActionEngine changes.
- Stickiness Policy: how an assignment is retained for a request/session/user/device/entity or another admitted assignment subject.

Adaptive algorithms belong to textus-experiment policy implementations, not ActionEngine. ActionEngine consumes the resulting assignment.

## Runtime shape

~~~text
Logical Operation Invocation
  -> ActionEngine execution chokepoint
  -> Online Experiment Binding?
       no  -> normal execution
       yes -> Experiment Assignment Resolver
             -> Allocation + Stickiness policies
             -> Experiment / Run / Arm
             -> Arm Execution/Experience Plan
                  -> same Operation + effective parameters/configuration
                  -> different compatible physical Operation
                  -> AI-backed or deterministic implementation
             -> ExecutionContext correlation
             -> normal ActionEngine / Provider execution
                  -> Observability
                  -> Experiment Observation
                  -> AI Audit when AI is used
~~~

## Execution plan

An Arm identifies an execution/experience plan rather than only a provider or parameter value. It may override admitted configuration for the same Operation, route to another compatible Operation, select deterministic versus AI-backed execution, and coordinate a presentation variant. Compatibility, authorization, input/output contract and recursion/loop protection fail closed.

## Correlation and evidence

ExecutionContext carries Experiment, Run and Arm correlation automatically. Existing Operation Evaluation records observations. AI-backed execution independently produces Phase 97 AI Audit evidence with the same inherited correlation.

Observability records compact routing/assignment facts and correlation.

## Reward and adaptive allocation boundary

Observation/Measurement and Reward Policy are distinct from assignment. A later adaptive policy can consume admitted outcome/reward evidence and update allocation state without changing Arm execution semantics or ActionEngine.

~~~text
Observation -> Measurement -> Reward Policy -> allocation state -> next Assignment
~~~

Reward may represent business outcomes rather than superficial clicks, for example successful completion, validation/admission outcome, human correction avoidance, latency/cost-bounded success, or an application-defined measure. CNCF does not invent the reward function.

## Offline/online boundary

Corpus-driven reproducible comparison remains the offline path. Online routing uses real application requests and does not require a Corpus case. Both modes share Experiment/Arm/Run/Observation/Summary where applicable.

## Display Model and UI experiments

The same server-side assignment may span presentation and backend execution. Phase 96 Display Model carries a client-safe assigned UI variant/Arm plus Display Instance/correlation reference. UI runtimes render the assigned variant but do not allocate Arms.

Display interactions and mutations preserve correlation; CNCF restores the authoritative Experiment/Run/Arm and associates mutation lifecycle, Business Operations, optional AI Interactions and downstream outcomes with the same Arm.

An Arm may therefore represent a complete experience/execution plan such as UI variant + Operation route + AI strategy.

## Non-goals

- Replacing textus-experiment as Experiment authority.
- Making UI/Action/Operation/AI implementations perform Arm allocation.
- Hard-coding Thompson Sampling, UCB or any allocation algorithm into ActionEngine.
- Requiring AI for Experiment execution.
- Automatically declaring a winning Arm or promoting it to production policy.
