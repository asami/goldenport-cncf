# Component Discovery from Classes Directories (Design Note)

status: draft
scope: Stage 5 demo → Stage 6 development / hot-reload ready

---

## 1. Purpose

This note describes a development-time Component discovery mechanism that loads CNCF Components directly from compiled classes directories, such as:

- scala-cli workspace output (.scala-build/**/classes)
- sbt output (target/scala-*/classes)

The primary goals are:

- Enable Stage 5 demos where newly written Components run immediately
- Avoid jar packaging and component.dir at this stage
- Keep the design extensible toward hot reloading and Stage 6 mechanisms

This mechanism is explicitly opt-in and not intended for production.

---

## 2. Non-Goals

- No production-grade plugin system
- No guaranteed compatibility with all build tools
- No hot reloading implementation in Stage 5
- No changes to Component execution semantics

---

## 3. High-Level Design

Introduce a Component Discovery abstraction that can discover Components from different sources.

ComponentDiscoverer
  └─ DirectoryClassesDiscoverer
       ├─ scala-cli workspace adapter
       └─ sbt target adapter

Discovery returns registrable Components with metadata sufficient for future hot reload.

---

## 4. Core Abstractions

### 4.1 ComponentDiscoverer

- discover(): Seq[DiscoveredComponent]

Responsible only for discovery, not registration policy.

### 4.2 DiscoveredComponent

- id: ComponentId (or derived name)
- instance: Component
- origin: ComponentOrigin
- fingerprint: ComponentFingerprint

### 4.3 ComponentOrigin

- kind: DirectoryClasses | Jar | Other
- roots: Seq[Path]
- loaderHint: LoaderHint (future use)

Stage 5 uses DirectoryClasses only.

### 4.4 ComponentFingerprint

- entries: Seq[(relativePath, lastModifiedMillis, size)]

Collected from .class files under origin roots.

---

## 5. Directory-Based Discovery (Core Mechanism)

### 5.1 DirectoryClassesDiscoverer

DirectoryClassesDiscoverer(classesDirs: Seq[Path], parentClassLoader: ClassLoader)

Responsibilities:
- Create a dedicated URLClassLoader
- Discover Component implementations
- Instantiate Components
- Attach origin and fingerprint metadata

### 5.2 Class Discovery Strategy

Two-step strategy:
1) ServiceLoader (preferred)
2) Fallback: classpath scanning (restricted by package prefix)

---

## 6. Build Tool Adapters

Adapters only determine which directories to scan.

### 6.1 scala-cli

Use --workspace to fix output location.

Expected structure:
<workspace>/
  .scala-build/
    **/classes/

Adapter:
ScalaCliWorkspaceClassesDirs(workspace: Path): Seq[Path]

### 6.2 sbt

Standard locations:
- target/scala-*/classes
- (optional) target/scala-*/test-classes

Adapter:
SbtTargetClassesDirs(projectRoot: Path): Seq[Path]

---

## 7. ClassLoader Policy (Hot-Reload Ready)

Assumptions:
- New ClassLoader per discovery cycle
- Old loaders become unreachable on replacement
- Registry supports replacement semantics later

---

## 8. Registry Integration

Opt-in discovery guarded by flags.

Examples:
- CLI: cncf command --discover=classes hello world greeting
- Env: CNCF_DISCOVER_CLASSES_DIRS=1

Registry API:
- register(discovered)
- replace(id, discovered)   (future)
- list()

Stage 5 uses register only.

---

## 9. Stage Mapping

Stage 5:
- scala-cli
- classes directory discovery
- no hot reload
- explicit opt-in

Stage 6+:
- jar discovery
- component.dir
- ServiceLoader-first
- optional hot reload via fingerprint watching

---

## 10. Rationale

- Keeps DSL and execution model unchanged
- Avoids premature jar commitment
- Enables write → run demos
- Preserves a clean path to hot reload

---

## 11. Summary

A minimal, explicit, and extensible discovery mechanism for development-time Components, suitable for Stage 5 demos and ready for Stage 6 evolution.
