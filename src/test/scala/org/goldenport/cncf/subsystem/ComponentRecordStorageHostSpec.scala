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
 * @version Oct.  8, 2026
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
