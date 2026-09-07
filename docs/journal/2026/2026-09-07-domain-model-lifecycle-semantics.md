# Domain Model Lifecycle Semantics for Component Dashboard

Date: 2026-09-07

A Textus CBD Support Dashboard design discussion identified a runtime implication for CNCF: composition and aggregation should be modeled strictly enough that their lifecycle distinction produces executable benefits.

The Dashboard DomainModel View will separate Static Model and Dynamic Model. Static Structure View treats composition, aggregation, and association as different semantic relationships. Dynamic Model uses Workflow as the overview and StateMachine as the lifecycle deep dive.

The resulting cross-model relationship is important for CNCF. Composition can imply owner-dependent creation/deletion, restricted reparenting, lifecycle propagation, aggregate/persistence boundaries, and owner-mediated mutation. Aggregation instead preserves independent member lifecycle and can permit membership change or reassignment. Association carries reference semantics without ownership propagation.

These semantics should come from authoritative generated model metadata rather than CNCF inference. Cozy remains responsible for CML/model transformation and metadata publication. CNCF should enforce admitted runtime semantics where appropriate. Textus CBD Support should visualize and explain them and may use runtime results as Review evidence.

A future implementation pass should compare this requirement with existing Entity, Aggregate, relation, Workflow, and StateMachine runtime contracts before defining any extension.

Detailed notes are recorded in `docs/notes/domain-model-lifecycle-semantics.md`.
