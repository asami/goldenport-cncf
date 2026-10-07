package org.goldenport.cncf.cli

import java.nio.file.Files
import java.time.Instant
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.config.CncfConfigurationParameterCatalog
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.*
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class CncfRuntimeStorageInitializationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Runtime initialization rejected: $failure")
  }
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "runtime", name))
  private val policy = take(EventPersistencePolicy.createC("runtime-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("facts"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(ComponentId("org.example.Workflow"),
        "workflow", collection("roots"), collection("continuations")))))
  private val plan = take(SubsystemStoragePlan.createC(policy, declaration, None))
  private val text = take(SubsystemStoragePlanCodec.encode(plan))
    .asInstanceOf[ConfigurationValue.StringValue].value
  private def event = EventRecord(EventId("test", "runtime", Some(Instant.EPOCH), Some("transient")),
    "unmatched", "unmatched", Map.empty, Map.empty, Instant.EPOCH, true,
    EventRecord.Status.Stored, EventLane.Transactional)
  private def controlledArguments(cwd: java.nio.file.Path): Array[String] = {
    val descriptor = cwd.resolve("test.yaml")
    Files.writeString(descriptor,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: storage-runtime
        |  time:
        |    mode: manual
        |    start-at: 2026-10-02T00:00:00Z
        |  random:
        |    mode: seeded
        |    seed: storage-runtime
        |  ids:
        |    mode: deterministic
        |  scheduler:
        |    mode: manual
        |  ordering:
        |    mode: deterministic
        |""".stripMargin)
    Array(s"--textus.test.descriptor=$descriptor")
  }

  "Canonical runtime storage initialization" should {
    "capture a host file plan before extra component construction and restore the captures" in {
      Given("an explicit controlled descriptor and a real CWD host file selecting SQLite and a typed plan")
      val cwd = Files.createTempDirectory("cncf-runtime-storage-")
      val directory = Files.createDirectories(cwd.resolve(".textus"))
      val database = cwd.resolve("runtime.sqlite")
      Files.writeString(directory.resolve("config.json"), Json.obj(
        CncfConfigurationParameterCatalog.SUBSYSTEM_STORAGE_PLAN_KEY -> Json.fromString(text),
        "textus.datastore.kind" -> Json.fromString("sqlite"),
        "textus.datastore.path" -> Json.fromString(database.toString)).noSpaces)
      val before = (SystemNodeConstruction.current, EventStoreConstruction.current,
        EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current)
      val runtime = new CncfRuntime()
      var componentBoundaryObserved = false
      When("the public embedding entry assembles its explicitly controlled runtime")
      val subsystem = take(runtime.initializeForEmbedding(cwd, controlledArguments(cwd),
        extraComponents = s => {
          componentBoundaryObserved = true
          s.systemNode.state shouldBe SystemNode.State.Running
          take(s.eventStore.append(Vector(event))).head.persistent shouldBe false
          Vector.empty
        }))
      try {
        Then("the configured EventStore is ready before components and no declaration escapes")
        componentBoundaryObserved shouldBe true
        take(subsystem.eventStore.load(event.id)) shouldBe None
        (SystemNodeConstruction.current, EventStoreConstruction.current,
          EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current) shouldBe before
      } finally {
        Subsystem.shutdownOwned(subsystem)
        runtime.closeEmbedding()
      }
    }

    "preserve ordinary startup when no host storage declaration exists" in {
      Given("an explicit controlled descriptor without host storage configuration")
      val runtime = new CncfRuntime()
      val cwd = Files.createTempDirectory("cncf-runtime-no-storage-")
      When("the public embedding entry starts without a storage plan")
      val subsystem = take(runtime.initializeForEmbedding(cwd, controlledArguments(cwd)))
      try {
        Then("the ordinary legacy EventStore behavior remains available")
        take(subsystem.eventStore.append(Vector(event))).head.persistent shouldBe true
      } finally {
        Subsystem.shutdownOwned(subsystem)
        runtime.closeEmbedding()
      }
    }

    "reject direct argument grants before component construction" in {
      Given("a valid plan supplied directly as an argument rather than a host file")
      val runtime = new CncfRuntime()
      val cwd = Files.createTempDirectory("cncf-runtime-reject-storage-")
      var componentBoundaryObserved = false
      val before = (SystemNodeConstruction.current, EventStoreConstruction.current,
        EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current)
      When("the public embedding entry attempts source admission")
      val result = runtime.initializeForEmbedding(cwd,
        Array(s"--textus.subsystem.storage.plan=$text"),
        extraComponents = _ => { componentBoundaryObserved = true; Vector.empty })
      try {
        Then("startup fails closed without entering component construction or leaking captures")
        result shouldBe a[Consequence.Failure[?]]
        componentBoundaryObserved shouldBe false
        (SystemNodeConstruction.current, EventStoreConstruction.current,
          EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current) shouldBe before
      } finally runtime.closeEmbedding()
    }
  }
}
