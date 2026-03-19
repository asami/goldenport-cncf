CNCF EventStore Design (Append-Only / Replay / Pruning)
=====================================================

status=work-in-progress
published_at=2026-03-20

---

# OVERVIEW

EventStore is responsible for:

- durable storage of events
- replay capability
- supporting compensation and recovery
- integration with ActionCall and Job execution

EventStore is NOT responsible for:

- routing
- execution
- subscription resolution

Those belong to EventBus / CNCF runtime.

---

# 1. Design Principles

## 1. Append-Only

Events are immutable.

```
append(event)
```

- no update
- no delete (logical only)

---

## 2. Event as Source of Truth (optional)

EventStore may be used for:

- audit log (mandatory)
- replay (optional)
- event sourcing (future)

---

## 3. Transactional Consistency

Event persistence MUST be in the same transaction as domain updates.

```
domain update
+ event append
----------------
commit
```

---

## 4. Separation of Concerns

| Responsibility | Component |
|---------------|----------|
| storage | EventStore |
| dispatch | EventBus |
| execution | ActionCall |
| orchestration | Job |

---

# 2. Core Interface

```
trait EventStore {
  def append(event: EventEnvelope): Consequence[EventId]

  def load(id: EventId): Consequence[Option[EventEnvelope]]

  def query(q: EventQuery): Consequence[Vector[EventEnvelope]]

  def replay(q: EventQuery): Consequence[Unit]
}
```

---

# 3. Event Model (Extended)

```
case class EventRecord(
  id: EventId,
  name: String,
  kind: EventKind,
  payload: Option[Any],
  attributes: Map[String, Any],
  createdAt: Instant,
  persistent: Boolean,
  status: EventStatus
)
```

---

## EventStatus

```
enum EventStatus {
  case Pending
  case Processed
  case Failed
  case Compensated
}
```

---

# 4. EventId

```
case class EventId(value: String) extends AnyVal
```

Requirements:

- globally unique
- sortable (recommended)
- traceable

---

# 5. Query Model

```
case class EventQuery(
  name: Option[String] = None,
  kind: Option[EventKind] = None,
  from: Option[Instant] = None,
  to: Option[Instant] = None,
  status: Option[EventStatus] = None,
  limit: Option[Int] = None
)
```

---

# 6. Replay

## Basic Replay

```
events = store.query(query)

events.foreach { e =>
  dispatch(e)
}
```

---

## Replay Modes

### 1. Full Replay

- entire history

### 2. Partial Replay

- filtered by query

### 3. Idempotent Replay

- safe re-execution required

---

# 7. Compensation

```
Event (Failed)
  ↓
CompensationAction
  ↓
ActionCall
```

---

## Strategy

- explicit compensation mapping
- or action-level compensation

---

# 8. Persistence Strategy

## Table Structure (RDB example)

```
events (
  id            VARCHAR PRIMARY KEY,
  name          VARCHAR,
  kind          VARCHAR,
  payload       JSON,
  attributes    JSON,
  created_at    TIMESTAMP,
  persistent    BOOLEAN,
  status        VARCHAR
)
```

---

## Index

- (name)
- (created_at)
- (status)
- (kind)

---

# 9. Ephemeral Event Handling

```
if (event.persistent == false)
  skip store.append
```

---

## Policy

- still dispatched
- not replayable
- not queryable

---

# 10. Pruning Strategy

EventStore is append-only, but pruning is required.

---

## 10.1 Logical Deletion

```
status = archived
```

---

## 10.2 Physical Deletion

- TTL-based
- batch cleanup

---

## 10.3 Snapshot Strategy (future)

```
state snapshot
+ discard old events
```

---

# 11. Integration with Job

```
Event
  ↓
Job registered
  ↓
Job execution
  ↓
Event status updated
```

---

## Retry

```
status = Failed
  ↓
retry policy
```

---

# 12. Ordering Guarantees

Options:

- global ordering (costly)
- per-aggregate ordering (recommended)

---

# 13. Consistency Model

## Strong Consistency

- same transaction
- synchronous append

---

## Eventual Consistency (future)

- async append
- distributed systems

---

# 14. Error Handling

## Append Failure

- transaction rollback

---

## Replay Failure

- mark event as Failed
- trigger compensation or retry

---

# 15. Implementation Phases

## Phase 1 (MVP)

- append
- load
- simple query
- synchronous replay

---

## Phase 2

- status management
- retry
- compensation

---

## Phase 3

- pruning
- snapshot
- distributed support

---

# SUMMARY

EventStore provides:

- durable event persistence
- replay capability
- foundation for compensation and recovery

It works with:

- EventBus (dispatch)
- ActionCall (execution)
- Job (orchestration)

and becomes a core infrastructure of CNCF.
