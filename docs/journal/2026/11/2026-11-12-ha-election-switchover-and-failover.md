# HA Election, Switchover and Failover Direction

Date: 2026-11-12
Status: design updated; control binding and implementation qualification pending

The user selected established mechanisms for Primary selection and failure
takeover, then requested that the HA design separate planned Switchover from
unplanned Failover. Earlier cache synchronization and drain work is preserved.

[Availability Architecture](../../../design/availability-architecture.md)
now adopts existing Leader Election/Lease facilities, explicit enforcement of
fencing and independent readiness qualification. etcd Election/Lease is the
first candidate binding, not an installed dependency. Existing equivalent
coordination can be reused when its actual contract qualifies. CNCF composes
the protocols and does not implement a new consensus/voting system.

Planned Switchover transfers new admissions to B while A completes exact
pre-switch admitted work under scoped continuation authority. Replacement-cache
readiness, synchronized fallback retention and all planned retirement conditions
remain required. Unplanned Failover treats lack of response as uncertainty,
requires valid takeover ownership, effective fencing and readiness before
mutation service, and reconciles durable outcomes without blind replay or
waiting for an unreachable runtime's drain acknowledgement.

The design distinguishes control voters from read-cache replicas and control
revisions from business-data commit positions. It states that election alone
cannot fence external mutations or guarantee acknowledged-write preservation.
Fenced completion permission does not survive solely because a request started
earlier; a returning former Primary rejoins as a replica before later failback.

The cache and Subsystem runtime designs link to the shared protocols and clarify
that their graceful-drain description applies to planned replacement. Public
APIs, concrete control topology/parameters, native scoped authority representation
and protected persistence/effect bindings remain implementation contracts.

Document whitespace, relative links/anchors and Markdown fences were checked.
No runtime implementation, generated source, dependency, live cluster or technical
guide was changed. No SBT/application tests, commit, publication or deployment
was performed by this documentation change.
