# Domain Model Lifecycle Semantics

Date: 2026-09-07

## Context

Textus CBD Support is defining a semantic Component Dashboard backed by CML/model metadata. Its DomainModel View distinguishes Static Model and Dynamic Model. A key design objective is to make strict modeling of composition and aggregation produce executable benefits rather than remain diagram notation.

Cozy owns faithful CML/model metadata. Textus CBD Support owns Dashboard projection. CNCF is the natural runtime boundary for semantics that affect entity lifecycle, aggregate behavior, persistence, commands, and validation.

## Structure Semantics

Structure View distinguishes:

- composition;
- aggregation;
- association.

These relations should not collapse to one generic runtime relation when their declared semantics affect behavior.

### Composition

Composition represents strong ownership and lifecycle dependence. Candidate runtime semantics include:

- a part belongs to the declared owner according to the model cardinality;
- independent creation of the part may be prohibited;
- independent deletion may be prohibited or mediated by the owner;
- arbitrary reparenting may be prohibited;
- owner lifecycle termination may propagate to the part;
- the composition boundary may contribute to aggregate, transaction, and persistence boundaries;
- commands that mutate the part may be required to enter through the owning aggregate.

These are model-driven policies and should be enforced only when present in authoritative generated metadata; CNCF must not infer them from naming or diagram shape.

### Aggregation

Aggregation represents membership/whole-part structure with an independently existing part. Candidate runtime semantics include:

- the part can exist independently of the aggregate;
- aggregate termination does not imply part termination;
- membership removal preserves the part;
- reassignment may be permitted according to model policy;
- persistence identity/lifecycle remains independent.

### Association

Association represents reference semantics without ownership. Runtime concerns are primarily reference integrity, cardinality, and declared navigation/access policy rather than lifecycle propagation.

## Connection to Dynamic Model

The Dashboard dynamic model uses Workflow as the overview and StateMachine as the deep dive for an individual subject lifecycle.

This creates an important runtime consistency opportunity:

    Structure ownership -> lifecycle policy
    Workflow activity -> domain change
    StateMachine transition -> subject lifecycle change

For example, termination of an owner may imply termination of composed parts, while termination of an aggregate must not automatically terminate independently aggregated members. Runtime validation can detect contradictions between declared structure semantics and lifecycle transitions.

## Runtime Boundary

CNCF should consume normalized, versioned model metadata rather than own CML syntax. Any future implementation should keep these responsibilities separate:

- Cozy: model language, transformation, semantic metadata publication.
- CNCF: runtime realization and enforcement of admitted model semantics.
- Textus CBD Support: semantic visualization, navigation, Review evidence, and explanation.

Before implementation, existing Entity/Aggregate/StateMachine runtime contracts should be reviewed to determine which lifecycle rules are already represented and which require explicit extensions. No new behavior should be inferred merely from this note.
