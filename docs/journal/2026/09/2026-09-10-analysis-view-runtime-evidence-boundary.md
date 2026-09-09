# Analysis Views and CNCF Runtime Evidence Boundary

Date: 2026-09-10

## Context

Textus CBD Support is extending its stakeholder-facing model projections with Mono-Koto Analysis, terminology/BoK linkage, Event Storming, Use Case, and an approachable Flowchart projection of Workflow.

CNCF must remain aligned with these views without taking ownership of their presentation semantics.

## Responsibility Boundary

CBD Support owns presentation and cross-view navigation:

- Mono-Koto Analysis as conceptual/domain overview and terminology bridge;
- Use Case as actor/goal/stakeholder-intent view;
- Event Storming as stakeholder-facing behavioral overview;
- Flowchart as an approachable projection that may intentionally simplify Workflow;
- engineering Event, Workflow, and StateMachine views.

CNCF owns runtime semantics and attributable runtime evidence. It must not introduce separate Mono-Koto, Event Storming, or terminology models merely to support those views.

## Runtime Evidence Useful to Event Storming

Where already represented by authoritative runtime/model contracts, CNCF should expose bounded attributable evidence sufficient for downstream projection of concepts such as:

```text
Command / operation invocation
  -> affected Entity / Aggregate
  -> emitted Domain Event
  -> Workflow reaction / policy execution
  -> subsequent operation / event
```

Relevant evidence may include:

- operation/command identity;
- admitted event identity and cause/consequence correlation;
- affected Entity/Aggregate identity where semantically available;
- Workflow activity/reaction identity;
- external component interaction where already attributable;
- Workflow failure, compensation, and recovery events where applicable;
- StateMachine transition evidence where applicable.

The runtime must preserve distinction between declared model semantics, observed execution evidence, inferred/advisory information, and unavailable evidence.

## Actor and Terminology Boundary

Actor identity is normally owned by Use Case/model semantics upstream and consumed by CBD Support. CNCF must not infer human/business Actors from runtime principals, caller names, or operation names unless an explicit admitted mapping exists.

Terminology/BoK linkage is likewise not a CNCF runtime responsibility. CNCF may preserve stable semantic identifiers that permit CBD Support to join runtime evidence to terminology/model concepts, but it does not resolve synonyms or domain vocabulary.

## Event Storming Boundary

Event Storming is not a CNCF runtime model and is not equivalent to the CNCF Event subsystem. It is a downstream cross-model projection combining Use Case Actor, Command/Operation, Entity/Aggregate, Event, Workflow policy/reaction, external-system, and Query/View semantics.

CNCF therefore exposes facts/evidence; CBD Support decides how those facts are arranged in Event Storming presentation.

Hotspots or unresolved analysis questions are CBD Support analysis/review observations and are not runtime facts.

## Workflow and Flowchart Boundary

Workflow remains the faithful engineering semantic representation. CBD Support may provide a Flowchart view that is intentionally easier for non-engineers and is not necessarily faithful to every Workflow detail.

CNCF must not weaken or simplify Workflow runtime semantics to make Flowchart generation easier. The Flowchart simplification is a downstream presentation concern.

## Phase 72 Impact

Phase 72 remains responsible for admitted lifecycle semantics and bounded runtime evidence. No new stakeholder-view runtime subsystem is required.

During Phase 72 runtime-evidence work, ensure that stable semantic identities and attributable event/workflow/state evidence are sufficient for downstream CBD Support cross-navigation when the upstream normalized metadata supplies the necessary links.

Missing Actor, terminology, semantic grouping, or Event Storming presentation metadata remains an upstream/downstream contract gap and must not be reconstructed heuristically inside CNCF.

## Alignment Principle

```text
Canonical / normalized model semantics
             |
             v
         CNCF runtime
             |
      attributable evidence
             |
             v
       CBD Support model
             |
     +-------+---------+
     |       |         |
 Mono-Koto  Event   Event Storming
            Model       |
                    Flowchart / Workflow / StateMachine
```

CNCF provides faithful runtime semantics and evidence. CBD Support provides stakeholder and engineering projections over shared semantic identities.
