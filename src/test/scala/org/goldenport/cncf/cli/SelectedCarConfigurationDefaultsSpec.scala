package org.goldenport.cncf.cli

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.configuration.*
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.config.*
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Selected CAR defaults are ordinary source provenance, not a copied-file seal.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
final class SelectedCarConfigurationDefaultsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"CAR defaults failed: $other")
  }
  private val selected = take(SubsystemInstanceId.default("application"))
  private val owner = ComponentId("org.example.Application")
  private val storageKey = CncfConfigurationParameterCatalog.SUBSYSTEM_STORAGE_PLAN_KEY
  private val processKey = CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "defaults", name))
  private val persistence = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"),
    collection("events"), collection("index"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(
      owner, "workflow", collection("instances"), collection("continuations")))))
  private val policy = take(EventPersistencePolicy.createC("v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val storage = take(SubsystemStoragePlanCodec.encode(take(SubsystemStoragePlan.createC(policy, persistence, None))))
    .asInstanceOf[ConfigurationValue.StringValue].value
  private val process = """{"schema":"cncf.runtime-process-execution.v1","programs":[],"grants":[]}"""
  private val values = Map(storageKey -> storage, processKey -> process,
    "application.path" -> "${runtime.root}/state", "application.json" -> """{"path":"${runtime.root}/input"}""")
  private def withCar[A](run: GenericSubsystemDescriptor => A): A = {
    val path = Files.createTempFile("selected-car-defaults-", ".car")
    try {
      Using.resource(new ZipOutputStream(Files.newOutputStream(path))) { zip =>
        val descriptor = """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Application","version":"0.1.0"}}"""
        val assembly = "subsystem: application\ncomponents:\n  - namespace: org.example\n    id: Application\n    version: 0.1.0\nconfig:\n" + values.toVector.sortBy(_._1)
          .map((key,value) => s"  $key: '${value.replace("'", "''")}'\n").mkString
        Vector("component-descriptor.json" -> descriptor, "assembly-descriptor.yaml" -> assembly).foreach { (name,text) =>
          zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(UTF_8)); zip.closeEntry()
        }
      }
      run(take(GenericSubsystemDescriptor.loadComponentArchive(path)))
    } finally Files.deleteIfExists(path)
  }
  private def bindings(descriptor: GenericSubsystemDescriptor) = {
    val candidates = take(CncfDiscoveredAssemblyConfigurationProjection.candidates(Map.empty, None, Some(descriptor), selected))
    val context = take(CncfConfigurationResolutionContext.forSubsystem(selected))
    take(ConfigurationBindingResolver.resolve(candidates, context.generic))
  }
  private def emptySnapshot = take(ConfigurationResolver.default.resolveSnapshot(Vector.empty))

  "Selected CAR configuration defaults" should {
    "preserve packaged defaults through ordinary runtime resolution and final descriptor admission" in withCar { descriptor =>
      Given("a selected CAR and an actual runtime root with no user configuration")
      val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      val root = Path.of("/tmp/defaults-runtime")
      val policy = RepositoryBootstrapPolicy(componentFiles = Vector(descriptor.path.toString),
        baseDirectory = root, defaultRepositoriesEnabled = false)
      When("the normal runtime resolves and admits its selected descriptor")
      val resolved = take(GenericSubsystemFactory.runtimeResolveDescriptorC(configuration, Some(policy))).get
      val admitted = take(GenericSubsystemFactory.runtimeAdmitDescriptorC(resolved, configuration, Some(policy)))
      Then("the CAR's own assembly is not mistaken for an external override and its root is resolved")
      admitted.componentDefaultSources.keySet should contain(storageKey)
      admitted.config("application.path") shouldBe root.resolve("state").toString
      take(SubsystemStorageConfiguration.admitC(bindings(admitted), selected, emptySnapshot)).nonEmpty shouldBe true
      take(SubsystemProcessExecutionConfiguration.admitC(bindings(admitted), selected)).nonEmpty shouldBe true
    }

    "admit its storage and process declarations for the selected Subsystem" in withCar { descriptor =>
      Given("an actual selected CAR containing its own standard declarations and no user file")
      When("ordinary discovery projects and admits its typed configuration")
      val result = bindings(descriptor)
      val stored = take(SubsystemStorageConfiguration.admitC(result, selected, emptySnapshot)).get
      val commands = take(SubsystemProcessExecutionConfiguration.admitC(result, selected)).get
      Then("CAR provenance is preserved and no second file snapshot is required")
      stored.binding.provenance.origin shouldBe ConfigurationOrigin.Resource
      stored.binding.provenance.sourceType shouldBe Some("component-car-defaults")
      take(SubsystemStoragePlanCodec.encode(stored.binding.value)) shouldBe ConfigurationValue.StringValue(storage)
      commands.programs shouldBe empty
    }

    "keep external assembly overrides separate from CAR-owned defaults" in withCar { descriptor =>
      Given("a CAR and an external assembly overriding its process declaration")
      val source = GenericSubsystemAssemblyDescriptorSource(Record.data("config" -> Record.data(processKey -> process)), "external")
      When("normal assembly merging and typed projection run")
      val merged = take(GenericSubsystemDescriptor.applyAssemblyOverrideC(descriptor, source))
      val result = bindings(merged)
      Then("the override retains ordinary assembly provenance while untouched CAR storage is admitted")
      merged.componentDefaultSources.contains(processKey) shouldBe false
      take(result.binding(CncfConfigurationParameterCatalog.subsystemProcessExecutionPlan)).get
        .provenance.sourceType shouldBe Some("assembly-descriptor")
      SubsystemProcessExecutionConfiguration.admitC(result, selected).isSuccess shouldBe false
      take(SubsystemStorageConfiguration.admitC(result, selected, emptySnapshot)).nonEmpty shouldBe true
    }

    "derive paths from RuntimeRoot only for surviving CAR defaults" in withCar { descriptor =>
      Given("CAR path templates and an explicit descriptor overriding one path")
      val overrideDescriptor = GenericSubsystemDescriptor(Path.of("external.yaml"), "application",
        config = Map("application.path" -> "${runtime.root}/explicit"))
      val merged = GenericSubsystemDescriptor.mergeComponentDefaults(descriptor, overrideDescriptor)
      val root = Path.of("/tmp/runtime root \"quoted\"")
      When("the owning runtime resolves its effective descriptor defaults")
      val resolved = merged.withComponentDefaultRuntimeRoot(root)
      Then("explicit values stay literal and JSON paths remain valid even for quoted root names")
      resolved.config("application.path") shouldBe "${runtime.root}/explicit"
      io.circe.parser.parse(resolved.config("application.json")).toOption.get.hcursor
        .get[String]("path").toOption.get shouldBe root.resolve("input").toString
      resolved.configuration.values("application.json") shouldBe ConfigurationValue.StringValue(resolved.config("application.json"))
    }

    "retain selected-Subsystem scope for CAR storage and process defaults" in withCar { descriptor =>
      Given("CAR defaults projected for one selected Subsystem")
      val result = bindings(descriptor)
      val other = take(SubsystemInstanceId.default("other"))
      When("another Subsystem tries to use those bindings")
      val stored = SubsystemStorageConfiguration.admitC(result, other, emptySnapshot)
      val commands = SubsystemProcessExecutionConfiguration.admitC(result, other)
      Then("neither accepts a different owning Subsystem")
      stored.isSuccess shouldBe false
      commands.isSuccess shouldBe false
    }
  }
}
