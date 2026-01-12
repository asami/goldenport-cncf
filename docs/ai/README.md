# AI Development Rules

This directory contains AI-specific operational rules and design notes.
They explain how AI agents must report verification/validation, handle
bootstrap-time constraints, and describe component discovery/initialization
without redefining core architecture.

The documents are split to keep governance rules separate from bootstrap
mechanics and component initialization details.

## Index

- ai-development-governance.md  
  Phase/step progress decisions, validation baselines, and recovery rules.
- ai-human-collaboration-convention.md  
  Human vs AI responsibility split, drift reporting, and bootstrap caveats.
- verification-validation.md  
  Terminology, reporting expectations, and Phase/Stage labeling guidance.
- bootstrap-logging.md  
  Why normal logging is unavailable during init and how BootstrapLog is used.
- component-initialization.md  
  Component discovery/initialization flow (Repository -> Factory/Provider -> initialize).
- codex-editing-rules.md  
  Codex-specific editing constraints for this repo.

These documents are mandatory for AI agents and must be read alongside
`docs/rules/` and `docs/design/` when relevant.
