# Declared logical mapping: source-only adoption and recovery

Status: implementation and acceptance in progress.
Owner: CNCF Phase 100 DSM-100-01/04/05.
Normative dependency: [logical mapping R11](datastore-logical-type-mapping.md#r11-consumer-transition-and-recovery).

## Scope and authority

The user confirms on 2026-10-18 that the Workbench migration contains source only
and no existing Workbench DB. There is no production database target to rewrite
or original server to discover for this migration. Retained proposal inputs and
isolated test-owned stores are the recovery targets. No claim of original-data
recovery, historical workflow-control recovery or live cutover follows.

Canonical source ownership is Core logical types, SimpleModeler storage-mapping
projection, Cozy Phase 78 generation, CNCF physical providers/EntityStore, and
Workbench proposal handling. They retain their existing repository versions and
declared artifact dependency paths. A mutable SNAPSHOT generator refresh requires
actual regeneration: disable the existing sbt-cozy unchanged-generation cache
for that validation rather than editing generated products.

## Trusted declarations and inventory

An operator must select the exact database/collection and the producer/model and
mapping version from retained source or original provenance. JSON spelling and
SQL column metadata never establish a logical declaration. Record an exact target
inventory before applying a mapping. Classify each selected row as:

- Ready for metadata-only adoption: trusted compatible declaration; all physical
  values decode under it; no byte rewrite is necessary.
- Unavailable/incompatible provenance: do not guess or rewrite; require the owner
  to supply or explicitly resolve its declaration and any missing original input.
- Corrupt payload: retain the raw original and its declared parse/decode failure;
  do not admit a partial row or replace it with an empty/default value.

Unknown collections or schema/mapping versions are not implicitly adopted.
Read-only inspection calls the declared row decoder and reads physical rows;
it does not create or update them. Use a closed source DB for copying, retain
one unchanged original, and inspect a separate working copy.

## Cutover and recovery procedure

1. Retain the authoritative raw inputs and a closed copy of the test database.
2. Select the exact targets and their trusted versioned mapping. Run read-only
   classification. Refuse unresolved provenance or corruption before adoption.
3. For ready rows, adopt metadata without rewriting physical bytes. Load through
   EntityStore to verify the declared String/JSON/Record/product/array meanings.
4. An interrupted read-only inventory restarts from the same complete targets;
   no application checkpoint, hash protocol or extra lock is needed. Repeated
   classification must agree and leave the original database unchanged.
5. Close the working provider before restoring its disposable copy from the
   retained original. Reopen and repeat the same read-only and logical checks.

The executable rehearsal is `Phase100MappingRecoverySpec`. It interrupts and
repeats inspection, verifies unchanged physical bytes, injects corruption only
into its disposable working copy, restores that copy, and refuses untrusted
provenance. It neither mutates a real DB nor implements a general data rewriter.
No rewritten physical format is required by this source-only adoption. Future
physical rewrites require an explicitly versioned source/target transformation,
its own copy-based migration/resume/rollback evidence, and separate live-data
authority. That work must not be inferred from this procedure.

Prior String formatting lost through reserialization cannot be recovered from
the stored structured value alone. Only retained raw input can supply it.
Workbench's two retained proposal fixtures supply the original-text authority
for this migration; they do not prove recovery of an absent production DB.

## Durable physical text and generated consumer boundaries

Durable Job and its storage, owned-blob and lifetime quota entities declare
`canonicalJson` as String; Job also declares `jobId` as opaque String. Their
closed row mappings retain existing framework identity/audit/security/revision
columns. Their existing canonical JSON decoders remain application-owned after
String readback. Direct quota inventories consume those physical Strings and
never infer an application JSON value from them.

The actual generated consumer fixture is `src/test/cozy/phase100-store.cml`.
The existing version/descriptor/provenance-aware Cozy generation contract compiles
its outputs into the CNCF test classpath. `Phase100GeneratedStoreSpec` must verify
the generated declarations and ordinary EntityStore mutation/query/restart paths.
No copied or hand-patched Scala fixture establishes producer acceptance.

Information's repository forwards the generated declaration and logical hooks.
Its current physical writer therefore matches ordinary generated readers.
Framework-managed embedded revision remains a native Number; create omits it
before the existing revision owner initializes it. The independently declared
Information V0 admission shape (no revision, only updatedAt) retains its
non-writing legacy conversion before current logical decoding. Mixed lifecycle
evidence and alias collisions still use the original stable refusal. Legacy
fixtures use the historical toDataStore writer explicitly, rather than deriving
old bytes from the changing current writer. A grouped canonical lifecycle is
an explicitly retained repository-owned compatibility field.
