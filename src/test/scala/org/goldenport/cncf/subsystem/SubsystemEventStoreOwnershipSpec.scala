package org.goldenport.cncf.subsystem

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Internal node-ownership evidence, separate from the external-package API spec.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class SubsystemEventStoreOwnershipSpec extends AnyWordSpec with Matchers {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Node ownership fixture rejected: $failure")
  }
  private given context: ExecutionContext = ExecutionContext.create()
  private def scope = ScopeContext(ScopeKind.Subsystem, "ownership", None, context.observability)
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "ownership", name))
  private val policy = take(EventPersistencePolicy.createC("ownership-policy-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("events"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(ComponentId("org.example.Ownership"),
        "workflow", collection("instances"), collection("continuations")))))

  "EventStore bootstrap SystemNode ownership" should {
    "prepare the shared pool before assembly and unwind it when assembly fails" in {
      // Given a newly constructed Subsystem with no acquired physical resources.
      val file = Files.createTempDirectory("eventstore-before-assembly-").resolve("host.sqlite")
      val configuration = ResolvedConfiguration(Configuration(Map(
        "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
        "textus.datastore.path" -> ConfigurationValue.StringValue(file.toString))), ConfigurationTrace.empty)
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        val subsystem = new Subsystem("preparation", configuration = configuration)
        val node = subsystem.systemNode
        try {
          node.managedDataStoreCount shouldBe 0
          // When core/service assembly is entered after infrastructure preparation.
          val error = intercept[IllegalStateException] {
            SubsystemStorageInitialization.withPreparedStorage(subsystem) {
              node.managedDataStoreCount shouldBe 1
              node.activeLeaseCount shouldBe 0
              throw new IllegalStateException("assembly failure after preparation")
            }
          }
          // Then the original failure survives and its owned pool is closed once.
          error.getMessage shouldBe "assembly failure after preparation"
          node.state shouldBe SystemNode.State.Stopped
          node.managedDataStoreCount shouldBe 0
          node.bindingCount shouldBe 0
        } finally Subsystem.shutdownOwned(subsystem)
      }
    }

    "close its own registered physical store after releasing the logical binding" in {
      // Given an owned host and an initialized canonical managed SQLite resource.
      val file = Files.createTempDirectory("eventstore-owned-node-").resolve("host.sqlite")
      val configuration = ResolvedConfiguration(Configuration(Map(
        "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
        "textus.datastore.path" -> ConfigurationValue.StringValue(file.toString))), ConfigurationTrace.empty)
      val host = take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope, configuration))
      val node = host.subsystem.systemNode
      try {
        // Shared infrastructure is ready before first-install or Operations.
        node.managedDataStoreCount shouldBe 1
        node.activeLeaseCount shouldBe 0
        take(host.initializeCursorC())
        node.managedDataStoreCount shouldBe 1
        node.activeLeaseCount shouldBe 0
        // When the host owner shuts down using its public lifecycle API.
        take(host.shutdownC())
        // Then both physical registry and logical binding are terminalized.
        node.state shouldBe SystemNode.State.Stopped
        node.managedDataStoreCount shouldBe 0
        node.bindingCount shouldBe 0
        host.initializeCursorC() shouldBe a[Consequence.Failure[?]]
        take(host.shutdownC()) // Idempotent terminal cleanup.
      } finally take(host.shutdownC())
    }

    "release its binding without stopping a SystemNode supplied by the runtime" in {
      // Given a runtime-owned node inherited during canonical host construction.
      val node = SystemNode.create()
      val file = Files.createTempDirectory("eventstore-borrowed-node-").resolve("host.sqlite")
      val configuration = ResolvedConfiguration(Configuration(Map(
        "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
        "textus.datastore.path" -> ConfigurationValue.StringValue(file.toString))), ConfigurationTrace.empty)
      val host = SystemNodeConstruction.withNode(node) {
        take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope, configuration))
      }
      try {
        host.subsystem.systemNode should be theSameInstanceAs node
        node.bindingCount shouldBe 1
        node.managedDataStoreCount shouldBe 1
        // When only that host's lifecycle ends.
        take(host.shutdownC())
        // Then the runtime owner can still admit another binding and lease.
        node.state shouldBe SystemNode.State.Running
        node.bindingCount shouldBe 0
        node.managedDataStoreCount shouldBe 1
        val binding = node.bind()
        try {
          val lease = take(binding.acquireLeaseC())
          try node.activeLeaseCount shouldBe 1
          finally lease.release()
        } finally binding.release()
        node.activeLeaseCount shouldBe 0
      } finally {
        take(host.shutdownC())
        take(node.shutdownC())
      }
      node.state shouldBe SystemNode.State.Stopped
    }

    "reject an unprepared persistent host without closing its borrowed runtime node" in {
      // Given a declaration but no admitted persistent storage destination.
      val node = SystemNode.create()
      val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      try {
        // When preparation fails before the built-in Component assembly call.
        val result = SystemNodeConstruction.withNode(node) {
          SubsystemEventStoreBootstrap.createC(policy, declaration, scope, configuration)
        }
        // Then the binding is unwound and ownership remains with the runtime.
        result shouldBe a[Consequence.Failure[?]]
        node.state shouldBe SystemNode.State.Running
        node.bindingCount shouldBe 0
        node.activeLeaseCount shouldBe 0
        node.managedDataStoreCount shouldBe 0
      } finally take(node.shutdownC())
    }
  }
}
