package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GenericSubsystemStorageInitializationSpec extends AnyWordSpec with Matchers {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Generic storage fixture rejected: $failure")
  }
  private given context: ExecutionContext = ExecutionContext.create()
  private def scope = ScopeContext(ScopeKind.Subsystem, "generic-storage", None, context.observability)
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "genericstorage", name))
  private val policy = take(EventPersistencePolicy.createC("generic-storage-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("events"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(ComponentId("org.example.GenericWorkflow"),
        "workflow", collection("instances"), collection("continuations")))))
  private def configuration(root: Path, values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(Configuration((values +
      (RuntimeConfig.repositoryDirKey -> s"component-dir:$root")).map { case (key, value) =>
      key -> ConfigurationValue.StringValue(value)
    }), ConfigurationTrace.empty)
  private def sqlite(path: Path): Map[String, String] = Map(
    "textus.datastore.kind" -> "sqlite", "textus.datastore.path" -> path.toString)

  "Generic Subsystem storage preparation" should {
    "share the prepared pool between named and descriptor hosts without installing a cursor" in {
      // Given two canonical assembly routes borrowing one runtime-owned node.
      val root = Files.createTempDirectory("generic-shared-storage-")
      val file = root.resolve("host.sqlite")
      val config = configuration(root, sqlite(file))
      val node = SystemNode.create()
      var named: Option[SubsystemEventStoreBootstrap] = None
      var described: Option[Subsystem] = None
      try {
        named = Some(SystemNodeConstruction.withNode(node) {
          take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope,
            configuration(root, sqlite(file) + (RuntimeConfig.subsystemNameKey -> "named-storage"))))
        })
        node.managedDataStoreCount shouldBe 1
        node.activeLeaseCount shouldBe 0
        described = Some(SystemNodeConstruction.withNode(node) {
          EventStoreConstruction.withService(EventStore.inMemory(policy)) {
            EventStorePersistenceConstruction.withDeclaration(declaration) {
              GenericSubsystemFactory.defaultWithScope(
                GenericSubsystemDescriptor(root.resolve("descriptor.yaml"), "descriptor-storage"),
                scope, configuration = config)
            }
          }
        })
        // Then both assembled hosts retain exactly one registered pool.
        node.managedDataStoreCount shouldBe 1
        node.bindingCount shouldBe 2
        node.activeLeaseCount shouldBe 0
        // First-install still succeeds; preparation did not install initial data.
        take(named.get.initializeCursorC()).revision shouldBe EventCursorRecordV1.initial.revision
        named.get.initializeCursorC() shouldBe a[Consequence.Failure[?]]
        take(named.get.shutdownC())
        take(described.get.shutdownC())
        node.bindingCount shouldBe 0
        node.managedDataStoreCount shouldBe 1
        node.state shouldBe SystemNode.State.Running
      } finally {
        named.foreach(host => take(host.shutdownC()))
        described.foreach(host => take(host.shutdownC()))
        take(node.shutdownC())
      }
      node.managedDataStoreCount shouldBe 0
    }

    "prepare descriptor defaults using the effective supplied configuration" in {
      // Given descriptor storage defaults and a host-owned destination override.
      val root = Files.createTempDirectory("generic-effective-storage-")
      val defaultFile = root.resolve("default.sqlite")
      val selectedFile = root.resolve("selected.sqlite")
      val descriptor = GenericSubsystemDescriptor(root.resolve("descriptor.yaml"),
        "effective-storage", config = sqlite(defaultFile))
      val subsystem = EventStorePersistenceConstruction.withDeclaration(declaration) {
        GenericSubsystemFactory.defaultWithScope(descriptor, scope,
          configuration = configuration(root, Map("textus.datastore.path" -> selectedFile.toString)))
      }
      try {
        // Then preparation uses the merged configuration before service assembly.
        Files.exists(selectedFile) shouldBe true
        Files.exists(defaultFile) shouldBe false
        subsystem.systemNode.managedDataStoreCount shouldBe 1
        subsystem.systemNode.activeLeaseCount shouldBe 0
      } finally Subsystem.shutdownOwned(subsystem)
    }

    "unwind failed preparation on both routes without stopping a borrowed node" in {
      // Given a persistence declaration without a persistent destination.
      val root = Files.createTempDirectory("generic-missing-storage-")
      val config = configuration(root, Map.empty)
      val node = SystemNode.create()
      try {
        SystemNodeConstruction.withNode(node) {
          EventStorePersistenceConstruction.withDeclaration(declaration) {
            // When either route rejects memory as persistent shared infrastructure.
            intercept[IllegalStateException] {
              GenericSubsystemFactory.defaultWithScope("missing-named-storage", scope, None,
                config, AliasResolver.empty)
            }
            intercept[IllegalStateException] {
              GenericSubsystemFactory.defaultWithScope(
                GenericSubsystemDescriptor(root.resolve("descriptor.yaml"), "missing-descriptor-storage"),
                scope, configuration = config)
            }
          }
        }
        // Then neither failed binding or preparation lease survives.
        node.bindingCount shouldBe 0
        node.activeLeaseCount shouldBe 0
        node.managedDataStoreCount shouldBe 0
        node.state shouldBe SystemNode.State.Running
      } finally take(node.shutdownC())
    }
  }
}
