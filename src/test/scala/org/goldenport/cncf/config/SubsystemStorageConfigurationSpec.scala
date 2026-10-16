package org.goldenport.cncf.config

import java.nio.file.{Path, Paths}
import org.goldenport.Consequence
import org.goldenport.configuration.*
import org.goldenport.configuration.source.ConfigurationSource
import org.goldenport.configuration.source.file.FileConfigLoader
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
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
final class SubsystemStorageConfigurationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Storage admission rejected: $failure")
  }
  private val storageKey = CncfConfigurationParameterCatalog.SUBSYSTEM_STORAGE_PLAN_KEY
  private val witness = CncfConfigurationParameterCatalog.subsystemStoragePlan
  private val selected = take(SubsystemInstanceId.default("platform"))
  private val owner = ComponentId("org.example.Workflow")
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "admission", name))
  private val policy = take(EventPersistencePolicy.createC("admission-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val persistence = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("facts"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(owner, "workflow",
        collection("roots"), collection("continuations")))))
  private val plan = take(SubsystemStoragePlan.createC(policy, persistence, None))
  private val raw = take(witness.codec.encode(plan))
  private val text = raw.asInstanceOf[ConfigurationValue.StringValue].value
  private def source(origin: ConfigurationOrigin = ConfigurationOrigin.Project,
    path: String = ".textus/subsystems/platform/instances/default/config.yaml",
    values: Map[String, ConfigurationValue] = Map(storageKey -> raw), loaded: () => Unit = () => ()) =
    ConfigurationSource.File(origin, Paths.get(path), ConfigurationSource.Rank.Project,
      new FileConfigLoader {
        def load(path: Path): Consequence[Configuration] = {
          loaded()
          Consequence.success(Configuration(values))
        }
      })
  private def snapshot(sources: ConfigurationSource*) =
    take(ConfigurationResolver.default.resolveSnapshot(sources))
  private def bindings(value: ConfigurationResolutionSnapshot) =
    take(CncfRuntimeConfigurationProjection.forSubsystem(value, selected))
  private def only(binding: ConfigurationBinding[SubsystemStoragePlan, CncfConfigurationTarget]) =
    take(ConfigurationBindingCollection.from[CncfConfigurationTarget](Vector(binding)))
  private def rebuilt(binding: ConfigurationBinding[SubsystemStoragePlan, CncfConfigurationTarget],
    parameter: ConfigurationParameter[SubsystemStoragePlan], value: SubsystemStoragePlan) =
    take(ConfigurationBinding.initial(take(ConfigurationBindingCandidate.create(
      parameter, binding.target, value, binding.provenance))))

  "Host storage configuration admission" should {
    "register only the canonical Subsystem-instance parameter witness" in {
      Given("the closed catalog and all four target kinds")
      val definition = take(CncfConfigurationParameterCatalog.closed.schema.definition(storageKey))
      val targets = Vector(CncfConfigurationTarget.Global,
        take(CncfConfigurationTarget.ComponentClass.create(owner)),
        take(CncfConfigurationTarget.ComponentInstance.create(selected, ComponentInstanceId(owner, "default"))),
        take(CncfConfigurationTarget.SubsystemInstance.create(selected)))
      When("the definition decodes each target without granting authority")
      val results = targets.map(t => definition.input(storageKey, t, raw, s"config.$storageKey"))
      Then("only the canonical Subsystem target is permitted and no alias is registered")
      results.map(_.isSuccess) shouldBe Vector(false, false, false, true)
      definition.decodeOnlyAliases shouldBe Vector.empty
      definition.isConfidential shouldBe true
      definition.parameterId shouldBe witness.id
      CncfConfigurationParameterCatalog.closed.schema.definition("cncf.subsystem.storage.plan").isSuccess shouldBe false
    }

    "admit retained host file inputs without loading a source again" in {
      Given("HOME, Project and CWD file inputs and counting physical loaders")
      var loads = 0
      val inputs = Vector(ConfigurationOrigin.Home, ConfigurationOrigin.Project, ConfigurationOrigin.Cwd)
        .map(origin => snapshot(source(origin, loaded = () => loads += 1)))
      When("the host projects and admits each selected binding from its retained snapshot")
      val admitted = inputs.map(s => take(SubsystemStorageConfiguration.admitC(bindings(s), selected, s)).get)
      Then("the complete file provenance is retained and each source was loaded exactly once")
      loads shouldBe 3
      admitted.map(_.binding.provenance.origin) shouldBe
        Vector(ConfigurationOrigin.Home, ConfigurationOrigin.Project, ConfigurationOrigin.Cwd)
      admitted.foreach(a => take(witness.codec.encode(a.binding.value)) shouldBe raw)
    }

    "preserve absence without inventing authority and reject missing host context" in {
      Given("a retained empty host file and absent effective storage binding")
      val s = snapshot(source(values = Map.empty))
      val effective = bindings(s)
      When("the host admits absence or receives incomplete context")
      val absent = SubsystemStorageConfiguration.admitC(effective, selected, s)
      val invalid = Vector(SubsystemStorageConfiguration.admitC(null, selected, s),
        SubsystemStorageConfiguration.admitC(effective, null, s),
        SubsystemStorageConfiguration.admitC(effective, selected, null))
      Then("absence remains None while incomplete context fails closed")
      take(absent) shouldBe None
      invalid.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject descriptor defaults and non-file runtime inputs" in {
      Given("a Component-style assembly default and environment or argument inputs")
      val empty = snapshot(source(values = Map.empty))
      val descriptor = take(CncfAssemblyConfigurationProjection.candidates(Map(storageKey -> text),
        Some("component-descriptor.json"), selected))
      val context = take(CncfConfigurationResolutionContext.forSubsystem(selected))
      val assembly = take(ConfigurationBindingResolver.resolve(descriptor, context.generic))
      val runtime = Vector(snapshot(ConfigurationSource.Args(Map(storageKey -> text))),
        snapshot(ConfigurationSource.Env(Map(storageKey -> text))), snapshot(source(ConfigurationOrigin.Resource)))
      When("owning-host storage admission checks their winning provenance")
      val results = Vector(SubsystemStorageConfiguration.admitC(assembly, selected, empty)) ++
        runtime.map(s => SubsystemStorageConfiguration.admitC(bindings(s), selected, s))
      Then("a Subsystem target alone cannot turn descriptor or ambient inputs into host grants")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject a different selected Subsystem or retained source" in {
      Given("an effective binding from one Subsystem and source identity")
      val first = snapshot(source())
      val otherSource = snapshot(source(path = "other-host/config.conf"))
      val effective = bindings(first)
      val other = take(SubsystemInstanceId.default("other"))
      When("the host checks mismatching Subsystem and source selections")
      val results = Vector(SubsystemStorageConfiguration.admitC(effective, other, first),
        SubsystemStorageConfiguration.admitC(effective, selected, otherSource))
      Then("neither target nor source provenance can be substituted")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject payload substitution and a rebuilt parameter witness" in {
      Given("a real file binding, different plan data and a counterfeit witness with the same id")
      val s = snapshot(source())
      val binding = take(bindings(s).binding(witness)).get
      val changedPolicy = take(EventPersistencePolicy.createC("changed-v1", policy.rules))
      val changed = take(SubsystemStoragePlan.createC(changedPolicy, persistence, None))
      val counterfeit = take(ConfigurationParameter.create(witness.id, witness.codec))
      val changedBindings = only(rebuilt(binding, witness, changed))
      val counterfeitBindings = only(rebuilt(binding, counterfeit, binding.value))
      When("the effective binding is compared with the actual retained file binding")
      val results = Vector(SubsystemStorageConfiguration.admitC(changedBindings, selected, s),
        SubsystemStorageConfiguration.admitC(counterfeitBindings, selected, s))
      Then("matching metadata or parameter names cannot admit different data or witness identity")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "permit an explicit host file to override an untrusted lower descriptor request" in {
      Given("host runtime candidates and a lower-precedence descriptor request for the same parameter")
      val s = snapshot(source())
      val runtime = take(CncfRuntimeConfigurationProjection.candidates(s, selected))
      val descriptor = take(CncfAssemblyConfigurationProjection.candidates(Map(storageKey -> text),
        Some("component-descriptor.json"), selected))
      val candidates = take(ConfigurationBindingCandidates.from(runtime.bindings ++ descriptor.bindings))
      val context = take(CncfConfigurationResolutionContext.forSubsystem(selected))
      val effective = take(ConfigurationBindingResolver.resolve(candidates, context.generic))
      When("the host checks the effective file winner against its retained source")
      val admitted = take(SubsystemStorageConfiguration.admitC(effective, selected, s)).get
      Then("only the explicit host file wins while the descriptor remains overridden evidence")
      admitted.binding.provenance.sourceType shouldBe Some("file")
      admitted.binding.overridden.map(_.provenance.sourceType) shouldBe Some(Some("assembly-descriptor"))
    }

    "admit only immutable configuration until the host enters trusted construction" in {
      Given("retained host configuration and current construction captures")
      val s = snapshot(source())
      val before = (SystemNodeConstruction.current, EventStoreConstruction.current,
        EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current)
      When("the host admits the plan without assembling services")
      val admitted = take(SubsystemStorageConfiguration.admitC(bindings(s), selected, s)).get
      Then("admission changes no capture and trusted construction restores its temporary declarations")
      (SystemNodeConstruction.current, EventStoreConstruction.current,
        EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current) shouldBe before
      admitted.withConstruction {
        EventStorePersistenceConstruction.current shouldBe Some(admitted.binding.value.persistence)
        EventStoreConstruction.current.isDefined shouldBe true
        SystemNodeConstruction.current shouldBe before._1
      }
      (SystemNodeConstruction.current, EventStoreConstruction.current,
        EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current) shouldBe before
    }
  }
}
