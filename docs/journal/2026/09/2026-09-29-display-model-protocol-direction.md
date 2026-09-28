# Display Model Protocol Direction

Date: 2026-09-29
Status: architectural direction

## Decision

CNCF keeps its existing View/Read Model as a semantic contract and adds a distinct presentation-facing Display Model.

Semantic/Application Model -> CNCF View Model -> Display Projection -> Display Model -> Display Model Protocol -> presentation clients.

Display Model is not a renamed View Model. View Model preserves application meaning. Display Model is an instance of a target-neutral Abstract/Logical UI vocabulary and describes what can be displayed and acted on.

Display Projection may map semantic names to presentation roles (for example product_name to title), reshape properties, convert JSON/server-native values into displayable typed values, and attach presentation Action descriptors backed by CNCF Operation/Command semantics.

The protocol must not expose Flutter Widget trees or make CNCF depend on Flutter.

## Initial proof

Use the Editing Studio List -> Detail -> one Action path as the first vertical proof. The client must replace mock data with CNCF-backed Display Models without rewriting standard List/Detail UI.

The target-neutral Abstract/Logical UI vocabulary is owned by Cozy. CNCF owns runtime Display Projection, Display Model instances, versioned protocol representation, and binding to View/Operation semantics.

Cross-project status is coordinated by textus-knowledge-workbench strategy knowledge-application-integration.md.
