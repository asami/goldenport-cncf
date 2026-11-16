# HA Technical Guide and Operating Notes

Date: 2026-11-12
Status: source guide and explanatory notes authored; runtime implementation remains open

The user requested an HA technical guide and reflection of the discussion in
design/notes. The new Document Project in `textus-technical-documentation`
contains a shared recursive Core, full Japanese/English Documents and a bilingual
SmartDox manuscript with 11 chapters and six tables.

The explanation preserves Primary/Read Replica/Standby roles, the distinction
between planned Switchover and unplanned Failover, scoped old-work completion,
generic multi-member cache synchronization, current fallback and conditional
retirement. It prioritizes operational immutable replacement over completing
automatic Failover and broader topology. IR-01..IR-05 remain OPEN.

The [HA design](../../../design/availability-architecture.md) now links the
reader-facing guide and the new
[operating-model notes](../../../notes/immutable-infrastructure-operating-model.md).
Canonical runtime semantics and Phase planning remain CNCF-owned; guide authoring
does not establish an implementation, API, producer capability or Phase completion.

Earlier journals were not rewritten. No runtime source, dependency or live
deployment was changed by this documentation work; there was no SBT, commit,
publication or remote write. Source inspection details belong to the new
technical-documentation journal and the original private command evidence.
