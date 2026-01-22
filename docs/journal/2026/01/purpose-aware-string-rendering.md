# Purpose-Aware String Rendering (Candidate)

Status: draft  
Target phase: Phase 2.8 (Infrastructure Hygiene)

## Motivation

Different execution contexts require different string representations:
- logs vs CLI vs debug output
- security-blinded vs full representation
- long-form vs embedded short form

Historically, this was handled ad-hoc (e.g. `toString`, util helpers).
This note proposes a structured vocabulary to make output intent explicit.

## Core Vocabulary (Proposed)

### DisplayIntent

An explicit enumeration of output intent.

- Print: natural representation, may be long, no security blinding
- Display: one-line interactive output, security blinding may apply
- Show: short debug-oriented output, security blinding may apply
- Embed(width): minimal representation for embedding with width constraint
- Literal: literal / serialization-oriented representation

This intent is passed explicitly to rendering logic.

### Printable

A marker interface for values that can render themselves
according to a DisplayIntent.

```
trait Printable {
  def print(intent: DisplayIntent): String
}
```

Printable is optional; values without Printable are rendered
by external renderers.

## Design Principles

- Output intent must be explicit (no overloaded toString usage)
- Rendering responsibility is separated from domain logic
- Security blinding is intent-driven, not caller-driven
- Suitable for AI-generated code (intent is explicit and checkable)

## Non-Goals (Phase 2.8)

- No runtime wiring yet
- No global renderer registry
- No replacement of all existing toString usage

This document serves as a design anchor for Phase 2.8.
