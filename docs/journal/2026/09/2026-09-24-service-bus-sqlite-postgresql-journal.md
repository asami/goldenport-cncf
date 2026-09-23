# Service Bus SQLite/PostgreSQL Journal

Date: 2026-09-24

Decision: CNCF Service Bus journal persistence will have a provider SPI with SQLite and PostgreSQL as first-class implementations. PostgreSQL is not required to use authoritative journal events.

MacBook Air uses a local SQLite journal because local PostgreSQL/OpenTelemetry are intentionally absent. Mac mini uses PostgreSQL `ops` for the operational journal; PostgreSQL `dev` remains separate for development use.

Provider selection is explicit rather than discovery-based. AUTHORITATIVE events are committed through the configured provider before subscriber delivery. This preserves the same event semantics across laptop/offline and server deployments.
