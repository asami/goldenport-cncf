# Information Canonical Model Design

`src/main/cozy/information.cml` is the sole Information domain-model source.
It keeps the Information entity, its value and powertype families, and the
`informationLifecycle` state machine in one executable vocabulary. The CML
defines the model boundary; generated Scala is its runtime realization.

The runtime model is generated under the canonical packages. The root entity
is `org.goldenport.cncf.information.entity.Information`. Its generated
`value.*` types contain the declared Information values and powertypes, while
the generated `domain.statemachine.*` types contain the lifecycle descriptors.
These generated types, including their generated record codecs, are the model
used by the Information runtime.

`InformationSpace` is the component-owned workbench that orchestrates
Information lifecycle operations and persistence. It caches and coordinates
the generated Information entity; it is not a competing domain model.

`InformationSupport.scala` is support code outside the CML. It contains
non-CML helpers, domain extraction support, lifecycle construction support, and
capabilities. It does not define a root Information runtime type.

The persisted-record boundary has one deliberately narrow migration seam.
`InformationPersistenceMigration` admits the supported historical persisted
record shape before generated decoding and canonicalizes it in memory. That
seam belongs to storage migration and does not create a second public
Information transport model or compatibility promise.

The canonical model is evidenced by `InformationCmlCanonicalContractSpec`,
`InformationCanonicalRuntimeReferenceSpec`,
`GeneratedInformationRuntimeAdoptionSpec`, and
`InformationPersistenceMigrationSpec`.
