package org.goldenport.cncf.subsystem

import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import cats.syntax.all.*
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct. 31, 2026
 */
final class ComponentRecordStorageHostSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _context = ExecutionContext.create()
  private val _owner = ComponentId("org.example.RecordWorkflow")
  private val _collection = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "records", "registrations"))
  private val _id = DataStore.StringEntryId("submission-1")
  private val _declaration = _take(ComponentRecordStorageDeclaration.createC(Vector(
    ComponentRecordStorageDeclaration.CreateGrant(_owner, "workflow", _collection))))
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Managed record rejected: $failure")
  }
  private def _configuration(path: String) = ResolvedConfiguration(Configuration(Map(
    "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
    "textus.datastore.path" -> ConfigurationValue.StringValue(path))), ConfigurationTrace.empty)
  private def _bind(subsystem: Subsystem) =
    subsystem.bindComponentRecordStorageC(_owner, "workflow", _collection, _id, _context)

  "Managed component record storage" should {
    "allow one create owner across two actual JVMs and retain a committed intent when its acknowledgement is lost" in {
      Given("two independent managed SQLite runtimes sharing only their declared native collection")
      val root = Files.createTempDirectory("record-two-process-")
      val file = root.resolve("shared.sqlite")
      val first = new ComponentRecordStorageCrossProcessProbe.Child(root, file, "race", "first", false)
      try {
        val second = new ComponentRecordStorageCrossProcessProbe.Child(root, file, "race", "second", false)
        try {
          first.ready(); second.ready()
          When("both ready JVMs independently attempt the same create-only key")
          first.release(); second.release()
          val a = first.result(); val b = second.result()
          Then("only one acknowledged INSERT can own effects, and both observe its immutable value")
          Vector(a, b).count(_.committed) shouldBe 1
          Vector(a, b).count(_.acknowledged) shouldBe 1
          a.owner shouldBe b.owner
          Set("first", "second") should contain(a.owner)
          first.pid should not be second.pid
        } finally second.close()
      } finally first.close()
      Given("a fault injected only after a real successful managed INSERT")
      val lost = new ComponentRecordStorageCrossProcessProbe.Child(root, file, "uncertain", "original", true)
      try {
        lost.ready(); lost.release()
        val written = lost.result()
        written.committed shouldBe true
        written.acknowledged shouldBe false
      } finally lost.close()
      When("a different JVM later encounters that retained uncertain intent")
      val observer = new ComponentRecordStorageCrossProcessProbe.Child(root, file, "uncertain", "late", false)
      try {
        observer.ready(); observer.release()
        val retained = observer.result()
        Then("it cannot create or reclaim ownership, although the original native row is readable")
        retained.committed shouldBe false
        retained.acknowledged shouldBe false
        retained.owner shouldBe "original"
      } finally observer.close()
    }

    "persist native terminal rows larger than File limits and retain intent after a rejected duplicate save" in {
      Given("ordinary managed SQLite with separate create-only intent and terminal identities")
      val file = Files.createTempDirectory("record-large-native-").resolve("shared.sqlite")
      val nativeText = "n" * (16777216 + 1)
      val intentId = DataStore.StringEntryId("large:intent")
      val terminalId = DataStore.StringEntryId("large:terminal")
      ComponentRecordStorageConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file.toString)) { subsystem =>
          _take(subsystem._with_managed_datastore_lease_c { _ =>
            for {
              intent <- subsystem.bindComponentRecordStorageC(_owner, "workflow", _collection, intentId, _context)
              terminal <- subsystem.bindComponentRecordStorageC(_owner, "workflow", _collection, terminalId, _context)
              _ <- intent.createC(Record.data("status" -> "UNKNOWN"))
              _ = When("a complete native result exceeds the sixteen-MiB File boundary")
              _ <- terminal.createC(Record.data("status" -> "completed", "payload" -> nativeText))
              loaded <- terminal.loadC()
              _ = Then("SQL preserves the native result without applying a File acquisition quota")
              _ = loaded.flatMap(_.getString("payload")) shouldBe Some(nativeText)
              _ = When("another save to the same create-only terminal identity is rejected")
              duplicate = terminal.createC(Record.data("status" -> "failed"))
              retained <- intent.loadC()
              original <- terminal.loadC()
            } yield {
              Then("the failed save cannot turn the old UNKNOWN intent into success or replace its result")
              duplicate.isSuccess shouldBe false
              retained.flatMap(_.getString("status")) shouldBe Some("UNKNOWN")
              original.flatMap(_.getString("status")) shouldBe Some("completed")
            }
          })
        }
      }
    }

    "freeze declarative Creates without writing and refuse expired or foreign staging" in {
      Given("a fixed record capability and an aliased nested payload inside a native lease")
      val file = Files.createTempDirectory("record-stage-").resolve("shared.sqlite")
      val source = Array("original", "second")
      val payload = Record.dataAuto("nested" -> Record.dataAuto("values" ->
        scala.collection.immutable.ArraySeq.unsafeWrapArray(source)))
      val original = RecordEncoder.json(payload)
      val completed: ActionExecution = ActionExecution.Completed(StateMachineOperationResult(
        StateMachineResultTypeReference("record.closing.v1"), ContextReference("closing", "1")))
      var escaped: ComponentRecordStorage = null
      ComponentRecordStorageConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file.toString)) { subsystem =>
          _take(subsystem._with_managed_datastore_lease_c { _ =>
            _bind(subsystem).flatMap { storage =>
              escaped = storage
              When("the capability stages a Create and the caller changes the source alias")
              storage.stageCreateC(payload).flatMap { program =>
                source(0) = "changed-after-staging"
                var guards = Vector.empty[DataStoreConditionalRoot]
                var creates = Vector.empty[EntityVersionedSideEffect.Create]
                val projection = new (UnitOfWorkOp ~> Consequence) {
                  def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
                    case UnitOfWorkOp.StageWorkflowClosingLocalIntent(intent) => intent.projectC.map {
                      case Left(guard) => guards :+= guard; ()
                      case Right(create) => creates :+= create; ()
                    }
                    case _ => Consequence.stateConflict("Fixture rejects non-staging effects")
                  }
                }
                _take(program.value.foldMap(projection).flatMap(identity))
                WorkflowClosingProgramPreparation.prepareC(program.map(_ => completed)).flatMap { prepared =>
                    Then("preparation retains frozen bytes and never performs a physical Create")
                    prepared.result shouldBe completed.asInstanceOf[ActionExecution.Completed].result
                    guards shouldBe empty
                    creates.map(_.entryId) shouldBe Vector(_id)
                    RecordEncoder.json(creates.head.record) shouldBe original
                    storage.loadC().map { row =>
                      row shouldBe None
                      When("a foreign thread attempts to stage through the same live capability")
                      val denied = CompletableFuture.supplyAsync(() => storage.stageCreateC(payload).isSuccess).get()
                      Then("the issuing thread boundary remains enforced")
                      denied shouldBe false
                    }
                }
              }
            }
          })
          When("the original dispatch lease has ended")
          val expired = escaped.stageCreateC(payload)
          Then("staging cannot acquire a replacement lease")
          expired shouldBe a[Consequence.Failure[?]]
          subsystem.systemNode.activeLeaseCount shouldBe 0
        }
      }
    }

    "reject physical identity injection and unsupported payloads without writing" in {
      Given("a declared fixed-target capability in its issuing dispatch")
      val file = Files.createTempDirectory("record-stage-payload-").resolve("shared.sqlite")
      ComponentRecordStorageConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file.toString)) { subsystem =>
          _take(subsystem._with_managed_datastore_lease_c { _ => _bind(subsystem).flatMap { storage =>
            When("null, an injected physical id and an unsupported mutable object are staged")
            val outcomes = Vector(storage.stageCreateC(null),
              storage.stageCreateC(Record.dataAuto("id" -> "foreign", "payload" -> "injected")),
              storage.stageCreateC(Record.dataAuto("payload" -> new Object)))
            Then("every outcome is rejected and the fixed target remains absent")
            outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
            storage.loadC().map(_ shouldBe None)
          }})
        }
      }
    }

    "deny a record grant overlapping captured Workflow infrastructure before resolution" in {
      Given("a host whose record grant collides with its Workflow instance collection")
      val file = Files.createTempDirectory("record-reserved-").resolve("shared.sqlite")
      def _collection_(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "records", name))
      val events = _take(org.goldenport.cncf.event.EventStorePersistenceDeclaration.createC("events",
        _collection_("cursor"), _collection_("events"), _collection_("indexes"), Vector(
          org.goldenport.cncf.event.EventStorePersistenceDeclaration.WorkflowGrant(
            _owner, "workflow", _collection, _collection_("continuations")))))
      EventStorePersistenceConstruction.withDeclaration(events) {
        ComponentRecordStorageConstruction.withDeclaration(_declaration) {
          TestComponentFactory.withEmptySubsystem(configuration = _configuration(file.toString)) { subsystem =>
            When("a managed dispatch attempts to bind the conflicting record target")
            val denied = subsystem._with_managed_datastore_lease_c(_ => _bind(subsystem))
            Then("record permission cannot overwrite infrastructure or open a pool")
            denied shouldBe a[Consequence.Failure[?]]
            subsystem.systemNode.managedDataStoreCount shouldBe 0
            Files.exists(file) shouldBe false
            When("the verified Workflow route attempts to admit the same conflicting declaration")
            val deniedworkflow = subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner,
              ComponentDataStore.Environment(_context.runtime.resolvedParameters, Some(subsystem.configuration)))(
              _ => Consequence.unit)
            Then("verified completion rejects the reserved grant before resolving any resource")
            deniedworkflow shouldBe a[Consequence.Failure[?]]
            subsystem.systemNode.managedDataStoreCount shouldBe 0
            Files.exists(file) shouldBe false
          }
        }
      }
    }

    "deny absent grants and absent dispatch leases before opening a resource" in {
      Given("an ordinary host and a declared host with no active lease")
      val file = Files.createTempDirectory("record-admission-").resolve("shared.sqlite")
      TestComponentFactory.withEmptySubsystem(configuration = _configuration(file.toString)) { ordinary =>
        When("an ordinary host attempts to bind record storage")
        val denied = ordinary._with_managed_datastore_lease_c(_ => _bind(ordinary))
        Then("no grant can be inferred from the SQL configuration")
        denied shouldBe a[Consequence.Failure[?]]
        ordinary.systemNode.managedDataStoreCount shouldBe 0
      }
      ComponentRecordStorageConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file.toString)) { declared =>
          When("the declared host attempts binding outside dispatch")
          val denied = _bind(declared)
          Then("binding cannot open its own replacement lease")
          denied shouldBe a[Consequence.Failure[?]]
          declared.systemNode.managedDataStoreCount shouldBe 0
          Files.exists(file) shouldBe false
        }
      }
    }

    "Create once, reopen retained bytes and revoke escaped capabilities" in {
      Given("two sequential host lifetimes sharing an exact managed database declaration")
      val file = Files.createTempDirectory("record-reopen-").resolve("shared.sqlite")
      var escaped: ComponentRecordStorage = null
      ComponentRecordStorageConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file.toString)) { first =>
          When("the issuing dispatch Creates and reads its fixed record")
          _take(first._with_managed_datastore_lease_c { _ =>
            for {
              storage <- _bind(first)
              _ = escaped = storage
              _ <- storage.createC(Record.dataAuto("payload" -> "retained"))
              record <- storage.loadC()
            } yield {
              Then("one immutable record is retained and duplicate Create cannot reset it")
              record.flatMap(_.getString("payload")) shouldBe Some("retained")
              When("a duplicate Create and a foreign thread try to use the capability")
              val duplicate = storage.createC(Record.dataAuto("payload" -> "changed"))
              val foreign = CompletableFuture.supplyAsync(() => storage.loadC().isSuccess).get()
              Then("neither can replace the record or escape dispatch thread ownership")
              duplicate shouldBe a[Consequence.Failure[?]]
              foreign shouldBe false
              first.systemNode.managedDataStoreCount shouldBe 1
            }
          })
          When("the dispatch lease has ended")
          val denied = escaped.loadC()
          val deniedwrite = escaped.createC(Record.dataAuto("payload" -> "changed"))
          Then("the escaped capability cannot read or write outside its original lease")
          denied shouldBe a[Consequence.Failure[?]]
          deniedwrite shouldBe a[Consequence.Failure[?]]
        }
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file.toString)) { reopened =>
          When("a new dispatch reads the same allocated identity after reopen")
          val retained = _take(reopened._with_managed_datastore_lease_c(_ => _bind(reopened).flatMap(_.loadC())))
          val escapedread = escaped.loadC()
          Then("no initialization or reset is required")
          retained.flatMap(_.getString("payload")) shouldBe Some("retained")
          escapedread shouldBe a[Consequence.Failure[?]]
        }
      }
      ComponentRecordStorageConstruction.current shouldBe None
    }

    "reject changed owner, logical store and collection before resource access" in {
      Given("one exact host-captured declaration")
      val file = Files.createTempDirectory("record-scope-").resolve("shared.sqlite")
      ComponentRecordStorageConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file.toString)) { subsystem =>
          When("a managed dispatch requests three foreign scopes")
          val denied = _take(subsystem._with_managed_datastore_lease_c { _ => Consequence.success(Vector(
            subsystem.bindComponentRecordStorageC(ComponentId("org.example.Other"), "workflow", _collection, _id, _context),
            subsystem.bindComponentRecordStorageC(_owner, "other", _collection, _id, _context),
            subsystem.bindComponentRecordStorageC(_owner, "workflow",
              DataStore.CollectionId.EntityStore(EntityCollectionId("test", "records", "other")), _id, _context))) })
          Then("all fail without creating a managed pool or physical file")
          denied.foreach(_ shouldBe a[Consequence.Failure[?]])
          subsystem.systemNode.managedDataStoreCount shouldBe 0
          Files.exists(file) shouldBe false
        }
      }
    }
  }
}

/** Test-only child entry: actual managed storage, with post-commit acknowledgement
  * loss explicitly injected at the transport boundary. Never a product launcher.
  */
object ComponentRecordStorageCrossProcessProbe {
  import java.io.{BufferedReader, InputStreamReader, PrintWriter}
  import java.nio.charset.StandardCharsets.UTF_8
  import java.nio.file.Path
  import java.util.concurrent.{Callable, Executors, TimeUnit}

  final case class Result(committed: Boolean, acknowledged: Boolean, owner: String)
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => throw new IllegalStateException("Managed record probe rejected: " + failure)
  }
  private def classpath: String = {
    def files(url: java.net.URL): Vector[String] =
      if (url.getProtocol == "file") Vector(Path.of(url.toURI).toAbsolutePath.normalize.toString) else Vector.empty
    val property = Option(System.getProperty("java.class.path")).toVector
      .flatMap(_.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)).toVector).filter(_.nonEmpty)
    var loader = Option(getClass.getClassLoader)
    var entries = Vector.empty[String]
    while (loader.nonEmpty) {
      loader.foreach {
        case urls: java.net.URLClassLoader => entries ++= urls.getURLs.toVector.flatMap(files)
        case _ => ()
      }
      loader = loader.flatMap(x => Option(x.getParent))
    }
    val sources = Vector(getClass, classOf[Subsystem]).flatMap(c =>
      Option(c.getProtectionDomain).flatMap(d => Option(d.getCodeSource)).toVector.flatMap(s => files(s.getLocation)))
    val paths = (sources ++ entries ++ property).distinct
    require(sources.size == 2 && paths.forall(p => Files.exists(Path.of(p))), "Actual test runtime classpath required")
    paths.mkString(java.io.File.pathSeparator)
  }
  final class Child(root: Path, database: Path, key: String, owner: String, loseAck: Boolean) extends AutoCloseable {
    private val process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString,
      "-cp", classpath, "org.goldenport.cncf.subsystem.ComponentRecordStorageCrossProcessProbe",
      database.toString, key, owner, loseAck.toString)
      .directory(root.toFile).redirectError(root.resolve(key + "-" + owner + ".stderr.log").toFile).start()
    private val reads = Executors.newSingleThreadExecutor()
    private val reader = new BufferedReader(new InputStreamReader(process.getInputStream, UTF_8))
    private val writer = new PrintWriter(process.getOutputStream, true, UTF_8)
    val pid: Long = process.pid()
    private def line(prefix: String): String = reads.submit(new Callable[String] {
      def call(): String = {
        var next = reader.readLine()
        while (next != null && !next.startsWith(prefix)) next = reader.readLine()
        require(next != null, "Probe ended before " + prefix + "; retained logs at " + root)
        next.substring(prefix.length)
      }
    }).get(45L, TimeUnit.SECONDS)
    def ready(): Unit = require(line("RECORD_READY ") == pid.toString, "Actual child readiness required")
    def release(): Unit = writer.println("create")
    def result(): Result = {
      val fields = line("RECORD_RESULT ").split(" ", -1)
      require(fields.length == 3, "Bounded probe result required")
      require(process.waitFor(45L, TimeUnit.SECONDS) && process.exitValue() == 0, "Actual probe termination required")
      Result(fields(0).toBoolean, fields(1).toBoolean, fields(2))
    }
    def close(): Unit = {
      try {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            require(process.waitFor(5L, TimeUnit.SECONDS), "Owned test child did not terminate")
          }
        }
      } finally {
        writer.close(); reader.close(); reads.shutdownNow()
      }
    }
  }
  def main(args: Array[String]): Unit = {
    require(args.length == 4 && Set("first", "second", "original", "late")(args(2)))
    val configuration = ResolvedConfiguration(Configuration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(args(0)))), ConfigurationTrace.empty)
    val owner = ComponentId("org.example.RecordWorkflow")
    val collection = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "records", "registrations"))
    val declaration = take(ComponentRecordStorageDeclaration.createC(Vector(
      ComponentRecordStorageDeclaration.CreateGrant(owner, "workflow", collection))))
    ComponentRecordStorageConstruction.withDeclaration(declaration) {
      TestComponentFactory.withEmptySubsystem(configuration = configuration) { subsystem =>
        take(subsystem._with_managed_datastore_lease_c { _ => for {
          storage <- subsystem.bindComponentRecordStorageC(owner, "workflow", collection,
            DataStore.StringEntryId(args(1)), ExecutionContext.create())
          _ = println("RECORD_READY " + ProcessHandle.current().pid())
          _ = require(scala.io.StdIn.readLine() == "create", "Only the fixture create operation is accepted")
          committed = storage.createC(Record.data("status" -> "UNKNOWN", "owner" -> args(2)))
          // Inject lost acknowledgement after genuine SQL commit; never infer
          // a right to effects from the row being observable afterward.
          acknowledged = committed.isSuccess && !args(3).toBoolean
          retained <- storage.loadC()
          actual <- retained.flatMap(_.getString("owner")).map(Consequence.success)
            .getOrElse(Consequence.stateConflict("Original native owner is unavailable"))
          _ = println("RECORD_RESULT " + committed.isSuccess + " " + acknowledged + " " + actual)
        } yield () })
      }
    }
  }
}
