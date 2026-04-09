# Assembly Selection And Observability Note

## Purpose

Record the current framework direction for component assembly selection and observability.

## Problem

When duplicate component names are discovered from different sources, the runtime must do more than just pick one.

It must also:

- record what was chosen
- record what was dropped
- record why that happened
- expose the decision to operators and tooling

## Current Direction

### Winner Selection Policy

The runtime uses source-sensitive selection rather than silent ambiguity.

Current policy includes cases such as:

- bundled subsystem component preferred over standalone component artifact

The exact priority table may continue to evolve, but explicit selection policy is now part of runtime behavior.

### Assembly Warnings

Duplicate and override situations should be preserved as assembly warnings, not hidden.

The warning model is intended to capture:

- duplicate component names
- winner selection reason
- dropped origins
- future conflict categories such as version or binding mismatches

### Observability Surface

The current admin surface includes:

- `admin.assembly.warnings`
- `admin.assembly.report`

The assembly report now carries:

- loaded components
- warnings
- raw wiring
- resolved wiring bindings
- port declarations

## Dashboard Direction

Assembly warnings are considered operationally important enough to appear in future admin/dashboard views.

That means:

- warnings should remain structured
- selection reasons should remain inspectable
- report data should be reusable outside the CLI

## Next Step

- extend assembly reporting as new selection cases appear
- add more explicit policy notes when component and subsystem activation rules are implemented
