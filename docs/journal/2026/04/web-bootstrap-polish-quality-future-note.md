# Web Bootstrap Polish Quality Future Note

## Context

The current Phase 12 Bootstrap polish work is intentionally conservative. It
uses Bootstrap 5 as the UI vocabulary and applies stable page-composition rules
to built-in Web pages without changing runtime contracts.

That current contract is recorded in:

- `docs/notes/web-bootstrap-ui-polish-design.md`

This journal entry records the next layer of UI quality discussion. These items
are design exploration, not current implementation requirements.

## Current Boundary

Current implementation polish means:

- Bootstrap 5 is the default output vocabulary.
- The framework improves page composition, not by creating a separate design
  system, but by applying consistent information hierarchy, action placement,
  responsive layout, and feedback patterns.
- Built-in pages must still work offline with local Bootstrap assets.
- Runtime contracts remain stable.

This is sufficient for the first Management Console entity pass:

- card sections for navigation and main content
- responsive hover tables for records
- grouped row actions
- Bootstrap form controls and validation feedback
- create/update/cancel action areas

## Future Quality Direction

After the current management-console, dashboard, manual, and static-form polish
passes, the next design questions are about higher-level visual quality.

Possible future criteria:

- stronger visual identity for CozyTextus while still using Bootstrap 5
- a minimal theme layer for colors, spacing, typography, and density
- consistent empty-state, loading-state, warning-state, and success-state
  components
- card-based summary views for user-facing business screens
- better status and health indicators for dashboard and admin pages
- responsive screenshot checks for desktop, tablet, and smartphone widths
- accessibility checks for labels, button text, contrast, and focus behavior
- explicit density modes for admin pages and user-facing pages

These are deliberately not required for the current WEB-10 first pass.

## Raw Bootstrap Versus Polish

Raw Bootstrap gives components such as cards, tables, forms, alerts, badges, and
buttons. The framework polish layer should define how those components compose
into useful CNCF/Textus pages.

The distinction is:

- raw Bootstrap: component-level markup
- current polish: framework page-composition rules using Bootstrap
- future polish: optional theme and richer UI quality rules on top of the same
  Bootstrap vocabulary

The future theme layer should stay small. If it grows into an application design
system or SPA framework, that should be treated as a separate product/design
decision.

## Relation To Textus Widgets

Card and layout widgets are the likely bridge from built-in admin pages to
application-facing static pages.

Related design notes:

- `docs/notes/web-textus-widget-design.md`
- `docs/journal/2026/04/web-bootstrap-card-widget-note.md`

Future widget-driven polish should preserve the same principles:

- CML/schema/view metadata remains the primary source of field order and labels.
- WebDescriptor remains supplemental.
- Widgets render ordinary Bootstrap 5 markup.
- Widget behavior must not require control-flow syntax in Static Form App HTML.

## Open Questions

- How much CozyTextus visual identity should be applied by default?
- Should a small `textus.css` asset exist in addition to local Bootstrap?
- Which pages need screenshot-level checks before future visual polish can be
  considered stable?
- Should admin pages and public Static Form App pages use different density
  defaults?
- Which status/empty-state patterns should become reusable Textus widgets?

## Next Step

Treat WEB-10 as the completed first-pass Bootstrap composition baseline. Revisit
this journal entry when WEB-11 widgets or later theme work identify concrete
gaps that cannot be solved by Bootstrap composition alone.
