# HA Role Terminology Adoption

Date: 2026-11-12
Status: design terminology adopted; runtime/API names unchanged

The user adopted the proposed Primary, Read Replica, Standby and Draining Former
Primary terminology. The [HA design](../../../design/availability-architecture.md#roles-and-established-control-mechanisms)
now defines these as the canonical explanatory role vocabulary. Read service
and promotion readiness remain independent: a Read Replica may also be Standby.
Once admitted updates are settled, a retained fallback is no longer called
draining solely because its former Primary role is retained in history.

Current HA, Subsystem runtime and cache-synchronization prose is aligned with
these terms. Existing ACTIVE/STANDBY/DRAINING lifecycle state labels, APIs,
configuration vocabulary, external-source titles and historical journals are
preserved. This is terminology alignment of the agreed architecture, not a
runtime role migration or a change to either switching protocol.

Document links/anchors, whitespace, fences and the terminology delta were checked.
No application tests, SBT, commit, publication or deployment were performed.
