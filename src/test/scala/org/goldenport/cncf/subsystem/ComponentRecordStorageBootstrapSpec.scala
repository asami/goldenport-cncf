package org.goldenport.cncf.subsystem

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.event.*
import org.goldenport.cncf.path.AliasResolver
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
final class ComponentRecordStorageBootstrapSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _context = ExecutionContext.create()
  private val _owner = ComponentId("org.example.RecordWorkflow")
  private def _collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "hostrecords", name))
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Record host rejected: $failure")
  }
  private val _records = _collection("registrations")
  private val _instances = _collection("instances")
  private val _events = _take(EventStorePersistenceDeclaration.createC("events",
    _collection("cursor"), _collection("events"), _collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(_owner, "workflow", _instances, _collection("continuations")))))
  private val _declaration = _take(ComponentRecordStorageDeclaration.createC(Vector(
    ComponentRecordStorageDeclaration.CreateGrant(_owner, "workflow", _records))))
  private val _policy = _take(EventPersistencePolicy.createC("record-host-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val _scope = ScopeContext(ScopeKind.Subsystem, "record-host", None, _context.observability)
  private def _configuration(path: String) = ResolvedConfiguration(Configuration(Map(
    "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
    "textus.datastore.path" -> ConfigurationValue.StringValue(path))), ConfigurationTrace.empty)

  "Trusted record storage host construction" should {
    "capture declarations without installation and isolate subsequent ordinary hosts" in {
      Given("one explicit record declaration and canonical host configuration")
      val file = Files.createTempDirectory("record-public-host-").resolve("shared.sqlite")
      val config = _configuration(file.toString)
      When("the existing bootstrap constructs declared and ordinary hosts")
      val host = _take(SubsystemEventStoreBootstrap.createC(_policy, _events, _scope,
        config, None, AliasResolver.empty, _declaration))
      val ordinary = _take(SubsystemEventStoreBootstrap.createC(_policy, _events, _scope, config))
      try {
        Then("record authority adds no pool to the canonical shared infrastructure preparation")
        host.subsystem.systemNode.managedDataStoreCount shouldBe 1
        ordinary.subsystem.systemNode.managedDataStoreCount shouldBe 1
        ComponentRecordStorageConstruction.current shouldBe None
        When("the declared host inspects its fixed registration identity before installation")
        val initial = _take(host.subsystem._with_managed_datastore_lease_c(_ =>
          host.subsystem.bindComponentRecordStorageC(_owner, "workflow", _records,
            DataStore.StringEntryId("submission-1"), _context).flatMap(_.loadC())))
        Then("canonical preparation has not installed a registration record")
        initial shouldBe None
        host.subsystem.systemNode.managedDataStoreCount shouldBe 1
        When("the declared host explicitly Creates inside its managed lease")
        val stored = _take(host.subsystem._with_managed_datastore_lease_c { _ =>
          for {
            storage <- host.subsystem.bindComponentRecordStorageC(_owner, "workflow", _records,
              DataStore.StringEntryId("submission-1"), _context)
            _ <- storage.createC(Record.dataAuto("payload" -> "retained"))
            record <- storage.loadC()
          } yield record
        })
        val denied = ordinary.subsystem._with_managed_datastore_lease_c(_ =>
          ordinary.subsystem.bindComponentRecordStorageC(_owner, "workflow", _records,
            DataStore.StringEntryId("submission-1"), _context))
        Then("only the declared host receives record authority and ordinary construction does not inherit it")
        stored.flatMap(_.getString("payload")) shouldBe Some("retained")
        denied shouldBe a[Consequence.Failure[?]]
        host.subsystem.systemNode.managedDataStoreCount shouldBe 1
        ordinary.subsystem.systemNode.managedDataStoreCount shouldBe 1
      } finally {
        _take(host.shutdownC())
        _take(ordinary.shutdownC())
      }
    }

    "reject reserved record targets before canonical host construction" in {
      Given("a record declaration overlapping the Workflow instance target")
      val file = Files.createTempDirectory("record-public-reserved-").resolve("shared.sqlite")
      val conflicting = _take(ComponentRecordStorageDeclaration.createC(Vector(
        ComponentRecordStorageDeclaration.CreateGrant(_owner, "workflow", _instances))))
      When("trusted bootstrap evaluates the conflicting declarations")
      val rejected = SubsystemEventStoreBootstrap.createC(_policy, _events, _scope,
        _configuration(file.toString), None, AliasResolver.empty, conflicting)
      Then("no host/resource is produced and authority does not leak")
      rejected shouldBe a[Consequence.Failure[?]]
      Files.exists(file) shouldBe false
      ComponentRecordStorageConstruction.current shouldBe None
    }
  }
}
