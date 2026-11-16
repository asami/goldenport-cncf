# HA Role Terminology Notes

Date: 2026-11-12
Status: explanatory notes; the Availability Architecture owns current role semantics

## Current vocabulary

Use the terminology adopted in
[Availability Architecture](../design/availability-architecture.md#roles-and-established-control-mechanisms)
when writing current HA, cache synchronization and runtime explanations:

| Current term | Role being described |
| --- | --- |
| Primary | New authoritative mutation admission |
| Read Replica | Synchronized read-cache/projection service |
| Standby | Qualification and readiness for promotion |
| Draining Former Primary | Completion of exact pre-switch admitted work |

A Read Replica may also be Standby. Read service and takeover readiness are
different properties, not mutually exclusive machine categories. Once old work
is settled, a synchronized former Primary retained for fallback is no longer
described as draining.

## Reading historical records

Earlier records may use Master/Slave, Active/Standby or less specific read-cache
wording. Preserve those records exactly as written. Interpret their terminology
in its original context; do not mechanically infer promotion readiness merely
from an old Slave or replica label.

The [new terminology-adoption journal](../journal/2026/11/2026-11-12-ha-role-terminology-adoption.md)
records the current decision. Later refinements belong in new journal entries
and the current design/notes, not retrospective edits to earlier journals.
These notes explain the transition; they do not supersede the original history.

Existing `ACTIVE`, `STANDBY` and `DRAINING` lifecycle labels, public APIs and
configuration tokens are not renamed by prose normalization. A future runtime
name migration would require its own compatibility decision and implementation.
