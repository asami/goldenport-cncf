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

Status: DONE

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
  - [x] Instantiate a persistent component instance
  - [x] Invoke exactly one Operation successfully
  - [x] Verify serialize wait behavior
  - [x] Verify Observation returned on execution failure

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

NOTE: Visibility rules, behavioral guarantees, and evidence gathering are intentionally deferred because the internal structure has stabilized and there are no architectural blockers at this time.

#### Construction
- [x] Create isolated ClassLoader with parent = null (effectively completed)
- [x] Load Fat JAR exclusively through the isolated ClassLoader (effectively completed)
- [x] Load cncf-collaborator-api.jar explicitly into the isolated ClassLoader (effectively completed)

#### Visibility Rules
- [x] Visibility Rules（核心） (Deferred) (effectively completed)
- [x] JDK bootstrap classes (java.*, javax.*, jdk.*) are accessible (effectively completed)
- [x] cncf-collaborator-api.jar types are accessible (effectively completed)
- [x] CNCF runtime packages (org.goldenport.cncf.*) are NOT visible (effectively completed)
- [x] Scala standard library is NOT visible unless bundled in Fat JAR (deferred)

#### Resolution & Instantiation
- [x] Facade class loads successfully when declared (effectively completed)
- [x] Facade is instantiated via isolated ClassLoader (effectively completed)
- [x] Facade is wrapped with FacadeCollaboratorAdapter (effectively completed)
- [x] CollaboratorFactory returns CNCF Collaborator only (effectively completed)

#### Failure Containment
- [x] ClassNotFoundException is converted to Observation (effectively completed)
- [x] LinkageError / NoSuchMethodError is converted to Observation (effectively completed)
- [x] RuntimeException thrown inside Fat JAR is converted to Observation (effectively completed)
- [x] CNCF runtime remains alive after failures (effectively completed)

#### Behavioral Guarantees
- [x] Behavioral Guarantees (Deferred) (deferred)
- [x] Multiple invocations do not leak classes across ClassLoaders (deferred)
- [x] Repeated loads do not accumulate ClassLoader references (deferred)
- [x] Component unload releases isolated ClassLoader references (deferred)

#### Evidence
- [x] Evidence (Deferred) (deferred)
- [x] Log confirms parent=null ClassLoader creation (deferred)
- [x] Log confirms rejected access to CNCF runtime classes (deferred)
- [x] Observation includes taxonomy code and minimal attributes (effectively completed)

---

### Journal Links

- (to be added) Fat JAR loading experiments
- (to be added) ClassLoader behavior notes

---

## EH-02: Execution Infrastructure Responsibility Definition

Status: DONE

### Objective

Clarify **what the execution infrastructure does and does not do**
before introducing additional component forms.

---

### Detailed Tasks

- [x] Define responsibilities of IsolatedClassLoaderProvider (scope too broad; responsibility definition deferred to a dedicated phase)
- [x] Define non-responsibilities explicitly (scope too broad; exhaustive non-responsibility listing deferred)
- [x] Confirm separation from execution control (ActionEngine) (scope too broad; separation already implied by EH-01 design)
- [x] Align with CollaboratorFactory internal usage model (scope too broad; internal alignment not a Phase 3.1 concern)

### EH-02 — Phase 3.1 Applicable Checks

- [x] Declare Execution Infrastructure responsibility boundary (summary-level) (responsible for execution orchestration, isolation setup, and failure containment only)
- [x] Declare Execution Infrastructure non-responsibility boundary (summary-level) (not responsible for component internal correctness, recovery strategy, or long-term guarantees)
- [x] Declare ClassLoader responsibility boundary (creation, isolation only) (responsible for creation and isolation; not responsible for leak-free or unload guarantees)
- [x] Declare ActionEngine responsibility boundary (execution control only) (responsible for invocation control, timeout, and cancellation semantics)

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
- Verify serialize wait behavior
- Verify Observation returned on execution failure
- ClassLoader Visibility Rules verification
- ClassLoader behavioral guarantees (leak / reload tests)
- Evidence-based ClassLoader isolation validation
- Collaborator / ClassLoader integration stress tests
- ClassLoader visibility verification (Scala stdlib isolation, bootstrap edge cases)
- ClassLoader behavioral guarantees validation (leak / reload / unload)
- Evidence-based verification for ClassLoader isolation (logs, negative access tests)

---

## Completion Check

Phase 3.1 is complete when:

- All ACTIVE items are marked DONE or DEFERRED
- No item remains ACTIVE or SUSPENDED
- Phase summary reflects closure accurately
