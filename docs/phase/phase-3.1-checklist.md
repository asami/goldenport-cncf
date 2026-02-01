# Phase 3.1 — Execution / Orchestration Foundation Checklist

This document contains **detailed task tracking and execution decisions**
for Phase 3.1 (Execution / Orchestration Foundation).

It mirrors the structure, semantics, and closure rules of the
Phase 2.85 checklist to ensure continuity of workflow and judgment.

---

## Checklist Usage Rules

- This document holds **detailed status and task breakdowns**.
- The phase document (`phase-3.md`) holds **summary only**.
- A development item marked DONE here must also be marked `[x]`
  in the phase summary document.
- Deep reasoning, experiments, and exploration must be recorded
  in journal entries, not inline here.

---

## Checkbox Operation Rules

### Meaning of `[ ]`

- A checkbox `[ ]` indicates that the item is **not yet complete
  in the context of Phase 3.1**.
- `[ ]` may represent one of the following states:
  - BACKLOG
  - PLANNED
  - ACTIVE
  - SUSPENDED

### Meaning of `[x]`

- A checkbox `[x]` indicates that the handling of this item
  **within Phase 3.1 is complete**, regardless of outcome:
  - completed
  - cancelled
  - deferred to a next phase

### Transition Rules

- `[ ] → [x]` is allowed **only once** per item.
- Once an item is marked `[x]`:
  - It must not be modified further in this document
  - No additional tasks may be added under it
  - Any remaining work must move to a next-phase checklist

---

## Status Semantics

- **ACTIVE**  
  Currently being worked on.  
  Only one development item may be ACTIVE at a time.

- **SUSPENDED**  
  Work that was started and intentionally paused.  
  A clear resume point must exist.

- **PLANNED**  
  Planned but not started yet.  
  May be cancelled depending on circumstances.

- **BACKLOG**  
  Kept for consideration without commitment.

- **DONE**  
  Handling of this item **within Phase 3.1 is complete**.

Once marked DONE, the item must not be modified.

---

## EH-01: Fat JAR Component — Baseline Execution Form

Status: ACTIVE

### Objective

Establish **Fat JAR Component** as the baseline execution form
for Phase 3, capable of encapsulating:

- large dependency graphs
- mixed Scala versions
- isolated runtime environments

while remaining operable through CNCF's unified execution model.

---

### Detailed Tasks

- [x] Define Fat JAR Component boundary and responsibilities
- [x] Define Fat JAR as JVM-based black-box component (Scala / Java)
- [x] Define persistent component instance model (stateful allowed)
- [x] Define execution lifecycle (load → instantiate → invoke → unload)
- [x] Define failure containment and Observation conversion principles
- [x] Define Facade / Adapter resolution rules for JarCollaborator
- [x] Design ClassLoader isolation strategy (parent = null)
- [x] Define shared API surface as cncf-collaborator-api.jar (Java-only)
- [x] Define Collaborator as a standardized SPI extension point

### Concurrency Policy (Framework Responsibility)

- [x] Concurrency control is a framework responsibility
- [x] Policy declaration levels: component / service / operation
- [x] Supported policies are defined:
  - serialize
  - concurrent
  - queue

### serialize Policy Semantics

- [x] serialize means sequential dispatch with wait
- [x] Subsequent invocations wait until current execution completes
- [x] No enqueue or job scheduling is involved
- [x] Timeout and cancellation are handled by ActionEngine
- [x] Cancellation is best-effort and results in Observation

---

- [x] Load a Fat JAR Component via CNCF
- [ ] Instantiate a persistent component instance
- [ ] Invoke exactly one Operation successfully
- [ ] Verify serialize wait behavior
- [ ] Verify Observation returned on execution failure

---

### Decisions

- Fat JAR Component is treated as **baseline**, not an edge case
- ClassLoader isolation is mandatory, not optional
- Execution failure must never crash CNCF runtime
- ActionEngine manages execution lifecycle; component state is initialized independently
- Concurrency semantics are enforced by the framework, not components
- serialize is the default policy for stateful components
- Facade / Adapter resolution rules are fixed and documented in phase-3.1.md
- The only API allowed to cross the ClassLoader boundary is cncf-collaborator-api.jar
- Collaborator serves as a standardized SPI extension point for pluggable execution logic

---

### ClassLoader Isolation — PoC Verification Checklist

#### Construction
- [ ] Create isolated ClassLoader with parent = null
- [ ] Load Fat JAR exclusively through the isolated ClassLoader
- [ ] Load cncf-collaborator-api.jar explicitly into the isolated ClassLoader

#### Visibility Rules
- [ ] JDK bootstrap classes (java.*, javax.*, jdk.*) are accessible
- [ ] cncf-collaborator-api.jar types are accessible
- [ ] CNCF runtime packages (org.goldenport.cncf.*) are NOT visible
- [ ] Scala standard library is NOT visible unless bundled in Fat JAR

#### Resolution & Instantiation
- [ ] Facade class loads successfully when declared
- [ ] Facade is instantiated via isolated ClassLoader
- [ ] Facade is wrapped with FacadeCollaboratorAdapter
- [ ] CollaboratorFactory returns CNCF Collaborator only

#### Failure Containment
- [ ] ClassNotFoundException is converted to Observation
- [ ] LinkageError / NoSuchMethodError is converted to Observation
- [ ] RuntimeException thrown inside Fat JAR is converted to Observation
- [ ] CNCF runtime remains alive after failures

#### Behavioral Guarantees
- [ ] Multiple invocations do not leak classes across ClassLoaders
- [ ] Repeated loads do not accumulate ClassLoader references
- [ ] Component unload releases isolated ClassLoader references

#### Evidence
- [ ] Log confirms parent=null ClassLoader creation
- [ ] Log confirms rejected access to CNCF runtime classes
- [ ] Observation includes taxonomy code and minimal attributes

---

### Journal Links

- (to be added) Fat JAR loading experiments
- (to be added) ClassLoader behavior notes

---

## EH-02: Execution Infrastructure Responsibility Definition

Status: PLANNED

### Objective

Clarify **what the execution infrastructure does and does not do**
before introducing additional component forms.

---

### Detailed Tasks

- [ ] Define responsibilities of IsolatedClassLoaderProvider
- [ ] Define non-responsibilities explicitly
- [ ] Confirm separation from execution control (ActionEngine)
- [ ] Align with CollaboratorFactory internal usage model

---

### Notes

- Docker, RPC, and AI Agent concerns are explicitly excluded here
- Execution Hub terminology is deprecated in Phase 3.1

---

## Deferred / Next Phase Candidates

- Docker Component execution (Phase 3.x)
- Antora integration
- CML → Component generation
- AI Agent Hub integration

---

## Completion Check

Phase 3.1 is complete when:

- All ACTIVE items are marked DONE or DEFERRED
- No item remains ACTIVE or SUSPENDED
- Phase summary reflects closure accurately
