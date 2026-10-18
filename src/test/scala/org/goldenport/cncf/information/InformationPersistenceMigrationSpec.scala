package org.goldenport.cncf.information

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.information.entity.Information
import org.goldenport.cncf.information.value.{
  InformationBindingStatus,
  InformationConflict,
  InformationConflictState,
  InformationFieldEvent,
  InformationFieldState,
  InformationIdentityBinding,
  InformationLifecycleState,
  InformationPublicationState,
  InformationPublicationStatus,
  InformationResolutionCandidate,
  InformationValidationIssue
}
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, KnowledgeEntityBinding, KnowledgeNodeId, RdfNodeName}
import org.goldenport.record.Record
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 1, 2026
 *  version Sep. 17, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationPersistenceMigrationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Information persisted-record migration" should {
    "preview and decode the one supported legacy v0 shape without changing its input" in {
      Given("a complete legacy v0 Information record with one top-level updatedAt and all curation data")
      val legacy = _legacy_record()

      When("the repository previews and decodes the legacy record before generated decoding")
      val preview = InformationPersistenceMigration.preview(legacy)
      val decoded = _success(
        InformationEntityRepository.informationPersistent.fromStoreRecord(legacy)
      )

      Then("the preview is non-mutating, deterministic, and preserves curation data through canonical decoding")
      legacy shouldBe _legacy_record()
      preview match {
        case InformationPersistenceMigration.Preview.LegacyV0(canonical) =>
          canonical.getAny("revision").isDefined shouldBe true
          canonical.getAny("lifecycleAttributes").isDefined shouldBe true
          canonical.getAny("updatedAt") shouldBe None
        case other => fail(s"expected supported legacy v0 preview, got $other")
      }
      decoded shouldBe _information
    }

    "admit a supported legacy v0 record through the EntityStore repository read without rewriting storage" in {
      Given("one tracking DataStore seeded with a supported legacy v0 Information record")
      val store = new TrackingInMemoryDataStore
      given ExecutionContext = _context(store)
      val repository = new InformationEntityRepository(None)
      val collectionid = _success(repository.collectionIdC)
      val information = _information.copy(
        id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          _information.id.major,
          _information.id.minor,
          collectionid,
          _information.id.entropy.getOrElse(fail("Information fixture entropy is missing"))
        )
      )
      val collection = DataStore.CollectionId.EntityStore(information.id.collection)
      val entry = DataStore.EntryId(information.id)
      val legacy = _legacy_record(information)
      _success(store.create(collection, entry, legacy))
      val before = _success(store.load(collection, entry)).getOrElse(
        fail(s"legacy physical record is missing: ${information.id.print}")
      )
      val writesbefore = store.writeCount

      When("the Information repository loads the legacy record through EntityStore")
      val loaded = _success(repository.load(information.id))
      val after = _success(store.load(collection, entry)).getOrElse(
        fail(s"legacy physical record disappeared: ${information.id.print}")
      )

      Then("the exact Information is decoded after in-memory admission with no physical write")
      loaded shouldBe Some(information)
      after shouldBe before
      store.writeCount shouldBe writesbefore
    }

    "reopen the V0 SQL text wire read-only and adopt its canonical representation once" in {
      Given("a closed test-owned SQLite database containing the retained V0 physical text representation")
      val directory = java.nio.file.Files.createTempDirectory(
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/phase100/information-v0")), "sql-").toAbsolutePath
      val database = directory.resolve("legacy.db")
      val writer = org.goldenport.cncf.datastore.sql.SqlDataStore.sqlite(database.toString)
      val information = locally {
        given ExecutionContext = _context(writer)
        val collectionid = _success(new InformationEntityRepository(None).collectionIdC)
        val information = _information.copy(id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          _information.id.major, _information.id.minor, collectionid, _information.id.entropy.get))
        try _success(writer.create(DataStore.CollectionId.EntityStore(collectionid), DataStore.EntryId(information.id),
          _legacy_sql_record(information)))
        finally _success(writer.closeC())
        information
      }
      val before = java.nio.file.Files.readAllBytes(database).toVector

      When("a fresh Information repository admits the physical SQL Strings without rewriting them")
      val reader = org.goldenport.cncf.datastore.sql.SqlDataStore.sqlite(database.toString)
      given ExecutionContext = _context(reader)
      val repository = new InformationEntityRepository(None)
      val collection = DataStore.CollectionId.EntityStore(information.id.collection)
      val entry = DataStore.EntryId(information.id)
      try {
        val physical = _success(reader.load(collection, entry)).get
        val loaded = _success(repository.load(information.id)).get
        val after = _success(reader.load(collection, entry)).get

        Then("the legacy raw text and repeated bindings decode exactly and the physical row is unchanged")
        physical.getString("rawData") shouldBe Some("{\"title\":\"Legacy raw title\",\"source\":\"v0\"}")
        physical.getAny("identityBindings").get shouldBe a[String]
        loaded shouldBe information
        after shouldBe physical
        java.nio.file.Files.readAllBytes(database).toVector shouldBe before

        When("the explicit fixture-owned adoption is performed twice using the V0/canonical discriminator")
        var writes = 0
        def _adopt_(): Unit = {
          val current = _success(reader.load(collection, entry)).get
          InformationPersistenceMigration.preview(current) match {
            case InformationPersistenceMigration.Preview.LegacyV0(_) =>
              val decoded = _success(InformationEntityRepository.informationPersistent.fromStoreRecord(current))
              val canonical = _success(InformationEntityRepository.informationPersistent.toStoreRecordC(information.id.collection, decoded))
              _success(reader.save(collection, entry, canonical))
              writes += 1
            case InformationPersistenceMigration.Preview.Canonical(_) => ()
            case other => fail(s"unsupported fixture adoption: $other")
          }
        }
        _adopt_()
        val adopted = _success(reader.load(collection, entry)).get
        _adopt_()
        val retried = _success(reader.load(collection, entry)).get
        val canonical = _success(repository.load(information.id)).get

        Then("only one physical conversion occurs and retry preserves the full canonical Information")
        writes shouldBe 1
        InformationPersistenceMigration.preview(adopted) shouldBe a[InformationPersistenceMigration.Preview.Canonical]
        retried shouldBe adopted
        canonical shouldBe information
      } finally _success(reader.closeC())
    }

    "create a generated embedded-revision entity through the runtime collection bridge" in {
      Vector(false, true).foreach { normalized =>
        Given(s"the actual Information descriptor registered against an isolated SQLite provider, normalize=$normalized")
        val directory = java.nio.file.Files.createTempDirectory(
          java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/phase100/runtime-create")), "sql-").toAbsolutePath
        val provider = org.goldenport.cncf.datastore.sql.SqlDataStore.jdbc(s"jdbc:sqlite:${directory.resolve("runtime.db")}",
          config = org.goldenport.cncf.datastore.sql.SqlDataStore.Config(normalized))
        given ExecutionContext = _context(provider)
        val repository = new InformationEntityRepository(None)
        val collectionid = _success(repository.collectionIdC)
        val information = _information.copy(id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          _information.id.major, _information.id.minor, collectionid, "runtime_seed"))
        _success(repository.load(information.id)) // Registers the descriptor without creating a row.
        val runtime = summon[ExecutionContext].entitySpace.entityOption(collectionid).get
        val logical = _success(InformationEntityRepository.informationPersistent.toLogicalStoreRecord(information))
        val input = Record.createFull(logical.fields.filterNot(_.key == "revision").flatMap { field =>
          field.value.single match {
            case Some(value) => Some(field.key -> value)
            case None => None
            case value => Some(field.key -> value)
          }
        })
        try {
          When("the runtime bridge creates the generated entity and the repository reads native acknowledgement")
          _success(runtime.createRecordSynced(input))
          val loaded = _success(repository.load(information.id)).get

          Then("native create initializes revision while generated declared data survives")
          loaded.revision shouldBe EntityRevision.INITIAL
          loaded.rawData shouldBe information.rawData
          loaded.workingData shouldBe information.workingData
          loaded.identityBindings shouldBe information.identityBindings

          When("the registered repository performs a native managed update and readback under the same SQL key policy")
          val changed = loaded.copy(rawData = Record.data("normalized" -> normalized.toString))
          val updated = _success(repository.update(changed))
          val reloaded = _success(repository.load(information.id)).get

          Then("the existing revision owner advances once and provider aliases preserve the changed domain value")
          updated.revision.value shouldBe loaded.revision.value + 1L
          reloaded shouldBe updated
          reloaded.rawData shouldBe changed.rawData

          When("application input attempts to set the managed revision through the same bridge")
          val rejected = runtime.createRecordSynced(input.upsertSingle("revision", 9L))
          val retained = _success(repository.load(information.id)).get

          Then("the revision owner refuses the input and the already created entity remains unchanged")
          rejected shouldBe a[Consequence.Failure[?]]
          retained shouldBe reloaded
        } finally _success(provider.closeC())
      }
    }

    "preserve nonempty framework media writes as explicit physical SQL text" in {
      Given("a generated Information with all four existing framework media arrays populated")
      val base = _information
      val content = org.simplemodeling.model.value.SimpleObjectContent(base.nameAttributes, base.descriptiveAttributes,
        base.contentAttributes, base.lifecycleAttributes, base.publicationAttributes, base.securityAttributes,
        base.resourceAttributes, base.auditAttributes, base.mediaAttributes, base.contextualAttribute)
      val objectid = org.goldenport.datatype.ObjectId(org.goldenport.datatype.Identifier("media"))
      val image = org.simplemodeling.model.value.Image(objectid, content)
      val audio = org.simplemodeling.model.value.Audio(objectid, content)
      val video = org.simplemodeling.model.value.Video(objectid, content)
      val attachment = org.simplemodeling.model.value.Attachment(objectid, content)
      val information = base.copy(mediaAttributes = base.mediaAttributes.copy(images = Vector(image), audios = Vector(audio),
        videos = Vector(video), atathments = Vector(attachment)))
      val directory = java.nio.file.Files.createTempDirectory(
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/phase100/framework-media")), "sql-").toAbsolutePath
      val provider = org.goldenport.cncf.datastore.sql.SqlDataStore.sqlite(directory.resolve("media.db").toString)
      given ExecutionContext = _context(provider)
      try {
        When("the actual generated physical adapter writes the nonempty arrays through strict SQL")
        val physical = _success(InformationEntityRepository.informationPersistent.toStoreRecordC(information.id.collection, information))
        val collection = DataStore.CollectionId.EntityStore(information.id.collection)
        val entry = DataStore.EntryId(information.id)
        _success(provider.create(collection, entry, physical))
        val stored = _success(provider.load(collection, entry)).get

        Then("each explicitly owned media column retains its legacy external projection as JSON array text")
        val external = information.toRecord()
        Vector("images", "audios", "videos", "attachments").foreach { key =>
          val json = _success(org.goldenport.schema.XJson.fromJsonText(stored.getString(key).get))
          val legacy = external.getAny(key).get match {
            case values: Seq[?] => values.map {
              case record: Record => _success(org.goldenport.schema.XJson.fromJsonText(record.toJsonString))
              case _ => fail("Expected legacy framework media Record projection")
            }
            case _ => fail("Expected legacy framework media array projection")
          }
          legacy should have size 1
          json shouldBe io.circe.Json.fromValues(legacy)
        }
      } finally _success(provider.closeC())
    }

    "reject mixed revision and lifecycle evidence with one stable compatibility failure" in {
      Given("a legacy candidate that also declares a revision without canonical lifecycle evidence")
      val mixed = _legacy_record() ++ Record.dataAuto("revision" -> 7L)

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
      val collision = _legacy_record().upsertSingle(
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

    "retain complete canonical Information through EntityStore create and InformationSpace reload" in {
      Given("one canonical Information whose raw data, working data, and lifecycle state differ")
      given ExecutionContext = _context(DataStore.inMemorySearchable())
      val repository = new InformationEntityRepository(None)
      val collectionid = _success(repository.collectionIdC)
      val information = _information.copy(
        id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          _information.id.major,
          _information.id.minor,
          collectionid,
          _information.id.entropy.getOrElse(fail("Information migration fixture entropy is missing"))
        )
      )

      When("the repository creates it and a fresh InformationSpace reloads its EntityStore collection")
      val created = _success(repository.create(information))
      val reloaded = _success(new InformationSpace().snapshotC).information

      Then("the complete canonical Information is decoded instead of a sparse store projection")
      created.rawData shouldBe information.rawData
      created.workingData shouldBe information.workingData
      created.state shouldBe information.state
      reloaded should have size 1
      reloaded.head.rawData shouldBe information.rawData
      reloaded.head.workingData shouldBe information.workingData
      reloaded.head.state shouldBe information.state
    }

    "retain empty raw and working data through InformationSpace registration and reload" in {
      Given("an Information registration with empty raw and working data")
      given ExecutionContext = _context(DataStore.inMemorySearchable())
      val space = new InformationSpace()

      When("the Information is registered and a fresh InformationSpace reloads it")
      val registered = _success(space.registerInformation("book", Vector(Record.empty))).head
      val reloaded = _success(new InformationSpace().snapshotC).information

      Then("both required empty records remain present in the persisted representation")
      registered.rawData shouldBe Record.empty
      registered.workingData shouldBe Record.empty
      reloaded should have size 1
      reloaded.head.rawData shouldBe Record.empty
      reloaded.head.workingData shouldBe Record.empty
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
          Record(_legacy_record().fields.filterNot(_.key == "updatedAt")),
          "information-persistence-incompatible:legacy-v0-missing-updatedAt"
        ),
        (
          "legacy lifecycle with invalid updatedAt",
          _legacy_record().upsertSingle("updatedAt", "not-an-instant"),
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
        id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          _information.id.major,
          _information.id.minor,
          collectionid,
          _information.id.entropy.getOrElse(fail("Information incompatibility fixture entropy is missing"))
        )
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
  private val _information_id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
    major = "phase61",
    minor = "migration",
    collection = EntityCollectionId("phase61", "migration", "information"),entropy = "legacy_information_1")
  private val _binding = InformationIdentityBinding(
    rdfSubject = Some(RdfNodeName("urn:legacy:paper")),
    externalIdentifiers = Vector(ExternalKnowledgeIdentifier("openlibrary", "OL45883W", Some("work"))),
    entityBindings = Vector(KnowledgeEntityBinding("Paper", "legacy-paper-1", Some("v0"), Some("catalog"))),
    knowledgeNodeId = Some(KnowledgeNodeId("knowledge:legacy:paper")),
    authority = Some("openlibrary"),
    confidence = Some(0.91),
    status = InformationBindingStatus.candidate
  )
  private val _information = _success(
    Information.Builder()
      .withId(_information_id)
      .withRevision(EntityRevision.INITIAL)
      .withLifecycleAttributes(InformationLifecycleSupport.lifecycleAttributes(_updated_at))
      .withDomain("paper")
      .withRawData(Record.data("title" -> "Legacy raw title", "source" -> "v0"))
      .withWorkingData(Record.data("title" -> "Legacy working title", "subject" -> "migration"))
      .withState(InformationLifecycleState.needs_resolution)
      .withImportContext(Some(org.goldenport.cncf.information.value.InformationImportContext(
        "catalog",
        Some("legacy-source"),
        Some("https://example.test/legacy"),
        Some("import"),
        Some("job-1"),
        Some("saga-1"),
        Some("task-1"),
        Some(_updated_at),
        Some("legacy-user")
      )))
      .withValidationIssues(Vector(InformationValidationIssue("title", "warning", "legacy title needs review")))
      .withResolutionCandidates(Vector(InformationResolutionCandidate(
        "candidate-1",
        "title",
        "Legacy working title",
        _binding,
        Some(0.91),
        Some("legacy candidate"),
        selected = false
      )))
      .withIdentityBindings(Vector(_binding))
      .withPublicationStatuses(Vector(InformationPublicationStatus(
        "publication-1",
        InformationPublicationState.published,
        "knowledge",
        Some("published before migration"),
        None,
        Some(_updated_at)
      )))
      .withConflicts(Vector(InformationConflict(
        "conflict-1",
        "title",
        "Legacy working title",
        "Canonical title",
        "warning",
        InformationConflictState.open,
        None
      )))
      .withFieldEvents(Vector(InformationFieldEvent(
        "title",
        InformationFieldState.imported,
        "catalog",
        Some("import"),
        None,
        None,
        None,
        None,
        None,
        None,
        _updated_at,
        Some("legacy-user")
      )))
      .withConfirmedAt(Some(_updated_at))
      .buildC()
  )

  private def _legacy_binding_record: Record =
    Record(
      InformationEntityRepository.bindingStoreRecord(_binding).fields.filterNot(field =>
        Set("rdfSubject", "knowledgeNodeId", "status").contains(field.key)
      )
    ) ++ Record.dataAuto(
      "rdf_subject" -> "urn:legacy:paper",
      "knowledge_node_id" -> "knowledge:legacy:paper"
    )

  private def _legacy_record(information: Information = _information): Record = {
    val aliases = _legacy_binding_record
    val candidate = Record(
      information.resolutionCandidates.head.toDataStore().fields.filterNot(
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
      // The V0 fixture retains the historical physical writer, independently of
      // the current declared writer used by the canonical-record fixtures.
      information.toDataStore()
        .fields
        .filterNot(field => managed.contains(field.key) || Set("identityBindings", "resolutionCandidates").contains(field.key))
    ) ++ Record.dataAuto(
      "identityBindings" -> Vector(aliases),
      "resolutionCandidates" -> Vector(candidate),
      "updatedAt" -> _updated_at
    )
  }

  // Frozen legacy SQL transport for the V0 fixture. This is intentionally
  // independent of the new physical provider and declared application codec.
  private def _legacy_sql_record(information: Information): Record = {
    val legacy = _legacy_record(information)
    Record(legacy.fields.map { field =>
      val value = field.value.single match {
        case record: Record => record.toJsonString
        case values: Iterable[?] => io.circe.Json.fromValues(values.map {
          case record: Record => io.circe.parser.parse(record.toJsonString).fold(error => fail(error.message), identity)
          case value => io.circe.Json.fromString(org.goldenport.text.Presentable.print(value))
        }).noSpaces
        case value => value
      }
      org.goldenport.record.Field(field.key, org.goldenport.record.Field.Value.Single(value))
    })
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
