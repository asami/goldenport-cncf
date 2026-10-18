package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.bootstrap.{BootstrapConfig, CncfBootstrap}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.event.*
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct. 18, 2026
 */
final class CanonicalRuntimeStorageInitializationSpec extends AnyWordSpec with Matchers {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Canonical runtime storage fixture rejected: $failure")
  }
  private given context: ExecutionContext = ExecutionContext.create()
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "runtimehost", name))
  private val policy = take(EventPersistencePolicy.createC("runtime-host-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("facts"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(ComponentId("org.example.RuntimeWorkflow"),
        "workflow", collection("instances"), collection("continuations")))))
  private def environment(subsystem: Subsystem) = ComponentDataStore.Environment(
    ResolvedParameters.fromResolvedConfiguration(subsystem.configuration), Some(subsystem.configuration))
  private def descriptor(root: Path): Path = {
    val file = root.resolve("test.yaml")
    Files.writeString(file,
      s"""kind: test-descriptor
         |execution:
         |  profile: controlled
         |  key: canonical-storage-preparation
         |  time:
         |    mode: manual
         |    start-at: '2026-10-01T00:00:00Z'
         |  random:
         |    mode: seeded
         |    seed: canonical-storage-preparation
         |  ids:
         |    mode: deterministic
         |  scheduler:
         |    mode: manual
         |  ordering:
         |    mode: deterministic
         |config:
         |  textus.datastore.kind: sqlite
         |  textus.datastore.path: ${root.resolve("host.sqlite")}
         |""".stripMargin)
    file
  }
  private def configured[A](body: => A): A =
    EventStoreConstruction.withService(EventStore.inMemory(policy)) {
      EventStorePersistenceConstruction.withDeclaration(declaration)(body)
    }

  "Canonical runtime storage preparation" should {
    "prepare storage before extra-Component assembly and retain its cursor across owned runtime reopen" in {
      // Given the real launcher bootstrap with host-owned typed persistence inputs.
      val root = Files.createTempDirectory("canonical-runtime-storage-")
      val configFile = descriptor(root)
      var assemblyObserved = false
      val config = BootstrapConfig(cwd = root,
        args = Array(s"--textus.test.descriptor=$configFile", "--no-default-components"),
        extraComponents = subsystem => {
          subsystem.systemNode.managedDataStoreCount shouldBe 1
          subsystem.systemNode.activeLeaseCount shouldBe 0
          assemblyObserved = true
          Nil
        })
      val first = configured(take(CncfBootstrap.initialize(config)))
      val firstNode = first.subsystem.systemNode
      try {
        assemblyObserved shouldBe true
        firstNode.bindingCount shouldBe 1
        take(first.subsystem.initializeEventCursorC(environment(first.subsystem))).revision shouldBe
          EventCursorRecordV1.initial.revision
      } finally first.close()
      first.close()
      firstNode.state shouldBe SystemNode.State.Stopped
      firstNode.managedDataStoreCount shouldBe 0
      firstNode.bindingCount shouldBe 0
      // When a fresh canonical runtime opens the same admitted database.
      val reopened = configured(take(CncfBootstrap.initialize(config)))
      try {
        reopened.subsystem.systemNode should not be theSameInstanceAs(firstNode)
        reopened.subsystem.systemNode.managedDataStoreCount shouldBe 1
        // Then restart did not replace the explicitly installed cursor.
        reopened.subsystem.initializeEventCursorC(environment(reopened.subsystem)) shouldBe
          a[Consequence.Failure[?]]
        val retained = take(reopened.subsystem.withWorkflowVerifiedMultiGuardSpaceC(
          declaration.workflowGrants.head.componentId, environment(reopened.subsystem)) { space =>
          space.dataStore(declaration.cursorCollection).flatMap(
            _.load(declaration.cursorCollection, declaration.cursorEntryId))
        })
        retained should not be empty
      } finally reopened.close()
      reopened.subsystem.systemNode.state shouldBe SystemNode.State.Stopped
    }

    "restore construction scopes before an ordinary runtime is initialized" in {
      // Given a configured host whose construction scope has already returned.
      val root = Files.createTempDirectory("canonical-runtime-policy-isolation-")
      val configFile = descriptor(root)
      val config = BootstrapConfig(cwd = root,
        args = Array(s"--textus.test.descriptor=$configFile", "--no-default-components"))
      val configuredHost = configured(take(CncfBootstrap.initialize(config)))
      val event = EventRecord(EventId("test", "runtimehost", Some(Instant.EPOCH), Some("unmatched")),
        "unmatched", "unmatched", Map.empty, Map.empty, Instant.EPOCH, true,
        EventRecord.Status.Stored, EventLane.Transactional)
      try {
        take(configuredHost.subsystem.eventStore.append(Vector(event))).head.persistent shouldBe false
      } finally configuredHost.close()
      // When a runtime is launched without that host authority.
      val ordinary = take(CncfBootstrap.initialize(config))
      try {
        // Then policy and preparation do not leak through global runtime state.
        ordinary.subsystem.systemNode.managedDataStoreCount shouldBe 0
        take(ordinary.subsystem.eventStore.append(Vector(event))).head.persistent shouldBe true
      } finally ordinary.close()
    }
  }
}
