# Phase 55 GCF-09J: Collaborator Repository Bootstrap Projection

Date: 2026-08-04

GCF-09J moves runtime collaborator repository discovery to the Global typed
repository bootstrap policy. `textus.collaborator.repositories` accepts the
canonical spelling and three established decode-only aliases, with String-only,
comma-only path decoding. The policy supplies normalized, deduplicated paths
relative to bootstrap cwd to the runtime factory. Blank or absent input uses
`collaborator.d`; explicit nonexistent input stays explicit and discovers no
repository. The raw `ResolvedConfiguration` factory overload remains solely
for compatibility callers and is not used by runtime bootstrap.

This slice leaves `application-mode` as presentation-only vocabulary. Its
removal remains separate compatibility and CML work.

Initial implementation validation passed: `Test/compile` serial invocation
`77811-20260803T164422Z`; focused catalog/bootstrap/collaborator projection
validation passed 14/14 at `78392-20260803T164519Z`.

Independent review admitted three P2 specification/test-fixture findings:
exercise the actual `CncfRuntime` collaborator-factory seam, make comma-only
empty-element removal and post-normalization dedup executable, and keep
temporary fixtures beneath `target/` with cleanup. REVIEW_FIX adds those
checks. Its serialized `Test/compile` passed at `84174-20260803T165550Z` and
focused validation passed 14/14 at `84883-20260803T165706Z`. Focused
independent re-review is clean. The review also recorded HYG-P55-002 in the
Phase 55 hygiene ledger for the separate `CollaboratorRepository.scala`
naming cleanup.
