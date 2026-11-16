package org.goldenport.cncf.cli

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.configuration.*
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.config.*
import org.goldenport.cncf.processexecution.RuntimeProcessExecutionPlanDeclarationCodec
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
  * @version Nov. 16, 2026
  */
final class SelectedCarConfigurationDefaultsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"CAR defaults failed: $other")
  }
  private val _selected = _take(SubsystemInstanceId.default("application"))
  private val _owner = ComponentId("org.example.Application")
  private val _storage_key = CncfConfigurationParameterCatalog.SUBSYSTEM_STORAGE_PLAN_KEY
  private val _process_key = CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY
  private def _collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "defaults", name))
  private val _persistence = _take(EventStorePersistenceDeclaration.createC("events", _collection("cursor"),
    _collection("events"), _collection("index"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(
      _owner, "workflow", _collection("instances"), _collection("continuations")))))
  private val _policy = _take(EventPersistencePolicy.createC("v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val _storage = _take(SubsystemStoragePlanCodec.encode(_take(SubsystemStoragePlan.createC(_policy, _persistence, None))))
    .asInstanceOf[ConfigurationValue.StringValue].value
  private val _process = """{"schema":"cncf.runtime-process-execution.v1","programs":[],"grants":[]}"""
  private val _values = Map(_storage_key -> _storage, _process_key -> _process,
    "application.path" -> "${runtime.root}/state", "application.json" -> """{"path":"${runtime.root}/input"}""")
  private def _with_car[A](run: GenericSubsystemDescriptor => A): A = {
    val path = Files.createTempFile("selected-car-defaults-", ".car")
    try {
      Using.resource(new ZipOutputStream(Files.newOutputStream(path))) { zip =>
        val descriptor = """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Application","version":"0.1.0"}}"""
        val assembly = "subsystem: application\ncomponents:\n  - namespace: org.example\n    id: Application\n    version: 0.1.0\nconfig:\n" + _values.toVector.sortBy(_._1)
          .map((key,value) => s"  $key: '${value.replace("'", "''")}'\n").mkString
        Vector("component-descriptor.json" -> descriptor, "assembly-descriptor.yaml" -> assembly).foreach { (name,text) =>
          zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(UTF_8)); zip.closeEntry()
        }
      }
      run(_take(GenericSubsystemDescriptor.loadComponentArchive(path)))
    } finally Files.deleteIfExists(path)
  }
  private def _bindings(descriptor: GenericSubsystemDescriptor) = {
    val candidates = _take(CncfDiscoveredAssemblyConfigurationProjection.candidates(Map.empty, None, Some(descriptor), _selected))
    val context = _take(CncfConfigurationResolutionContext.forSubsystem(_selected))
    _take(ConfigurationBindingResolver.resolve(candidates, context.generic))
  }
  private def _empty_snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector.empty))

  "Selected CAR configuration defaults" should {
    "roundtrip the catalog's parsed process declaration before selected runtime admission" in _with_car { descriptor =>
      Given("a selected CAR whose catalog value is declaration data")
      val result = _bindings(descriptor)
      val declaration = _take(result.binding(CncfConfigurationParameterCatalog.subsystemProcessExecutionPlan)).get.value
      When("the catalog declaration codec re-encodes and decodes the value")
      val encoded = _take(RuntimeProcessExecutionPlanDeclarationCodec.encode(declaration))
      val restored = _take(RuntimeProcessExecutionPlanDeclarationCodec.decode(encoded))
      Then("the private catalog remains roundtrippable without carrying an admitted runtime plan")
      restored.schemaVersion shouldBe "cncf.runtime-process-execution.v1"
      restored.programs shouldBe empty
      encoded shouldBe ConfigurationValue.StringValue(_process)
    }

    "preserve packaged defaults through ordinary runtime resolution and final descriptor admission" in _with_car { descriptor =>
      Given("a selected CAR and an actual runtime root with no user configuration")
      val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      val root = Path.of("/tmp/defaults-runtime")
      val policy = RepositoryBootstrapPolicy(componentFiles = Vector(descriptor.path.toString),
        baseDirectory = root, defaultRepositoriesEnabled = false)
      When("the normal runtime resolves and admits its selected descriptor")
      val resolved = _take(GenericSubsystemFactory.runtimeResolveDescriptorC(configuration, Some(policy))).get
      val admitted = _take(GenericSubsystemFactory.runtimeAdmitDescriptorC(resolved, configuration, Some(policy)))
      Then("the CAR's own assembly is not mistaken for an external override and its root is resolved")
      admitted.componentDefaultSources.keySet should contain(_storage_key)
      admitted.config("application.path") shouldBe root.resolve("state").toString
      _take(SubsystemStorageConfiguration.admitC(_bindings(admitted), _selected, _empty_snapshot)).nonEmpty shouldBe true
      _take(SubsystemProcessExecutionConfiguration.admitC(_bindings(admitted), _selected)).nonEmpty shouldBe true
    }

    "admit its storage and process declarations for the selected Subsystem" in _with_car { descriptor =>
      Given("an actual selected CAR containing its own standard declarations and no user file")
      When("ordinary discovery projects and admits its typed configuration")
      val result = _bindings(descriptor)
      val stored = _take(SubsystemStorageConfiguration.admitC(result, _selected, _empty_snapshot)).get
      val commands = _take(SubsystemProcessExecutionConfiguration.admitC(result, _selected)).get
      Then("CAR provenance is preserved and no second file snapshot is required")
      stored.binding.provenance.origin shouldBe ConfigurationOrigin.Resource
      stored.binding.provenance.sourceType shouldBe Some("component-car-defaults")
      _take(SubsystemStoragePlanCodec.encode(stored.binding.value)) shouldBe ConfigurationValue.StringValue(_storage)
      commands.programs shouldBe empty
    }

    "keep external assembly overrides separate from CAR-owned defaults" in _with_car { descriptor =>
      Given("a CAR and an external assembly overriding its process declaration")
      val source = GenericSubsystemAssemblyDescriptorSource(Record.data("config" -> Record.data(_process_key -> _process)), "external")
      When("normal assembly merging and typed projection run")
      val merged = _take(GenericSubsystemDescriptor.applyAssemblyOverrideC(descriptor, source))
      val result = _bindings(merged)
      Then("the override retains ordinary assembly provenance while untouched CAR storage is admitted")
      merged.componentDefaultSources.contains(_process_key) shouldBe false
      _take(result.binding(CncfConfigurationParameterCatalog.subsystemProcessExecutionPlan)).get
        .provenance.sourceType shouldBe Some("assembly-descriptor")
      SubsystemProcessExecutionConfiguration.admitC(result, _selected).isSuccess shouldBe false
      _take(SubsystemStorageConfiguration.admitC(result, _selected, _empty_snapshot)).nonEmpty shouldBe true
    }

    "derive paths from RuntimeRoot only for surviving CAR defaults" in _with_car { descriptor =>
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

    "retain selected-Subsystem scope for CAR storage and process defaults" in _with_car { descriptor =>
      Given("CAR defaults projected for one selected Subsystem")
      val result = _bindings(descriptor)
      val other = _take(SubsystemInstanceId.default("other"))
      When("another Subsystem tries to use those bindings")
      val stored = SubsystemStorageConfiguration.admitC(result, other, _empty_snapshot)
      val commands = SubsystemProcessExecutionConfiguration.admitC(result, other)
      Then("neither accepts a different owning Subsystem")
      stored.isSuccess shouldBe false
      commands.isSuccess shouldBe false
    }
  }
}
