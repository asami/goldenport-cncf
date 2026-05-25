# Bootstrap Core + UX Profile Architecture

status=draft
updated_at=2026-05-25

# Purpose

The CNCF Web Engine provides a Web UI execution platform optimized for:

- long-term maintainability
- AI-assisted UI generation
- structural stability
- offline operation

To achieve this,
the UI implementation is divided into the following layers:

- Bootstrap Core
- Textus Widget
- UX Profile

This architecture separates:

- semantic UI structure
- visual appearance
- interaction behavior

into independently controllable layers.

# Architecture

```plantuml
@startuml

package "Application" {
  [View Model]
}

package "Textus Widget Layer" {
  [textus-query-table]
  [textus-form]
  [textus-command-panel]
}

package "Bootstrap Core" {
  [Semantic DOM]
  [Bootstrap Grid]
  [Bootstrap Form]
  [Bootstrap Table]
}

package "UX Profile" {
  [Bootstrap Profile]
  [Material Profile]
}

package "Runtime Enhancement" {
  [htmx]
  [Astro Island]
}

[View Model]
  --> [textus-query-table]

[textus-query-table]
  --> [Semantic DOM]

[Semantic DOM]
  --> [Bootstrap Grid]

[Semantic DOM]
  --> [Bootstrap Form]

[Semantic DOM]
  --> [Bootstrap Table]

[Bootstrap Profile]
  --> [Semantic DOM]

[Material Profile]
  --> [Semantic DOM]

[htmx]
  --> [Semantic DOM]

[Astro Island]
  --> [Semantic DOM]

@enduml
```

# Core Concepts

## Bootstrap Core

Bootstrap is not used merely as a CSS framework.

Instead,
it serves as a stable semantic DOM substrate for Web UI construction.

Bootstrap Core provides:

- grid structure
- form structure
- table structure
- navbar structure
- responsive baseline
- accessibility baseline

Bootstrap Core does not define application-specific visual design.

Its primary purpose is semantic structure stabilization.

# Textus Widget

Textus Widget represents semantic UI components with domain meaning.

Examples:

- textus-query-table
- textus-form
- textus-command-panel
- textus-job-monitor
- textus-aggregate-editor

Textus Widgets are transformed into Bootstrap semantic DOM structures.

```text
Textus Widget
  ->
Bootstrap Semantic DOM
  ->
UX Profile Rendering
```

# UX Profile

UX Profile defines the presentation layer and interaction layer.

A UX Profile controls:

- typography
- spacing
- elevation
- motion
- density
- color scheme
- interaction feedback

Initial profiles:

- Bootstrap Profile
- Material Profile

Future profiles:

- Compact Enterprise Profile
- Mobile Profile
- Kiosk Profile
- Accessibility High Contrast Profile

# Design Principles

## Semantic First

The UI is designed around semantic structure,
not visual implementation details.

Instead of treating a UI element as:

```text
table
```

it is treated as:

```text
query result table
```

## Harness-Oriented Architecture

The CNCF Web Engine prioritizes predictability and structural stability over unrestricted UI flexibility.

Objectives:

- AI-generated UI stabilization
- long-term maintainability
- reviewability
- offline compatibility
- component replaceability

## AI-Friendly Web UI DSL

Textus Widget functions as an AI-friendly Web UI DSL.

AI systems can focus on:

- semantic intent
- data binding
- action flow

rather than low-level CSS implementation details.

# Runtime Model

The default runtime architecture adopts a minimal JavaScript approach.

The platform is designed to remain:

- static-first
- SSR-friendly
- SSG-friendly
- offline-friendly

Dynamic enhancement mechanisms may include:

- htmx
- Astro Island
- partial hydration

# Future Direction

In the future,
Textus Widget Trees may be generated directly from CML / Cozy models.

```text
Domain Model
  ->
View Model
  ->
Textus Widget Tree
  ->
Bootstrap Semantic DOM
  ->
UX Profile
```

With this structure,
the CNCF Web Engine can function as a semantic Web UI runtime for the AI era.
