package org.goldenport.cncf.information

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, KnowledgeEntityBinding, KnowledgeNodeId, RdfNodeName}
import org.goldenport.record.Record
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationPersistenceMigrationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Information persisted-record migration" should {
    "preview and decode the one supported legacy v0 shape without changing its input" in {
      Given("a complete legacy v0 Information record with one top-level updatedAt and all curation data")
      val legacy = _legacy_record

      When("the repository previews and decodes the legacy record before generated decoding")
      val preview = InformationPersistenceMigration.preview(legacy)
      val decoded = _success(
        InformationEntityRepository.informationPersistent.fromStoreRecord(legacy)
      )

      Then("the preview is non-mutating, deterministic, and preserves curation data through canonical decoding")
      legacy shouldBe _legacy_record
      preview match {
        case InformationPersistenceMigration.Preview.LegacyV0(canonical) =>
          canonical.getAny("revision").isDefined shouldBe true
          canonical.getAny("lifecycleAttributes").isDefined shouldBe true
          canonical.getAny("updatedAt") shouldBe None
        case other => fail(s"expected supported legacy v0 preview, got $other")
      }
      decoded shouldBe _information
    }

    "reject mixed revision and lifecycle evidence with one stable compatibility failure" in {
      Given("a legacy candidate that also declares a revision without canonical lifecycle evidence")
      val mixed = _legacy_record ++ Record.dataAuto("revision" -> 7L)

      When("the repository previews and decodes the ambiguous persisted shape")
      val preview = InformationPersistenceMigration.preview(mixed)
      val decoded = InformationEntityRepository.informationPersistent.fromStoreRecord(mixed)

      Then("it fails before generated defaults can synthesize missing lifecycle provenance")
      preview shouldBe InformationPersistenceMigration.Preview.Incompatible(
        "information-persistence-incompatible:partial-or-mixed-revision-lifecycle"
      )
      decoded shouldBe a[Consequence.Failure[?]]
      decoded match {
        case Consequence.Failure(conclusion) =>
          conclusion.display shouldBe
            "information-persistence-incompatible:partial-or-mixed-revision-lifecycle"
        case other =>
          fail(s"expected stable compatibility failure, got $other")
      }
    }

    "reject nested legacy identity alias collisions with one stable compatibility failure" in {
      Given("a complete legacy v0 Information record whose identity binding provides both rdf_subject and rdfSubject")
      val collisionbinding = _legacy_binding_record ++ Record.dataAuto(
        "rdfSubject" -> "urn:canonical:paper"
      )
      val collision = _legacy_record.upsertSingle(
        "identityBindings",
        Vector(collisionbinding)
      )

      When("the repository previews and decodes the ambiguous nested binding")
      val preview = InformationPersistenceMigration.preview(collision)
      val decoded = InformationEntityRepository.informationPersistent.fromStoreRecord(collision)

      Then("it fails before generated decoding with the stable legacy identity-binding compatibility category")
      preview shouldBe InformationPersistenceMigration.Preview.Incompatible(
        "information-persistence-incompatible:legacy-v0-invalid-identity-binding"
      )
      decoded shouldBe a[Consequence.Failure[?]]
      decoded match {
        case Consequence.Failure(conclusion) =>
          conclusion.display shouldBe
            "information-persistence-incompatible:legacy-v0-invalid-identity-binding"
        case other =>
          fail(s"expected stable compatibility failure, got $other")
      }
    }

    "retain a direct generated canonical record without migration rewriting" in {
      Given("one current generated Information EntityStore representation")
      val canonical = InformationEntityRepository.informationPersistent.toStoreRecord(_information)

      When("the repository previews and decodes the current storage record")
      val preview = InformationPersistenceMigration.preview(canonical)
      val decoded = _success(
        InformationEntityRepository.informationPersistent.fromStoreRecord(canonical)
      )

      Then("the complete generated audit representation is canonical and all values remain equal")
      preview shouldBe InformationPersistenceMigration.Preview.Canonical(canonical)
      decoded shouldBe _information
    }

    "accept only complete grouped lifecycle evidence when revision is present" in {
      Given("one complete grouped lifecycle record and one otherwise identical incomplete grouped lifecycle record")
      val complete = _grouped_canonical_record
      val incomplete = Record.dataAuto(
        "revision" -> 1L,
        "lifecycleAttributes" -> Record.dataAuto("createdAt" -> _updated_at)
      )

      When("the repository previews and decodes both grouped lifecycle shapes")
      val completepreview = InformationPersistenceMigration.preview(complete)
      val completedecoded = _success(
        InformationEntityRepository.informationPersistent.fromStoreRecord(complete)
      )
      val incompletepreview = InformationPersistenceMigration.preview(incomplete)
      val incompletedecoded =
        InformationEntityRepository.informationPersistent.fromStoreRecord(incomplete)

      Then("only the structurally complete lifecycle group is canonical and decodable")
      completepreview shouldBe InformationPersistenceMigration.Preview.Canonical(complete)
      completedecoded shouldBe _information
      _assert_incompatible(
        incompletepreview,
        incompletedecoded,
        "information-persistence-incompatible:partial-or-mixed-revision-lifecycle"
      )
    }

    "reject every unsupported lifecycle evidence shape with its stable compatibility diagnostic" in {
      Given("the frozen incompatible lifecycle-shape matrix")
      val cases = Vector(
        (
          "grouped plus flattened lifecycle",
          _grouped_canonical_record ++ Record.dataAuto("updatedAt" -> _updated_at),
          "information-persistence-incompatible:conflicting-legacy-and-canonical-audit-evidence"
        ),
        (
          "mixed camel and physical lifecycle",
          InformationEntityRepository.informationPersistent.toStoreRecord(_information) ++
            Record.dataAuto("created_at" -> _updated_at),
          "information-persistence-incompatible:partial-or-mixed-revision-lifecycle"
        ),
        (
          "legacy lifecycle without updatedAt",
          Record(_legacy_record.fields.filterNot(_.key == "updatedAt")),
          "information-persistence-incompatible:legacy-v0-missing-updatedAt"
        ),
        (
          "legacy lifecycle with invalid updatedAt",
          _legacy_record.upsertSingle("updatedAt", "not-an-instant"),
          "information-persistence-incompatible:legacy-v0-missing-or-invalid-updatedAt"
        )
      )

      When("the repository previews and decodes every unsupported shape")
      val outcomes = cases.map { case (name, record, diagnostic) =>
        (
          name,
          InformationPersistenceMigration.preview(record),
          InformationEntityRepository.informationPersistent.fromStoreRecord(record),
          diagnostic
        )
      }

      Then("each shape fails before generated decoding with its exact compatibility diagnostic")
      outcomes.foreach { case (_, preview, decoded, diagnostic) =>
        _assert_incompatible(preview, decoded, diagnostic)
      }
    }

    "preserve an incompatible physical record and its write count on repository load failure" in {
      Given("one tracking DataStore seeded with an incompatible physical Information record")
      val store = new TrackingInMemoryDataStore
      given ExecutionContext = _context(store)
      val repository = new InformationEntityRepository(None)
      val collectionid = _success(repository.collectionIdC)
      val information = _information.copy(
        id = _information.id.copy(collection = collectionid)
      )
      val collection = DataStore.CollectionId.EntityStore(information.id.collection)
      val entry = DataStore.EntryId(information.id)
      val incompatible = Record(
        _physical_store_record(information).fields.filterNot(_.key == "updated_at")
      )
      _success(store.create(collection, entry, incompatible))
      val before = _success(store.load(collection, entry)).getOrElse(
        fail(s"incompatible physical record is missing: ${information.id.print}")
      )
      val writesbefore = store.writeCount

      When("the existing InformationEntityRepository loads the persisted incompatible record")
      val loaded = repository.load(information.id)
      val after = _success(store.load(collection, entry)).getOrElse(
        fail(s"incompatible physical record disappeared: ${information.id.print}")
      )

      Then("the read fails with the migration diagnostic without rewriting the physical record")
      _assert_failure(
        loaded,
        "information-persistence-incompatible:partial-or-mixed-revision-lifecycle"
      )
      after shouldBe before
      store.writeCount shouldBe writesbefore
    }
  }

  private val _updated_at = Instant.parse("2026-08-29T03:04:05Z")
  private val _information_id = EntityId(
    major = "phase61",
    minor = "migration",
    collection = EntityCollectionId("phase61", "migration", "information"),
    timestamp = Some(_updated_at),
    entropy = Some("legacy_information_1")
  )
  private val _binding = InformationIdentityBinding(
    rdfSubject = Some(RdfNodeName("urn:legacy:paper")),
    externalIdentifiers = Vector(ExternalKnowledgeIdentifier("openlibrary", "OL45883W", Some("work"))),
    entityBindings = Vector(KnowledgeEntityBinding("Paper", "legacy-paper-1", Some("v0"), Some("catalog"))),
    knowledgeNodeId = Some(KnowledgeNodeId("knowledge:legacy:paper")),
    authority = Some("openlibrary"),
    confidence = Some(0.91)
  )
  private val _information = Information(
    id = _information_id,
    domain = "paper",
    rawData = Record.data("title" -> "Legacy raw title", "source" -> "v0"),
    workingData = Record.data("title" -> "Legacy working title", "subject" -> "migration"),
    state = InformationLifecycleState.needsResolution,
    importContext = Some(org.goldenport.cncf.information.value.InformationImportContext(
      "catalog",
      Some("legacy-source"),
      Some("https://example.test/legacy"),
      Some("import"),
      Some("job-1"),
      Some("saga-1"),
      Some("task-1"),
      Some(_updated_at),
      Some("legacy-user")
    )),
    validationIssues = Vector(InformationValidationIssue("title", "warning", "legacy title needs review")),
    resolutionCandidates = Vector(InformationResolutionCandidate(
      "candidate-1",
      "title",
      "Legacy working title",
      _binding,
      Some(0.91),
      Some("legacy candidate"),
      selected = false
    )),
    identityBindings = Vector(_binding),
    publicationStatuses = Vector(InformationPublicationStatus(
      "publication-1",
      InformationPublicationState.published,
      "knowledge",
      Some("published before migration"),
      None,
      Some(_updated_at)
    )),
    conflicts = Vector(InformationConflict(
      "conflict-1",
      "title",
      "Legacy working title",
      "Canonical title"
    )),
    fieldEvents = Vector(InformationFieldEvent(
      "title",
      InformationFieldState.imported,
      "catalog",
      operation = Some("import"),
      occurredAt = _updated_at,
      actor = Some("legacy-user")
    )),
    confirmedAt = Some(_updated_at),
    updatedAt = _updated_at
  )

  private def _legacy_binding_record: Record =
    Record(
      _binding.toDataStore().fields.filterNot(field =>
        Set("rdfSubject", "knowledgeNodeId", "status").contains(field.key)
      )
    ) ++ Record.dataAuto(
      "rdf_subject" -> "urn:legacy:paper",
      "knowledge_node_id" -> "knowledge:legacy:paper"
    )

  private def _legacy_record: Record = {
    val aliases = _legacy_binding_record
    val candidate = Record(
      _information.resolutionCandidates.head.toDataStore().fields.filterNot(
        _.key == "binding"
      )
    ) ++ Record.dataAuto("binding" -> aliases)
    val managed = Set(
      "revision",
      "createdAt",
      "updatedAt",
      "createdBy",
      "updatedBy",
      "postStatus",
      "aliveness"
    )
    Record(
      InformationEntityRepository.informationPersistent
        .toStoreRecord(_information)
        .fields
        .filterNot(field => managed.contains(field.key))
    ) ++ Record.dataAuto(
      "identityBindings" -> Vector(aliases),
      "resolutionCandidates" -> Vector(candidate),
      "updatedAt" -> _updated_at
    )
  }

  private def _grouped_canonical_record: Record =
    Record(
      InformationEntityRepository.informationPersistent
        .toStoreRecord(_information)
        .fields
        .filterNot(field => _lifecycle_keys.contains(field.key))
    ) ++ Record.dataAuto(
      "lifecycleAttributes" -> _grouped_lifecycle_record
    )

  private def _grouped_lifecycle_record: Record =
    Record.dataAuto(
      "createdAt" -> _information.lifecycleAttributes.createdAt,
      "updatedAt" -> _information.lifecycleAttributes.updatedAt,
      "createdBy" -> _information.lifecycleAttributes.createdBy,
      "updatedBy" -> _information.lifecycleAttributes.updatedBy,
      "postStatus" -> _information.lifecycleAttributes.postStatus,
      "aliveness" -> _information.lifecycleAttributes.aliveness
    )

  private def _physical_store_record(information: Information): Record = {
    val record = InformationEntityRepository.informationPersistent.toStoreRecord(information)
    Record(record.fields.filterNot(field => _lifecycle_keys.contains(field.key))) ++
      Record.dataAuto(
        "created_at" -> information.lifecycleAttributes.createdAt,
        "updated_at" -> information.lifecycleAttributes.updatedAt,
        "created_by" -> information.lifecycleAttributes.createdBy,
        "updated_by" -> information.lifecycleAttributes.updatedBy,
        "post_status" -> information.lifecycleAttributes.postStatus,
        "aliveness" -> information.lifecycleAttributes.aliveness
      )
  }

  private def _context(store: DataStore): ExecutionContext = {
    val clock = Clock.fixed(_updated_at, ZoneOffset.UTC)
    val context = ExecutionContext.create(clock)
    context.dataStoreSpace.useDataStore(store)
    context
  }

  private def _assert_incompatible(
    preview: InformationPersistenceMigration.Preview,
    decoded: Consequence[?],
    diagnostic: String
  ): Unit = {
    preview shouldBe InformationPersistenceMigration.Preview.Incompatible(diagnostic)
    _assert_failure(decoded, diagnostic)
  }

  private def _assert_failure(
    result: Consequence[?],
    diagnostic: String
  ): Unit =
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.display shouldBe diagnostic
      case other =>
        fail(s"expected stable compatibility failure, got $other")
    }

  private val _lifecycle_keys = Set(
    "createdAt",
    "updatedAt",
    "createdBy",
    "updatedBy",
    "postStatus",
    "aliveness"
  )

  private final class TrackingInMemoryDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop) {
    private var _write_count = 0

    def writeCount: Int =
      _write_count

    override def create(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      _write_count += 1
      super.create(collection, id, record)
    }

    override def save(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      _write_count += 1
      super.save(collection, id, record)
    }

    override def update(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      changes: Record
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      _write_count += 1
      super.update(collection, id, changes)
    }

    override def delete(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      _write_count += 1
      super.delete(collection, id)
    }
  }

  private def _success[A](consequence: Consequence[A]): A =
    consequence.toOption.getOrElse(fail(s"expected success, got $consequence"))
}
