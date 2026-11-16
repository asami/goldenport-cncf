# Runtime Cache Synchronization and HA Drain Direction

Date: 2026-11-12
Status: design recorded; implementation and executable qualification pending

The user refined planned master/read-cache replacement: switch new reads and
updates to B while old master A completes its already admitted updates, start
replacement read cache C, retain A for switch-back, then retire it only when
work, replacement readiness and retention conditions are all satisfied.
The user further required a generic synchronization/purge mechanism for future
multiple read-cache replicas and inclusion of the proposed synchronization
properties.

The [shared synchronization design](../../../design/runtime-cache-synchronization.md)
records committed-only changes, recoverable commit-to-notification correspondence,
source-owned ordering/version semantics, duplicates and delayed changes,
update/delete/invalidation/purge, applied progress, consistent initial load and
delta catch-up, rejoin/history gaps and explicit read consistency. It separates
application data caches from the existing CAR/SAR artifact cache.

The availability and Subsystem runtime designs now distinguish new-admission
authority from old-work completion authority, preserve synchronization of
retained former masters, and require all retirement conditions. Their original
switch-before-drain direction is retained and clarified. No wait-for-all-old-
updates-before-switch alternative was adopted. Temporary old-work completion
does not introduce unrestricted active-active admission.

Transport, owning persistence capability, public ABI, cache application semantics
and executable acceptance remain future implementation work. No runtime source,
generated contract, dependency, configuration or technical-guide publication
was changed. This work ran document/static checks only; it did not run SBT,
application tests, commit, publish or deploy.
