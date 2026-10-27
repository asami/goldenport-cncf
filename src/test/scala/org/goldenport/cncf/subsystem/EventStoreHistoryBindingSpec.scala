package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.concurrent.CompletableFuture
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind, SecurityContext}
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Normal captured declaration, authority and dispatch lease boundaries.
  * @since   Oct. 25, 2026
  * @version Oct. 27, 2026
  */
final class EventStoreHistoryBindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _context = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
  private val _query = ManagedEventHistory.Query("sample.orders.v1", "one", Vector("recorded"))
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Unexpected managed binding rejection: $other")
  }
  private def _rejection[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.print
    case _ => fail("Expected a structured rejection")
  }
  private def _collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "historybinding", name))
  private val _declaration = _take(EventStorePersistenceDeclaration.createC("events",
    _collection("cursor"), _collection("events"), _collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(ComponentId("org.example.HistoryWorkflow"),
        "workflow", _collection("root"), _collection("continuation")))))
  private def _database: Path = {
    val root = Path.of("target", "managed-event-history", "binding").toAbsolutePath
    Files.createDirectories(root)
    Files.createTempDirectory(root, "spec-").resolve("events.sqlite")
  }
  private def _configuration(path: Path) = ResolvedConfiguration(Configuration(Map(
    "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
    "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)

  private def _bootstrap(path: Path): SubsystemEventStoreBootstrap = {
    val policy = _take(EventPersistencePolicy.createC("history-first-install-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist))))
    _take(SubsystemEventStoreBootstrap.createC(policy, _declaration,
      ScopeContext(ScopeKind.Subsystem, "history-first-install", None, _context.observability), _configuration(path)))
  }

  "Explicit EventStore first installation" should afterWord(
    "spec:managed-event-history example:E1 rules:R1,R2,R4,R5 phase:5.1") {
    "expose empty history and retain the initial cursor across duplicate calls and normal reopen" in {
      Given("Spec: docs/spec/managed-event-history.md; Rules: R1,R2,R4,R5; Example: E1; a fresh managed host")
      val path = _database
      given ExecutionContext = _context
      val first = _bootstrap(path)
      try {
        When("the trusted public bootstrap explicitly installs its captured storage")
        val cursor = _take(first.initializeStorageC())
        Then("only the real initial cursor exists and normal history is a complete empty retained window")
        cursor.revision shouldBe 1L
        cursor.lastSequence shouldBe 0L
        _take(first.subsystem._with_managed_datastore_lease_c { _ =>
          first.subsystem.bindEventHistoryC(_context).flatMap(_.readC(_query)).map { snapshot =>
            snapshot.events shouldBe empty
            snapshot.truncated shouldBe false
          }
        })
        first.initializeStorageC().isSuccess shouldBe false
        first.initializeCursorC().isSuccess shouldBe false
        _take(first.subsystem.loadEventCursorC(ComponentDataStore.Environment(
          _context.runtime.resolvedParameters, Some(first.subsystem.configuration)))).revision shouldBe 1L
      } finally _take(first.shutdownC())
      When("a new public host reopens the retained database without installation")
      val reopened = _bootstrap(path)
      try {
        val environment = ComponentDataStore.Environment(_context.runtime.resolvedParameters, Some(reopened.subsystem.configuration))
        Then("the cursor remains unchanged and another first-install attempt is refused")
        _take(reopened.subsystem.loadEventCursorC(environment)).lastSequence shouldBe 0L
        reopened.initializeStorageC().isSuccess shouldBe false
        _take(reopened.subsystem._with_managed_datastore_lease_c { _ =>
          reopened.subsystem.bindEventHistoryC(_context).flatMap(_.readC(_query)).map(_.events shouldBe empty)
        })
      } finally _take(reopened.shutdownC())
    }

    "preserve the legacy cursor-only initializer without implicitly upgrading its existing database" in {
      Given("Spec: docs/spec/managed-event-history.md; Rules: R2,R5; Example: E1; an explicitly cursor-only host")
      given ExecutionContext = _context
      val host = _bootstrap(_database)
      try {
        val initial = _take(host.initializeCursorC())
        When("full installation or history is requested against that retained cursor-only database")
        val rejected = host.initializeStorageC()
        val history = _take(host.subsystem._with_managed_datastore_lease_c(_ =>
          host.subsystem.bindEventHistoryC(_context).map(_.readC(_query))))
        Then("neither call repairs schema and the legacy cursor is not reset")
        rejected.isSuccess shouldBe false
        history.isSuccess shouldBe false
        _take(host.subsystem.loadEventCursorC(ComponentDataStore.Environment(
          _context.runtime.resolvedParameters, Some(host.subsystem.configuration)))).revision shouldBe initial.revision
      } finally _take(host.shutdownC())
    }
  }

  "EventStore history binding" should {
    "deny missing authority, declaration and active lease before resource resolution" in {
      Given("ordinary and declared hosts with SQL configuration but no inferred admission")
      val file = _database
      TestComponentFactory.withEmptySubsystem(configuration = _configuration(file)) { ordinary =>
        When("an authorized dispatch on an undeclared host attempts binding")
        val denied = ordinary._with_managed_datastore_lease_c(_ => ordinary.bindEventHistoryC(_context))
        Then("configuration alone grants no EventStore history resource")
        denied.isSuccess shouldBe false
        ordinary.systemNode.managedDataStoreCount shouldBe 0
      }
      EventStorePersistenceConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file)) { declared =>
          When("an unprivileged dispatch or an authorized caller without a dispatch attempts binding")
          val denied = declared._with_managed_datastore_lease_c(_ =>
            declared.bindEventHistoryC(ExecutionContext.test(SecurityContext.Privilege.User)))
          val outside = declared.bindEventHistoryC(_context)
          Then("both fail without opening a datastore or a substitute lease")
          denied.isSuccess shouldBe false
          outside.isSuccess shouldBe false
          declared.systemNode.managedDataStoreCount shouldBe 0
          declared.systemNode.activeLeaseCount shouldBe 0
          Files.exists(file) shouldBe false
        }
      }
      ManagedEventHistory.bindForCallC(null).isSuccess shouldBe false
    }

    "read canonical history through the existing admitted managed resource" in {
      Given("an admitted EventStore with one retained generic event in a normal dispatch")
      val file = _database
      val time = Instant.parse("2026-10-25T00:00:00Z")
      val event = EventRecord(EventId("sample", "binding", Some(time), Some("issued")), "recorded", "fact",
        Map("arbitrary" -> "payload"), Map("history.namespace" -> _query.correlationNamespace,
          "history.key" -> _query.correlationKey), time, true,
        EventRecord.Status.Stored, EventLane.Transactional, 9007199254740993L)
      val create = _take(EventRecordJsonV2.createSideEffectC(_declaration.eventCollection, event))
      EventStorePersistenceConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file)) { subsystem =>
          val component = TestComponentFactory.create("HistoryReader", Protocol.empty, subsystem = subsystem)
          val core = ActionCall.Core(null, _context, Some(component), None)
          _take(subsystem._with_managed_datastore_lease_c { _ =>
            given ExecutionContext = _context
            for {
              store <- subsystem.resolveManagedComponentDataStoreC(ComponentDataStore.Environment(
                _context.runtime.resolvedParameters, Some(subsystem.configuration)), _declaration.serviceRequest)
              _ <- store.create(_declaration.eventCollection, create.entryId, create.record)
              history <- ManagedEventHistory.bindForCallC(core)
              _ = When("the normal history capability reads the retained exact association")
              snapshot <- history.readC(_query)
            } yield {
              Then("the generic event is returned exactly through the existing managed resource")
              snapshot.events shouldBe Vector(event)
              snapshot.truncated shouldBe false
              snapshot.coverage shouldBe "retained-correlated-window"
              subsystem.systemNode.managedDataStoreCount shouldBe 1
            }
          })
          subsystem.systemNode.activeLeaseCount shouldBe 0
        }
      }
    }

    "honor revocation of the issuing lease without replacement resource access" in {
      Given("a captured history reader in a real issuing lease")
      EventStorePersistenceConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(_database)) { subsystem =>
          _take(subsystem._with_managed_datastore_lease_c { lease =>
            subsystem.bindEventHistoryC(_context).map { history =>
              When("the SystemNode revokes that same lease before the read")
              ObservationLeaseTestSupport.revoke(lease)
              val denied = history.readC(_query)
              Then("normal lease inheritance rejects the reader without acquiring another lease")
              denied.isSuccess shouldBe false
              lease.isRevoked shouldBe true
              subsystem.systemNode.managedDataStoreCount shouldBe 1
            }
          })
          subsystem.systemNode.activeLeaseCount shouldBe 0
        }
      }
    }

    "reject foreign-thread and escaped capabilities while preserving resource ownership" in {
      Given("a history reader captured during its issuing managed dispatch")
      val file = _database
      var escaped: ManagedEventHistory = null
      EventStorePersistenceConstruction.withDeclaration(_declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = _configuration(file)) { subsystem =>
          _take(subsystem._with_managed_datastore_lease_c { _ =>
            subsystem.bindEventHistoryC(_context).map { history =>
              escaped = history
              When("a different thread attempts to use the live reader")
              val foreign = CompletableFuture.supplyAsync(() => history.readC(_query)).get()
              Then("it cannot inherit the issuing thread's dispatch lease")
              _rejection(foreign) should include("escaped its issuing dispatch lease")
            }
          })
          When("the issuing lease has ended and a later dispatch is active")
          val expired = escaped.readC(_query)
          val replacement = _take(subsystem._with_managed_datastore_lease_c(_ =>
            Consequence.success(escaped.readC(_query))))
          Then("the escaped reader cannot borrow either a missing or replacement lease")
          _rejection(expired) should include("escaped its issuing dispatch lease")
          _rejection(replacement) should include("escaped its issuing dispatch lease")
          subsystem.systemNode.activeLeaseCount shouldBe 0
        }
      }
    }
  }
}
