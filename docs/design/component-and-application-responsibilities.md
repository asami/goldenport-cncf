Component and Application Responsibilities
==========================================

Purpose
-------
Clarify who configures, initializes, and uses:
- DataStore
- SelectableDataStore
- UnitOfWork
- (FUTURE) ServiceBus

This document is a "who does what" reference based on current implementation.


Roles Overview
--------------

| Role | Primary Responsibility |
|------|------------------------|
| Component Developer | Domain behavior implementation |
| Application Developer | Application assembly & configuration |
| CNCF Framework / Runtime | Runtime initialization & routing |


Component Developer Responsibilities (Current Implementation)
-------------------------------------------------------------

Implements:
- ActionCall / OperationCall
- Repository (domain-oriented)
- EntityStore (record ↔ entity mapping)

Uses:
- `executionContext.runtime.unitOfWork`
- DataStore / SelectableDataStore via protected helpers

Must NOT:
- instantiate DataStore
- select backend
- interpret Config
- manage commit / rollback

References:
- `src/main/scala/org/goldenport/cncf/action/OperationCallFeaturePart.scala`
- `src/main/scala/org/goldenport/cncf/repository/RepositorySupport.scala`
- `src/main/scala/org/goldenport/cncf/entity/EntityStore.scala`


Application Developer Responsibilities (Current Implementation)
---------------------------------------------------------------

Does:
- choose backend via config
  - `datastore.backend = "memory"` (currently supported)
- supply config values per environment
- trigger Component creation


Does NOT:
- initialize DataStore directly
- touch UnitOfWork directly

Rationale:
- Application-level configuration defines operational choices
  without leaking infrastructure concerns into Components.

References:
- `src/main/scala/org/goldenport/cncf/datastore/DataStackFactory.scala`
- `src/main/scala/org/goldenport/cncf/component/Component.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala`


CNCF Framework / Runtime Responsibilities (Current Implementation)
------------------------------------------------------------------

Does:
- interpret Config
- initialize the data-processing stack
- construct UnitOfWork
- bind UnitOfWork to RuntimeContext
- inject RuntimeContext into ExecutionContext
- act as the single authoritative place for interpreting datastore-related configuration

References:
- `src/main/scala/org/goldenport/cncf/datastore/DataStackFactory.scala`
- `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWork.scala`
- `src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala`


Current Data Stack Initialization Flow
--------------------------------------

1) Application provides Config  
2) Component creation triggers DataStackFactory  
3) DataStore / SelectableDataStore instantiated  
4) UnitOfWork constructed  
5) RuntimeContext created with UnitOfWork  
6) ExecutionContext created and passed to ActionCalls


Notes on Service Bus (FUTURE)
-----------------------------

- ServiceBus will follow the same responsibility split
- Config-driven backend selection
- Factory-based initialization
- Component-level usage only


Design Principles (Current Code)
--------------------------------

- Single routing boundary: UnitOfWork
- No lazy infrastructure initialization in Components
- No backend leakage into domain code
- Deterministic Component creation


Non-Goals
---------

- Component-driven backend selection
- Runtime reconfiguration
- Global service locators
- DI frameworks
