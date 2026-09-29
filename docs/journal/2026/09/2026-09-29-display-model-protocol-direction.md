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
