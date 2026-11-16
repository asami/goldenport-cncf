# Immutable Infrastructure Operating Model Notes

Date: 2026-11-12
Status: explanatory synthesis; canonical runtime semantics remain in design

## Direction adopted in the discussion

Prioritize the parts needed to operate immutable infrastructure: reconstruct
the environment from admitted artifacts, switch new admissions while old updates
finish, synchronize all affected caches, establish replacement read capacity,
retain a current fallback and retire the old runtime conditionally. Scheduling
and compatible selected-CAR replacement reuse that engine. First local-ha
evidence does not prove host or site availability.

Use Primary, Read Replica, Standby and Draining Former Primary. Read service
and promotion readiness are separate properties. Current explanatory terms do
not retroactively alter historical journals or rename native lifecycle states.

## Questions the design now separates

| Question | Owning distinction |
| --- | --- |
| Can A finish after new admissions move to B? | Planned scoped completion authority, distinct from new-admission ownership |
| Can a failed or partitioned A later write? | Effective fencing at the actual mutation boundary; election/routing alone is insufficient |
| How do late A commits reach B and C? | Shared source-ordered cache synchronization, not a pair-specific transfer |
| When is C usable? | Consistent baseline plus complete required delta/application progress |
| When can A be retired? | Settled admitted work, replacement capacity, retention/observation and no unresolved need for A |
| What happens after unexpected authority loss? | Failover safety and outcome reconciliation; no preserved invalid completion permit |
| What is the first usable milestone? | Operational planned replacement; automatic Failover and topology expansion remain later qualification |

The [HA design](../design/availability-architecture.md),
[cache synchronization design](../design/runtime-cache-synchronization.md) and
[Phase 108 work ordering](../phase/phase-108.md) own the corresponding requirements.
This note is an explanation, not a second coordinator, progress ledger or API.

## Reader-facing guide

The new [technical guide](../../../../dev2026/textus-technical-documentation/src/main/smartdox/technical-guide/availability/textus-cncf-availability-architecture.dox/README.md)
provides the coherent explanation, including the A/B/C timeline, Switchover versus
Failover, retained fallback, generic purge/synchronization and first delivery.
Its source draft is not evidence of implemented HA. Prior journals are preserved;
the current source-authoring event is recorded in a new journal.
