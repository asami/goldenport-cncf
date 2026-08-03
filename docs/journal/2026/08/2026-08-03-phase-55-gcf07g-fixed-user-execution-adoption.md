# Phase 55 GCF-07G Fixed-User Execution Adoption

Date: 2026-08-03

GCF-07G consumes the final resolved generic collection for fixed-user execution.
The required identity and optional display name, locale, and timezone are
resolved through the registered CNCF catalog witnesses. Fixed ingress uses the
typed identity and display name, and applies typed locale/timezone after request
formatting recovery. It rebinds Unit of Work so it receives the same formatting
authority. Authenticated and controlled execution does not receive this profile.

The runtime acceptance exercises the production sequence: retained runtime
snapshot candidates, preliminary fixed-profile selection, HOME profile
admission, candidate merge, final generic resolution, and Subsystem admission.
It proves retained `standalone` mode remains authoritative over a conflicting
legacy `multi-user` value while the typed fixed profile is retained. Review
fixed an outer/Unit-of-Work formatting mismatch and added this acceptance seam.

Validation: CNCF `Test/compile`, the focused related suites (38/38), and the
filtered runtime acceptance (1/1) passed through the serialized runner at
`26093-20260802T221636Z`. Final independent re-review is clean. No Phase 53
CS-01--CS-07 historical source or specification was changed.
