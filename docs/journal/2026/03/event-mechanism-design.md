CNCF Event Mechanism Design (Core → Runtime → Integration)
=========================================================

status=work-in-progress
published_at=2026-03-20

# OVERVIEW

The CNCF event mechanism integrates:

- the same execution model as Command
- database persistence (optional)
- replay
- compensation
- job management
- StateMachine integration

---

# 1. Event Core Model

## Basic Structure

```
case class EventEnvelope(
  name: String,
  persistent: Boolean = true,
  kind: EventKind = EventKind.Domain,
  payload: Option[Any] = None,
  attributes: Map[String, Any] = Map.empty
)
```

---

## EventKind

```
enum EventKind {
  case Domain
  case System
  case Integration
}
```

---

## Design Principles

- Event is pure data
- core does NOT handle dispatch or execution
- Event represents “fact + trigger”

---

# 2. EventBus Interface (CNCF)

## Publisher

```
trait EventPublisher {
  def publish(event: EventEnvelope): Consequence[Unit]
}
```

---

## Handler

```
trait EventHandler {
  def handle(event: EventEnvelope): Consequence[Unit]
}
```

---

## Subscription

```
case class EventSubscription(
  eventName: String,
  actionName: String
)
```

---

## EventBus

```
trait EventBus {
  def publish(event: EventEnvelope): Consequence[Unit]
  def register(subscription: EventSubscription): Consequence[Unit]
}
```

---

# 3. Unified Execution Model

## ActionCall-Centric Model

```
Command → Operation → Action → ActionCall
Event   → Reception → Action → ActionCall
```

---

## Principle

<strong>ActionCall is the single execution unit</strong>

---

# 4. Dispatch Specification (Synchronous)

## Flow

```
publish(event)
  ↓
(optional) persist
  ↓
resolve subscriptions
  ↓
create ActionCall
  ↓
execute
```

---

## Transaction

```
domain update
+ event persist
+ event dispatch
-------------------
commit
```

---

## Policy

- default: synchronous
- same transaction
- deterministic order

---

# 5. Async Execution (Job Integration)

```
Event
  ↓
register as Job
  ↓
ActionCall
```

---

## Characteristics

- same model as Command
- retryable
- distributable

---

# 6. Persistence Model

## Persistent Event

Used for:

- replay
- audit
- recovery

---

## Ephemeral Event

Used for:

- lightweight notification
- high-frequency processing

---

## Atomicity

```
data update + event persist = same transaction
```

---

# 7. Replay & Compensation

## Replay

```
EventStore → replay → ActionCall
```

---

## Compensation

```
Event → CompensationAction
```

---

# 8. Routing

```
Event(name)
  ↓
ActionResolver
  ↓
ActionCall
```

---

# 9. StateMachine Integration

## Flow

```
event received
  ↓
select transition
  ↓
evaluate guard
  ↓
execute action
  ↓
update state
  ↓
publish event
```

---

## Integration Points

- Event = StateMachine input
- Event = StateMachine output

---

# 10. CML Representation

## Event Definition

```
# Event

## OrderPaid

### Kind
domain

### Payload
orderId :: OrderId
amount :: Money
```

---

## Subscription

```
# Subscription

## Billing

- on :: OrderPaid
- action :: recordPayment
```

---

## StateMachine Integration

```
##### Transition
- on :: pay
- action :: recordPayment
```

---

# 11. Design Decisions

## 1. Event = Action

- Event also acts as Action
- unified execution path

---

## 2. Persistent / Ephemeral Separation

- performance optimization
- storage control

---

## 3. Unified with Command

- same Job model
- same ActionCall

---

## 4. Transaction Integration

- strong consistency

---

# 12. Advantages

## Reproducibility

- replayable

## Extensibility

- functionality via event addition

## AI Compatibility

- explicit structure

---

# 13. Implementation Order

1. Event core model
2. EventBus interface
3. synchronous dispatch
4. StateMachine integration
5. CML support

---

# 14. Design Status

- core model: defined
- event bus: defined
- execution model: unified
- state machine integration: defined

Next:

→ EventStore
→ Job integration details
→ runtime implementation

---

# SUMMARY

<span lang="en">The CNCF event mechanism is integrated into a unified execution model centered on ActionCall.</span>

<span lang="en">This connects Command, Event, StateMachine, and Job in a consistent structure.</span>
