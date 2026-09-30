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


## Locale-aware Display Model

Display Projection must accept a display context that includes at least locale. The server prepares locale-aware presentation data rather than requiring every client to reproduce server-side I18N rules.

Display values must not replace semantic values. For values that may need client-side re-presentation, the protocol carries both the semantic value and the server-prepared display value. The semantic value remains the authoritative value; the display value is the server's recommended presentation for the requested context.

Conceptually:

Semantic/Application Model -> View Model -> Display Projection(DisplayContext) -> Display Model

DisplayContext initially includes locale and is extensible to timezone, unit system, and other presentation context where appropriate.

The protocol must support structures equivalent to semantic value + display value for dates/times, numbers, currency, units, status/enumeration labels, and other localized values. The exact wire shape remains a protocol design task.

This allows a smartphone client to honor device/user presentation settings by re-presenting the semantic value when necessary, while Web/Flutter clients can otherwise use consistent server-prepared I18N output. Client re-presentation must not change application semantics.


## Platform-independent Display Model and Action Protocol

Display Model and Action descriptors are platform-neutral contracts. They must express presentation meaning and interaction meaning without embedding Flutter, Web, Wear OS, watchOS, or other platform implementation objects.

The Display Model should carry logical presentation roles such as title, summary, fields, sections, semantic/display values, priority and available actions. Presentation adapters may select or reshape these roles for the available surface. A Fold may realize List + Detail + Evidence while a Watch may realize only a primary summary and primary action from the same semantic candidate.

The Action Protocol is especially important for cross-platform continuity. Actions such as confirm, reject, defer, edit, open-detail, standard display mutation, and business-operation invocation must be represented by stable semantics and targets, then projected to platform-native interactions such as Flutter buttons, Web actions, Wear OS notification actions, or watchOS actions.

A platform adapter changes realization, not application semantics. The first stress-test scenario is Candidate-Admission: a Watch confirmation action should invoke the same admission semantics as Smartphone, Fold, Web, or Desktop.
