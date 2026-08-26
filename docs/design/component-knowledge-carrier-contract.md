# Component Knowledge Carrier Design

The carrier separates producer integrity evidence from later consumer behavior.
Cozy derives its declaration from the canonical source file after checking the
consumer contract's declared schema, Component identity, and logical release.
It hashes raw bytes and injects the generated declaration; an authored
`componentKnowledge` descriptor field is not a competing authority.

Archive and development flows preserve one raw logical resource:

- Source: `src/main/car/component-knowledge.json`
- Archive: `component-knowledge.json`
- Development evidence: `target/cncf.d/component-knowledge.json`

The prebuilt CAR admission boundary verifies a declared digest against the
named archive entry. A declaration is optional for compatibility, but when it
is present its schema, path, and digest are exact. The design introduces no
resolver, transport encoding, CML API, or CBD consumer behavior. A downstream
repository catalog may publish the same raw file at one declared,
version-scoped sidecar route after independently checking it against this
archive declaration; that transport is not part of the core carrier format.
