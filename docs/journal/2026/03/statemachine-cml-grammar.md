# StateMachine CML Grammar
==========================

status=working-draft
published_at=2026-03-19

---

# Goal

Define a practical CML grammar for state machine descriptions used by Cozy generation and CNCF runtime integration.

This grammar targets:
- clear authoring in markdown-like CML
- deterministic AST generation
- compatibility with SM-01/SM-02 runtime contracts

---

# Top-Level Structure

```text
StateMachineDocument
  ::= StateMachineHeader
      StateMachineBody

StateMachineHeader
  ::= "# StateMachine" NEWLINE
      NEWLINE
      "##" SP MachineName NEWLINE
```

---

# Body Blocks

```text
StateMachineBody
  ::= (StateSection | EventSection | MetadataSection)*

StateSection
  ::= "### State" NEWLINE
      NEWLINE
      StateDef+

EventSection
  ::= "### Event" NEWLINE
      NEWLINE
      EventDef+

MetadataSection
  ::= "### Metadata" NEWLINE
      NEWLINE
      MetadataItem*
```

---

# State Definition

```text
StateDef
  ::= "####" SP StateName NEWLINE
      NEWLINE
      (EntryBlock | ExitBlock | TransitionBlock)*

EntryBlock
  ::= "##### Entry" NEWLINE
      ActionLine*

ExitBlock
  ::= "##### Exit" NEWLINE
      ActionLine*

ActionLine
  ::= "- action :: " ActionExpr NEWLINE
```

---

# Transition Definition

```text
TransitionBlock
  ::= "##### Transition" NEWLINE
      ToLine
      OnLine
      GuardLine?
      PriorityLine?
      TransitionActionLine*

ToLine
  ::= "- to :: " StateName NEWLINE

OnLine
  ::= "- on :: " EventName NEWLINE

GuardLine
  ::= "- guard :: " GuardExpr NEWLINE

PriorityLine
  ::= "- priority :: " Integer NEWLINE

TransitionActionLine
  ::= "- action :: " ActionExpr NEWLINE
```

Notes:
- `priority` omitted means default priority (`0`).
- smaller value means higher priority.
- same priority uses declaration order.

---

# Event Definition

```text
EventDef
  ::= "####" SP EventName NEWLINE
      NEWLINE?
      PayloadBlock?

PayloadBlock
  ::= "##### Payload" NEWLINE
      PayloadField+

PayloadField
  ::= FieldName SP "::" SP TypeName NEWLINE
```

---

# Guard Expression Grammar

```text
GuardExpr
  ::= GuardRef
   | GuardExpression

GuardRef
  ::= Identifier

GuardExpression
  ::= <any non-empty expression text>
```

Parsing rule:
- if guard text is a single identifier, parse as `GuardExpr.Ref`
- otherwise parse as `GuardExpr.Expression`

Examples:
- `paymentConfirmed` -> `Ref`
- `event.amount > 0` -> `Expression`
- `event.amount > 0 && state.confirmed` -> `Expression`

---

# Action Expression Grammar

```text
ActionExpr
  ::= Identifier
   | QualifiedName

QualifiedName
  ::= Identifier ("." Identifier)+
```

Resolution is runtime-bound through `ActionBindingResolver`.

---

# Lexical Rules

```text
MachineName ::= Identifier
StateName   ::= Identifier
EventName   ::= Identifier
FieldName   ::= Identifier
TypeName    ::= QualifiedName | Identifier

Identifier  ::= [A-Za-z_][A-Za-z0-9_]*
Integer     ::= "-"? [0-9]+
SP          ::= " "+
NEWLINE     ::= "\n"
```

---

# Minimal Valid Example

```text
# StateMachine

## OrderStatus

### State

#### Created

##### Transition
- to :: Paid
- on :: pay
- guard :: event.amount > 0
- action :: recordPayment

#### Paid

### Event

#### pay

##### Payload
amount :: Money
```

---

# AST Mapping Summary

- Machine -> `StateMachineDef`
- State -> `StateDef`
- Transition -> `TransitionDef`
- Event -> `EventDef`
- Guard text -> `GuardExpr` (`Ref` or `Expression`)
- Action lines -> `ActionExpr`

---

# Validation Rules

1. Machine name is required.
2. At least one state is required.
3. Transition must include `to` and `on`.
4. Target state in `to` must exist.
5. Event in `on` must exist in Event section (or be auto-declared by policy).
6. Priority collisions are allowed; declaration order resolves ties.

---

# Open Points

1. Whether unknown events are auto-created or rejected.
2. Whether payload type names require import resolution at parse phase.
3. Whether metadata section should include explicit initial state declaration.

