/*
 * @since   Apr.  6, 2026
 * @version Apr.  6, 2026
 * @author  ASAMI, Tomoharu
 */

# Subsystem-Scoped Requirement Help Policy

## Policy

Top-level requirement-model elements are treated as subsystem-scoped help metadata rather than component-scoped help metadata.

The affected requirement elements are:

- `VISION`
- `USE CASE`
- `CAPABILITY`
- `QUALITY`
- `CONSTRAINT`

## Default Rule

When these elements are written at top level in CML, they belong to an implicit subsystem by default.

They are therefore exposed through subsystem help projection, not component help projection.

## Expected Help Behavior

- `component help` must not show top-level requirement metadata.
- `subsystem help` must show top-level requirement metadata.
- component-local requirement metadata remains available where it is explicitly scoped under `COMPONENT` or `SERVICE`.

## Reason

This keeps requirement scope explicit.

Without this separation, top-level requirement descriptions appear to be component-local, which is misleading once the model needs to distinguish system, subsystem, and component concerns.
