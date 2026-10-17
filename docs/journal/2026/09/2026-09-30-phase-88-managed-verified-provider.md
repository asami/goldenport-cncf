# Explicit managed verified multi-guard provider

Date: 2026-09-30
Status: internal managed SQLite cross-owner route passed focused-319; runtime delivery open

Subsystem.withWorkflowVerifiedMultiGuardSpaceC is a separate explicit internal
path. It resolves the declaration's Workflow and EventStore requests inside the
same SystemNode lease, checks reference-identical managed resource identity,
then borrows a non-owning verified SQLite view. The ordinary/raw host path,
ordinary SqlDataStore views and internal same-owner candidate retain their
existing capability/ownership restrictions. No global SQL capability is enabled.

EntityVerifiedMultiGuardDataStore now retains the opaque host invocation into
the provider's native commit hook. The default hook preserves prior same-owner
fixtures. The managed hook cannot commit using a bare plan: it requires the
invocation to match its exact provider, live issuing-thread lease and declaration
policy. The shared native implementation checks authority before staging and
after the final pre-publication checkpoint, before native commit. Complete
post-images and snapshot readback use the existing admitted final seam.
The managed view uses the original borrowed resource and admission; it owns no
datasource, opens no second resource and provides no caller transaction callback.

Four new real host examples cover:
- Declared Workflow root/continuation and independently owned EventStore cursor
  updates plus event/index Creates; all five complete post-images are verified
  and recovered after Subsystem shutdown and reconstruction.
- Explicit stale resubmission returns NotMatched. It is not automatic replay.
- A stale cursor or second Create (index) collision rolls back earlier updates
  and Creates; reopening storage confirms no partial publication.
- Wrong owner and live cross-thread execution publish nothing, while the
  issuing thread can subsequently commit; missing grants reject before resource
  resolution. The escaped prepared adapter remains unusable after lease release.

The event/index payloads in these storage examples are fixture records. They
do not prove EventRecord codec integration, authoritative selected-only sequence
allocation, actual EventStore dispatch or application Workflow completion.
This is a callable internal managed storage route, not launcher/runtime activation.

Invocation SMWF-CNCF-MANAGED-VERIFIED-DIRECT-20260930-319 passed 173/173 tests
in 20 suites, zero failed/aborted/canceled/ignored/pending. SBT exit=0,
wrapper exit=0, lock=released; persistent summary confirms compile coverage.
The user-selected simplified parent execution route preserved serial locking,
fresh permission metadata and scoped platform review.

Log:
/tmp/skill.cncf.d/cncf-sbt-6d1cb57cd796939c69d9cc7267c6e9ac934a0e47c80d8700e3f31603c46cf742-f03ef6b03da4172d441b865063abf171/53744-20260930T065003Z.log

Validated SHA-256:
- EntityVerifiedMultiGuardDataStore.scala: 7a66e4aecc8da407e19d23ae6f7df3e148f6f59b6ed55a5b073cef3200d9734a
- SqlDataStore.scala: c1fe34937c1ea5e8a0739da540cde971c88aa0d05cee3109efc0077e9c80ff93
- Subsystem.scala: 26e8103722c9681840845e3d26a423afc412eccc396d248f5dbeacc71a3a9e13
- EventStorePersistenceHostSpec.scala: 1a1d908df636fa79f77dc43f82c796472a6699f4e821f463c5c89d52f1af686d

Next: compose EventStore-owned selection and canonical event/index records with
the guarded cursor through this managed route, then prove mixed-order delivery
only after authoritative verification and no delivery on rollback/uncertainty.
Managed cross-owner concurrent writers and managed revocation during staging
still need dedicated evidence; existing native same-owner and generic admission
specs are not relabeled as those proofs.
S12/P2-U15/P2-U16/P2-U05 remain open. No full-suite, independent review or typed
Phase/PLAN acceptance is claimed. No commit/publication was performed.
