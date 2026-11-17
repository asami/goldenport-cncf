package org.goldenport.cncf.cli

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.testutil.CarArchiveFixture
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, ResolvedParameters, RuntimeConfig}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, ComponentRecordStorage, ComponentRecordStorageDeclaration, DataStore, Query, QueryDirective, SearchableDataStore}
import org.goldenport.cncf.event.{EventPersistencePolicy, EventStorePersistenceDeclaration, EventTypeId}
import org.goldenport.cncf.subsystem.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
final class StartupManagedDataImportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _owner = ComponentId("org.goldenport.cncf.Specification")
  private val _context = ExecutionContext.create()
  private val _records = _collection("evidence")
  private val _sentinel_id = DataStore.StringEntryId("prior-row")
  private val _import_id = DataStore.StringEntryId("imported-row")
  private val _prefix = "textus.component.org.goldenport.cncf.specification.datastores"
  private val _events_prefix = "textus.component.org.goldenport.cncf.event-store.datastores.events"
  private val _storage_key = CncfConfigurationParameterCatalog.SUBSYSTEM_STORAGE_PLAN_KEY
  private val _persistence = _take(EventStorePersistenceDeclaration.createC("events",
    _collection("cursor"), _collection("events"), _collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(_owner, "workflow",
        _collection("instances"), _collection("continuations")))))
  private val _policy = _take(EventPersistencePolicy.createC("startup-import-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val _grant = ComponentRecordStorageDeclaration.CreateGrant(_owner, "workflow", _records)
  private val _e1 = afterWord("in spec:startup-managed-data-import, example:E1, rules:R1, phase:DIAGNOSE")
  private val _e2 = afterWord("in spec:startup-managed-data-import, example:E2, rules:R2, phase:DIAGNOSE")
  private val _e3 = afterWord("in spec:startup-managed-data-import, example:E3, rules:R3, phase:DIAGNOSE")
  private val _e4 = afterWord("in spec:startup-managed-data-import, example:E4, rules:R4, phase:DIAGNOSE")

  "Startup managed data import" should {
    "E1 preserve the managed owner's selected destination" must _e1 {
      "retain prior Workflow rows and expose imported evidence through ordinary managed binding after reopen" in {
        Given("a controlled runtime, a host Workflow and record grant, and only packaged CAR datastore defaults")
        _with_fixture() { (root, arguments) =>
          _write_host(root)
          _with_runtime(root, arguments) { subsystem =>
            _take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner, _environment(subsystem))(
              _ => Consequence.unit))
            _create_prior_row(subsystem, "workflow", _records)
            subsystem.systemNode.managedDataStoreCount shouldBe 1
            subsystem.systemNode.activeLeaseCount shouldBe 0
          }
          _write_seed(root, Vector(_records -> "synthetic-evidence"))

          When("normal startup imports into the already selected managed Workflow destination")
          _with_runtime(root, arguments) { subsystem =>
            Then("the previous row and imported values are visible through fresh managed record bindings")
            _expect_rows(subsystem, "workflow", _records, "synthetic-evidence")
            subsystem.systemNode.managedDataStoreCount shouldBe 1
            subsystem.systemNode.activeLeaseCount shouldBe 0
            _database_files(root) shouldBe Set(_default_database(root))
          }

          And("an owned runtime reopens without replaying the seed")
          Files.delete(root.resolve("seed.yaml"))
          _remove_seed_selection(root)
          _with_runtime(root, arguments) { reopened =>
            Then("both rows remain durable and no alternate database or lease remains")
            _expect_rows(reopened, "workflow", _records, "synthetic-evidence")
            reopened.systemNode.managedDataStoreCount shouldBe 1
            reopened.systemNode.activeLeaseCount shouldBe 0
            _database_files(root) shouldBe Set(_default_database(root))
          }
        }
      }

      "prefer an explicit component and named-store override to packaged global defaults" in {
        Given("a host-selected component workflow store that differs from the CAR global destination")
        _with_fixture() { (root, arguments) =>
          val dedicated = root.resolve("dedicated.sqlite")
          _write_host(root, Map(s"${_prefix}.workflow.kind" -> "sqlite",
            s"${_prefix}.workflow.path" -> dedicated.toString,
            s"${_events_prefix}.kind" -> "sqlite", s"${_events_prefix}.path" -> dedicated.toString))
          _retained_import(root, arguments, dedicated, "component-override")
          Files.exists(_default_database(root)) shouldBe false
        }
      }

      "preserve explicit global overrides ahead of CAR defaults in each existing spelling" in {
        Vector("textus", "cncf").foreach { spelling =>
          Given(s"a host's $spelling datastore override and CAR defaults using the same spelling")
          _with_fixture(root => _car_defaults(root).map { case (key, value) =>
            key.replace("textus.", s"$spelling.") -> value
          }) { (root, arguments) =>
            val explicit = root.resolve("explicit.sqlite")
            _write_host(root, Map(s"$spelling.datastore.kind" -> "sqlite", s"$spelling.datastore.path" -> explicit.toString))
            _retained_import(root, arguments, explicit, s"$spelling-override")
            Files.exists(_default_database(root)) shouldBe false
          }
        }
      }

      "use the managed resolver's existing relative path meaning with a distinct bootstrap base directory" in {
        Given("a CAR SQLite path relative to the process directory, with the runtime root below target")
        _with_fixture(root => Map("textus.datastore.kind" -> "sqlite",
          "textus.datastore.path" -> Path.of("").toAbsolutePath.normalize
            .relativize(root.resolve("relative.sqlite")).toString)) { (root, arguments) =>
          val relative = Path.of("").toAbsolutePath.normalize.relativize(root.resolve("relative.sqlite"))
          _write_host(root)
          _retained_import(root, arguments, root.resolve("relative.sqlite"), "relative-destination")
          Then("startup shares the managed destination rather than rebasing SQLite beneath the bootstrap root")
          Files.exists(root.resolve(relative)) shouldBe false
        }
      }
    }

    "E2 keep independently declared stores and standalone collections separate" must _e2 {
      "import each captured collection only into its own named Component store" in {
        Given("two captured record collections with independent workflow and archive destinations")
        _with_fixture() { (root, arguments) =>
          val archive = _collection("archive")
          val workflowfile = root.resolve("dedicated-workflow.sqlite")
          val archivefile = root.resolve("archive.sqlite")
          _write_host(root, Map(
            s"${_prefix}.workflow.kind" -> "sqlite", s"${_prefix}.workflow.path" -> workflowfile.toString,
            s"${_events_prefix}.kind" -> "sqlite", s"${_events_prefix}.path" -> workflowfile.toString,
            s"${_prefix}.archive.kind" -> "sqlite", s"${_prefix}.archive.path" -> archivefile.toString),
            _plan_text(Vector(_grant, ComponentRecordStorageDeclaration.CreateGrant(_owner, "archive", archive))))
          _with_runtime(root, arguments) { subsystem =>
            _create_prior_row(subsystem, "workflow", _records)
            _create_prior_row(subsystem, "archive", archive)
          }
          _write_seed(root, Vector(_records -> "workflow-evidence", archive -> "archive-evidence"))

          When("normal startup imports both declared collections")
          _with_runtime(root, arguments) { subsystem =>
            Then("each store retains its earlier row and receives its own evidence")
            _expect_rows(subsystem, "workflow", _records, "workflow-evidence")
            _expect_rows(subsystem, "archive", archive, "archive-evidence")
            _load_from_store(subsystem, "workflow", archive, _import_id) shouldBe None
            _load_from_store(subsystem, "archive", _records, _import_id) shouldBe None
            subsystem.systemNode.managedDataStoreCount shouldBe 2
            subsystem.systemNode.activeLeaseCount shouldBe 0
            _database_files(root) shouldBe Set(workflowfile, archivefile)
          }

          And("both managed destinations reopen after the seed is removed")
          Files.delete(root.resolve("seed.yaml"))
          _remove_seed_selection(root)
          _with_runtime(root, arguments) { subsystem =>
            _expect_rows(subsystem, "workflow", _records, "workflow-evidence")
            _expect_rows(subsystem, "archive", archive, "archive-evidence")
            subsystem.systemNode.managedDataStoreCount shouldBe 2
            subsystem.systemNode.activeLeaseCount shouldBe 0
          }
        }
      }

      "retain the runtime datastore fallback for a collection without a captured grant" in {
        Given("a declared managed evidence collection and an unrelated standalone startup collection")
        _with_fixture() { (root, arguments) =>
          val standalone = DataStore.CollectionId("standalone_evidence")
          _write_host(root)
          val baseline = _with_runtime(root, arguments) { subsystem =>
            val count = subsystem.systemNode.managedDataStoreCount
            val files = _database_files(root)
            count shouldBe 1
            files shouldBe Set(_default_database(root))
            (count, files)
          }
          _write_seed(root, Vector(standalone -> "legacy-fallback"))

          When("normal startup imports the unmatched collection")
          _with_runtime(root, arguments) { subsystem =>
            given ExecutionContext = _context
            val loaded = _take(subsystem.globalRuntimeContext.dataStoreSpace.dataStore(standalone)
              .flatMap(_.load(standalone, _import_id)))
            Then("the original runtime store owns the row without adding to the prepared host storage")
            loaded.flatMap(_.getString("value")) shouldBe Some("legacy-fallback")
            loaded.flatMap(_.getString("id")) shouldBe Some(_import_id.print)
            subsystem.systemNode.managedDataStoreCount shouldBe baseline._1
            subsystem.systemNode.activeLeaseCount shouldBe 0
            _database_files(root) shouldBe baseline._2
          }
        }
      }
    }

    "E3 enforce captured admission and managed lifetime before provider access" must _e3 {
      "reject a reserved collection in the host plan before constructing Components or opening a database" in {
        Given("an otherwise valid host plan whose record grant was changed to the Workflow root collection")
        _with_fixture() { (root, arguments) =>
          val valid = io.circe.parser.parse(_plan_text()).toOption.get
          val conflicting = valid.mapObject(_.add("records", Json.arr(Json.obj(
            "componentId" -> Json.fromString(_owner.name), "storeName" -> Json.fromString("workflow"),
            "collection" -> Json.fromString(_persistence.workflowGrants.head.rootCollection match {
              case DataStore.CollectionId.EntityStore(identity) => identity.print
              case _ => fail("Workflow root requires a canonical entity collection")
            })))))
          _write_host(root, plantext = conflicting.noSpaces)
          val runtime = new CncfRuntime()
          var constructed = false
          val before = (SystemNodeConstruction.current, EventStoreConstruction.current,
            EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current)
          try {
            When("the public embedding entry admits the selected host storage plan")
            val result = runtime.initializeForEmbedding(root, arguments, extraComponents = _ => {
              constructed = true
              Vector.empty
            })
            try {
              Then("admission fails without a Component, unauthorized resource, or leaked construction scope")
              result shouldBe a[Consequence.Failure[?]]
              constructed shouldBe false
              _database_files(root) shouldBe empty
              (SystemNodeConstruction.current, EventStoreConstruction.current,
                EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current) shouldBe before
            } finally result.toOption.foreach(Subsystem.shutdownOwned)
          } finally runtime.closeEmbedding()
        }
      }

      "refuse absent declarations and undeclared owner or store scopes without resolving a provider" in {
        Given("one host with no record declaration and one host with one exact record scope")
        _with_fixture() { (root, arguments) =>
          _write_host(root, plantext = _plan_text(Vector.empty))
          _with_runtime(root, arguments) { subsystem =>
            val baseline = subsystem.systemNode.managedDataStoreCount
            baseline shouldBe 1
            _database_files(root) shouldBe Set(_default_database(root))
            When("an ordinary managed lease attempts a record bind without a captured declaration")
            val denied = subsystem._with_managed_datastore_lease_c(_ => _bind(subsystem, "workflow", _records, _import_id))
            Then("datastore defaults cannot manufacture record admission")
            denied shouldBe a[Consequence.Failure[?]]
            subsystem.systemNode.managedDataStoreCount shouldBe baseline
            subsystem.systemNode.activeLeaseCount shouldBe 0
            _database_files(root) shouldBe Set(_default_database(root))
          }
          _write_host(root)
          _with_runtime(root, arguments) { subsystem =>
            val baseline = subsystem.systemNode.managedDataStoreCount
            baseline shouldBe 1
            _database_files(root) shouldBe Set(_default_database(root))
            When("a managed lease requests a foreign owner, store or collection")
            val denied = _take(subsystem._with_managed_datastore_lease_c { _ => Consequence.success(Vector(
              subsystem.bindComponentRecordStorageC(ComponentId("org.example.Other"), "workflow", _records, _import_id, _context),
              _bind(subsystem, "archive", _records, _import_id),
              _bind(subsystem, "workflow", _collection("ungranted"), _import_id))) })
            Then("all requests fail without adding a resource to the prepared host storage")
            denied.foreach(_ shouldBe a[Consequence.Failure[?]])
            subsystem.systemNode.managedDataStoreCount shouldBe baseline
            subsystem.systemNode.activeLeaseCount shouldBe 0
            _database_files(root) shouldBe Set(_default_database(root))
          }
        }
      }

      "refuse bindings outside dispatch and revoke a capability when its issuing lease and runtime end" in {
        Given("a captured record grant in a running controlled runtime")
        _with_fixture() { (root, arguments) =>
          _write_host(root)
          _with_runtime(root, arguments) { subsystem =>
            val baseline = subsystem.systemNode.managedDataStoreCount
            baseline shouldBe 1
            _database_files(root) shouldBe Set(_default_database(root))
            When("record binding is requested outside an active managed lease")
            val denied = _bind(subsystem, "workflow", _records, _import_id)
            Then("binding cannot add a resource or open a replacement lease")
            denied shouldBe a[Consequence.Failure[?]]
            subsystem.systemNode.managedDataStoreCount shouldBe baseline
            subsystem.systemNode.activeLeaseCount shouldBe 0
            _database_files(root) shouldBe Set(_default_database(root))

            When("an ordinary lease returns its capability and then ends")
            val escaped = _take(subsystem._with_managed_datastore_lease_c(_ =>
              _bind(subsystem, "workflow", _records, _import_id)))
            Then("the escaped capability is unusable and no lease remains active")
            escaped.loadC() shouldBe a[Consequence.Failure[?]]
            subsystem.systemNode.activeLeaseCount shouldBe 0
            subsystem.systemNode.managedDataStoreCount shouldBe baseline
            _database_files(root) shouldBe Set(_default_database(root))

            When("the owned runtime is shut down")
            Subsystem.shutdownOwned(subsystem)
            val stopped = subsystem._with_managed_datastore_lease_c(_ =>
              _bind(subsystem, "workflow", _records, _import_id))
            Then("neither an expired capability nor a new dispatch can reopen a stopped resource owner")
            stopped shouldBe a[Consequence.Failure[?]]
            escaped.loadC() shouldBe a[Consequence.Failure[?]]
            subsystem.systemNode.state shouldBe SystemNode.State.Stopped
            subsystem.systemNode.activeLeaseCount shouldBe 0
            subsystem.systemNode.managedDataStoreCount shouldBe 0
          }
        }
      }
    }

    "E4 retain closed synthetic state during operator relocation" must _e4 {
      "reopen an operator-copied target database beneath the project hidden directory without losing evidence or duplicating import" in {
        Given("a synthetic legacy target database with a retained row and a stable project-relative evidence reference")
        _with_fixture() { (root, arguments) =>
          val source = root.resolve("target/workflow.sqlite")
          val destination = _default_database(root)
          val reference = ".codex-workflow/evidence/retained.txt"
          val payload = "retained synthetic evidence"
          val evidencefile = root.resolve(reference)
          Files.createDirectories(evidencefile.getParent)
          Files.writeString(evidencefile, payload, UTF_8)
          _write_host(root, Map("textus.datastore.kind" -> "sqlite", "textus.datastore.path" -> source.toString))
          val sourceowner = _with_runtime(root, arguments) { subsystem =>
            _take(subsystem._with_managed_datastore_lease_c(_ =>
              _bind(subsystem, "workflow", _records, _sentinel_id).flatMap(_.createC(
                Record.data("value" -> "retained", "evidence" -> reference)))))
            _record_count(subsystem) shouldBe 1
            subsystem.systemNode
          }

          When("the operator copies only the fully closed synthetic database to an absent hidden destination")
          sourceowner.state shouldBe SystemNode.State.Stopped
          sourceowner.activeLeaseCount shouldBe 0
          sourceowner.managedDataStoreCount shouldBe 0
          Files.exists(Path.of(source.toString + "-wal")) shouldBe false
          Files.exists(Path.of(source.toString + "-shm")) shouldBe false
          Files.exists(destination) shouldBe false
          val retainedbytes = Files.readAllBytes(source).toVector
          Files.createDirectories(destination.getParent)
          Files.copy(source, destination)
          _write_host(root)

          Then("ordinary reconfiguration reads the original row and its unchanged evidence reference")
          _with_runtime(root, arguments) { subsystem =>
            _expect_evidence(subsystem, root, reference, payload)
            _record_count(subsystem) shouldBe 1
            subsystem.systemNode.managedDataStoreCount shouldBe 1
            subsystem.systemNode.activeLeaseCount shouldBe 0
          }

          When("normal startup imports a new synthetic row into the relocated destination")
          _write_seed(root, Vector(_records -> "relocated-evidence"))
          _with_runtime(root, arguments) { subsystem =>
            Then("the prior row, reference and imported values coexist")
            _expect_rows(subsystem, "workflow", _records, "relocated-evidence")
            _expect_evidence(subsystem, root, reference, payload)
            _record_count(subsystem) shouldBe 2
          }

          When("the same normal startup input is read again by a fresh owner")
          _with_runtime(root, arguments) { subsystem =>
            Then("the same two records remain without duplicate creation and the source remains available for rollback")
            _expect_rows(subsystem, "workflow", _records, "relocated-evidence")
            _expect_evidence(subsystem, root, reference, payload)
            _record_count(subsystem) shouldBe 2
            subsystem.systemNode.managedDataStoreCount shouldBe 1
            subsystem.systemNode.activeLeaseCount shouldBe 0
            _database_files(root) shouldBe Set(source, destination)
            Files.readAllBytes(source).toVector shouldBe retainedbytes
          }
        }
      }
    }
  }

  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }

  private def _collection(name: String): DataStore.CollectionId =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "startup", name))

  private def _collection_text(collection: DataStore.CollectionId): String = collection match {
    case DataStore.CollectionId.EntityStore(identity) => s"entity:${identity.major}.${identity.minor}.${identity.name}"
    case _ => collection.collectionName
  }

  private def _plan_text(grants: Vector[ComponentRecordStorageDeclaration.CreateGrant] = Vector(_grant)): String = {
    val records = if (grants.isEmpty) None else Some(_take(ComponentRecordStorageDeclaration.createC(grants)))
    _take(SubsystemStoragePlanCodec.encode(_take(SubsystemStoragePlan.createC(_policy, _persistence, records))))
      .asInstanceOf[ConfigurationValue.StringValue].value
  }

  private def _write_host(root: Path, values: Map[String, String] = Map.empty,
    plantext: String = _plan_text()): Unit = {
    val directory = Files.createDirectories(root.resolve(".textus"))
    val config = values ++ Map(_storage_key -> plantext, "textus.import.data.file" -> "seed.yaml")
    // No seed exists during the precreation boot; absence is selected explicitly below.
    val selected = if (Files.exists(root.resolve("seed.yaml"))) config else config - "textus.import.data.file"
    Files.writeString(directory.resolve("config.json"), Json.obj(selected.toVector.sortBy(_._1)
      .map { case (key, value) => key -> Json.fromString(value) }*).noSpaces, UTF_8)
  }

  private def _write_seed(root: Path, entries: Vector[(DataStore.CollectionId, String)]): Unit = {
    val content = "datastore:\n" + entries.map { case (collection, value) =>
      s"  - collection: '${_collection_text(collection)}'\n    records:\n" +
        s"      - id: '${_import_id.print}'\n        value: '$value'\n"
    }.mkString
    Files.writeString(root.resolve("seed.yaml"), content, UTF_8)
    val file = root.resolve(".textus/config.json")
    val config = io.circe.parser.parse(Files.readString(file, UTF_8)).toOption.get
    Files.writeString(file, config.mapObject(_.add("textus.import.data.file", Json.fromString("seed.yaml"))).noSpaces, UTF_8)
  }

  private def _bind(subsystem: Subsystem, storename: String, collection: DataStore.CollectionId,
    entryid: DataStore.StringEntryId): Consequence[ComponentRecordStorage] =
    subsystem.bindComponentRecordStorageC(_owner, storename, collection, entryid, _context)

  private def _create_prior_row(subsystem: Subsystem, storename: String, collection: DataStore.CollectionId): Unit =
    _take(subsystem._with_managed_datastore_lease_c(_ =>
      _bind(subsystem, storename, collection, _sentinel_id).flatMap(_.createC(Record.data("value" -> "retained")))))

  private def _load(subsystem: Subsystem, storename: String, collection: DataStore.CollectionId,
    entryid: DataStore.StringEntryId): Option[Record] =
    _take(subsystem._with_managed_datastore_lease_c(_ => _bind(subsystem, storename, collection, entryid).flatMap(_.loadC())))

  private def _expect_rows(subsystem: Subsystem, storename: String, collection: DataStore.CollectionId, value: String): Unit = {
    _load(subsystem, storename, collection, _sentinel_id).flatMap(_.getString("value")) shouldBe Some("retained")
    val imported = _load(subsystem, storename, collection, _import_id)
    imported.flatMap(_.getString("value")) shouldBe Some(value)
    imported.flatMap(_.getString("id")) shouldBe Some(_import_id.print)
  }

  private def _environment(subsystem: Subsystem): ComponentDataStore.Environment =
    ComponentDataStore.Environment(ResolvedParameters.fromResolvedConfiguration(subsystem.configuration), Some(subsystem.configuration))

  private def _record_count(subsystem: Subsystem): Int = {
    given ExecutionContext = _context
    _take(subsystem._with_managed_datastore_lease_c(_ =>
      subsystem.resolveManagedComponentDataStoreC(_environment(subsystem), ComponentDataStore.Request.forComponent(_owner, "workflow"))
        .flatMap {
          case store: SearchableDataStore => store.count(_records, QueryDirective(Query.Empty))
          case _ => fail("The managed destination requires ordinary record counting")
        }))
  }

  private def _expect_evidence(subsystem: Subsystem, root: Path, reference: String, payload: String): Unit = {
    val retained = _load(subsystem, "workflow", _records, _sentinel_id)
    retained.flatMap(_.getString("value")) shouldBe Some("retained")
    val actual = retained.flatMap(_.getString("evidence"))
    actual shouldBe Some(reference)
    Files.readString(root.resolve(actual.get), UTF_8) shouldBe payload
  }

  private def _load_from_store(subsystem: Subsystem, storename: String, collection: DataStore.CollectionId,
    entryid: DataStore.StringEntryId): Option[Record] = {
    given ExecutionContext = _context
    _take(subsystem._with_managed_datastore_lease_c(_ =>
      subsystem.resolveManagedComponentDataStoreC(_environment(subsystem), ComponentDataStore.Request.forComponent(_owner, storename))
        .flatMap(_.load(collection, entryid))))
  }

  private def _retained_import(root: Path, arguments: Array[String], destination: Path, value: String): Unit = {
    _with_runtime(root, arguments)(_create_prior_row(_, "workflow", _records))
    _write_seed(root, Vector(_records -> value))
    When("normal startup imports a seed after the managed owner has closed its prior row")
    _with_runtime(root, arguments) { subsystem =>
      Then("the managed destination retains both rows and is the only physical database")
      _expect_rows(subsystem, "workflow", _records, value)
      subsystem.systemNode.managedDataStoreCount shouldBe 1
      subsystem.systemNode.activeLeaseCount shouldBe 0
      _database_files(root) shouldBe Set(destination)
    }
    Files.delete(root.resolve("seed.yaml"))
    _remove_seed_selection(root)
    When("a fresh runtime reads the selected destination without replaying startup data")
    _with_runtime(root, arguments) { subsystem =>
      Then("the earlier row and imported values survive reopen")
      _expect_rows(subsystem, "workflow", _records, value)
      subsystem.systemNode.managedDataStoreCount shouldBe 1
      subsystem.systemNode.activeLeaseCount shouldBe 0
      _database_files(root) shouldBe Set(destination)
    }
  }

  private def _remove_seed_selection(root: Path): Unit = {
    val file = root.resolve(".textus/config.json")
    val config = io.circe.parser.parse(Files.readString(file, UTF_8)).toOption.get
    Files.writeString(file, config.mapObject(_.remove("textus.import.data.file")).noSpaces, UTF_8)
  }

  private def _database_files(root: Path): Set[Path] =
    Using.resource(Files.walk(root)) { paths =>
      paths.iterator().asScala.filter(path => Files.isRegularFile(path) &&
        Vector(".sqlite", ".sqlite3", ".db").exists(path.toString.endsWith)).toSet
    }

  private def _with_runtime[A](root: Path, arguments: Array[String])(run: Subsystem => A): A = {
    val runtime = new CncfRuntime()
    try {
      val subsystem = _take(runtime.initializeForEmbedding(root, arguments, modeHint = Some(RunMode.Command)))
      try run(subsystem)
      finally {
        Subsystem.shutdownOwned(subsystem)
        subsystem.systemNode.activeLeaseCount shouldBe 0
        subsystem.systemNode.managedDataStoreCount shouldBe 0
      }
    } finally runtime.closeEmbedding()
  }

  private def _default_database(root: Path): Path =
    root.resolve(".codex-workflow/storage/workflow.sqlite")

  private def _car_defaults(root: Path): Map[String, String] = Map(
    "textus.datastore.kind" -> "sqlite", "textus.datastore.path" -> "${runtime.root}/.codex-workflow/storage/workflow.sqlite")

  private def _with_fixture[A](defaults: Path => Map[String, String] = _car_defaults)(run: (Path, Array[String]) => A): A = {
    val work = Files.createDirectories(Path.of("target/startup-managed-data-import/work").toAbsolutePath.normalize)
    val root = Files.createTempDirectory(work, "runtime-").toRealPath()
    try {
      val assets = Files.createDirectories(root.resolve("assets"))
      val componentjar = assets.resolve("main.jar")
      Using.resource(new ZipOutputStream(Files.newOutputStream(componentjar))) { zip =>
        zip.putNextEntry(new ZipEntry(
          "org/goldenport/cncf/component/builtin/specification/SpecificationComponent$Factory.class"))
        zip.closeEntry()
      }
      val componentdescriptor = Files.writeString(assets.resolve("component-descriptor.json"),
        """{"schemaVersion":3,"component":{"namespace":"org.goldenport.cncf","id":"Specification","version":"0.1.0"}}""", UTF_8)
      val assembly = "subsystem: application\ncomponents:\n  - namespace: org.goldenport.cncf\n    id: Specification\n    version: 0.1.0\nconfig:\n" +
        defaults(root).toVector.sortBy(_._1).map { case (key, value) => s"  $key: '${value.replace("'", "''")}'\n" }.mkString
      val assemblydescriptor = Files.writeString(assets.resolve("assembly-descriptor.yaml"), assembly, UTF_8)
      val car = root.resolve("specification.car")
      CarArchiveFixture.write(car, Vector(
        "component/main.jar" -> componentjar,
        "component-descriptor.json" -> componentdescriptor,
        "assembly-descriptor.yaml" -> assemblydescriptor))
      val descriptor = Files.writeString(root.resolve("test.yaml"),
        """kind: test-descriptor
          |execution:
          |  profile: controlled
          |  key: startup-managed-data-import
          |  time:
          |    mode: manual
          |    start-at: '2026-11-17T00:00:00Z'
          |  random:
          |    mode: seeded
          |    seed: startup-managed-data-import
          |  ids:
          |    mode: deterministic
          |  scheduler:
          |    mode: manual
          |  ordering:
          |    mode: deterministic
          |""".stripMargin, UTF_8)
      run(root, Array("--no-default-components", s"--${RuntimeConfig.componentFileKey}=$car",
        s"--textus.test.descriptor=$descriptor"))
    } finally {
      Using.resource(Files.walk(root)) { paths =>
        paths.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists(_))
      }
    }
  }
}
