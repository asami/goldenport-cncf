# Service Bus Journal Provider

CNCF Service Bus authoritative persistence is abstracted behind a Journal SPI. PostgreSQL is not a prerequisite for Service Bus.

## Providers

- SQLiteJournalProvider: first-class zero-setup/local provider for laptops, development, CLI/single-subsystem and offline use. Stores the authoritative journal in a configured local SQLite file.
- PostgreSqlJournalProvider: server/ops provider for long-running hosts, larger journals and richer operational querying.

SQLite is not merely an emergency fallback. Both providers implement the same authoritative journal contract.

## Host profiles

- MacBook Air: no local PostgreSQL; use SQLite journal.
- Mac mini: PostgreSQL is available; use the PostgreSQL `ops` instance for operational Service Bus journal data. The `dev` DB remains a separate development database.

## Configuration

Provider selection is explicit configuration. Do not automatically switch persistence based on PostgreSQL discovery, because the journal is authoritative information and its storage location must remain predictable.

## Commit semantics

For JournalPolicy.AUTHORITATIVE, the event becomes an authoritative operational fact only after the selected JournalProvider successfully commits it. Delivery to subscribers follows successful commit. JournalPolicy.NONE messages bypass persistent journal storage.

The SPI should support append/commit and the query primitives required by correlation/timeline/Control Center without exposing provider-specific details.

## Existing EventBus baseline

This capability extends the existing CNCF `EventBus` / `EventEngine` implementation. Existing `EventPublishOption(persistent)` and persist-before-dispatch behavior are the migration baseline; new JournalPolicy/provider/transport concepts should be introduced compatibly rather than by creating a second independent bus.

## Subsystem-local SQLite journals

The default SQLite physical database boundary is one database per Subsystem. A host running many Subsystems therefore does not make every process contend for one SQLite writer lock.

Example layout:

```text
~/.textus/subsystems/<subsystem-id>/event-journal.db
```

This also aligns journal lifecycle, backup/removal and ownership with the CNCF Subsystem boundary. SQLite should normally use WAL and a reasonable busy timeout, but cross-Subsystem sharing of one SQLite file is not the default architecture.

PostgreSQL differs physically: multiple Subsystems may share the server/ops database while journal rows remain logically partitioned/identified by subsystemId. Journal API/query semantics should hide this physical difference from consumers such as Control Center.
