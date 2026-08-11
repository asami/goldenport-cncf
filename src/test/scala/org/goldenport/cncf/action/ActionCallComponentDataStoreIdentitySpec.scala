package org.goldenport.cncf.action

import java.nio.file.{Files, Path, Paths}
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 10, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class ActionCallComponentDataStoreIdentitySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:component-local-datastore-layout, example:E1, rules:R1,R2,R3,R4, phase:M2"
  )
  private val _e2 = afterWord(
    "in spec:component-local-datastore-layout, example:E2, rules:R2,R5,R6, phase:M2"
  )
  private val _e3 = afterWord(
    "in spec:component-local-datastore-layout, example:E3, rules:R1,R2,R5, phase:M2"
  )
  private val _e4 = afterWord(
    "in spec:component-local-datastore-layout, example:E4, rules:R1,R2,R5, phase:M2"
  )

  "Component-local datastore layout" should {
    "E1 isolate same local IDs by canonical namespace-qualified selectors" must _e1 {
      "select distinct canonical policy, directory, full-path, and dedicated datastore prefixes" in {
        Given("Spec: docs/spec/component-local-datastore-layout.md; Rules: R1,R2,R3,R4; Example: E1")
        _with_temp_directory("component-datastore-selectors") { root =>
          given ExecutionContext = ExecutionContext.create()
          val alpha = ComponentId("org.example.alpha.ArtScene")
          val beta = ComponentId("org.example.beta.ArtScene")
          val alphaprefix = "textus.component.org.example.alpha.art-scene.datastores"
          val betaprefix = "textus.component.org.example.beta.art-scene.datastores"
          val alphadir = root.resolve("alpha-directory")
          val betapath = root.resolve("beta-override").resolve("application.db")
          val dedicatedpath = root.resolve("alpha-dedicated.db")
          val params = _params(Map(
            "textus.local-data.root" -> root.toString,
            s"$alphaprefix.application.policy" -> "local-only",
            s"$betaprefix.application.policy" -> "local-only",
            "textus.local-data.org.example.alpha.art-scene.dir" -> alphadir.toString,
            "textus.local-data.org.example.beta.art-scene.application.path" -> betapath.toString,
            s"$betaprefix.dedicated.policy" -> "external-default",
            s"$betaprefix.dedicated.sqlite.path" -> dedicatedpath.toString
          ))
          val collection = DataStore.CollectionId("selector_marker")

          When("two canonical requests sharing ArtScene select their independently qualified configuration")
          val alphastore = ComponentDataStore.resolve(
            params,
            ComponentDataStore.Request.forComponent(alpha)
          )
          val betastore = ComponentDataStore.resolve(
            params,
            ComponentDataStore.Request.forComponent(beta)
          )
          val dedicatedstore = ComponentDataStore.resolve(
            params,
            ComponentDataStore.Request.forComponent(beta, "dedicated")
          )

          val alphacreated = alphastore.create(collection, DataStore.StringEntryId("alpha"), Record.data("value" -> "alpha"))
          val betacreated = betastore.create(collection, DataStore.StringEntryId("beta"), Record.data("value" -> "beta"))
          val dedicatedcreated = dedicatedstore.create(collection, DataStore.StringEntryId("dedicated"), Record.data("value" -> "dedicated"))

          Then("each namespace receives only its canonical directory, full path, and dedicated datastore")
          ComponentDataStore.Request.forComponent(alpha).canonicalComponentSelector shouldBe Some("org.example.alpha.art-scene")
          ComponentDataStore.Request.forComponent(beta).canonicalComponentSelector shouldBe Some("org.example.beta.art-scene")
          alphacreated shouldBe Consequence.unit
          betacreated shouldBe Consequence.unit
          dedicatedcreated shouldBe Consequence.unit
          Files.exists(alphadir.resolve("datastores").resolve("application.db")) shouldBe true
          Files.exists(betapath) shouldBe true
          Files.exists(dedicatedpath) shouldBe true
          alphastore should not be theSameInstanceAs (betastore)
          dedicatedstore should not be theSameInstanceAs (alphastore)
        }
      }
    }

    "E2 reject local binding without a canonical ComponentId and ignore local-ID legacy keys" must _e2 {
      "retain only global policy fallback for a noncanonical compatibility request" in {
        Given("Spec: docs/spec/component-local-datastore-layout.md; Rules: R2,R5,R6; Example: E2")
        _with_temp_directory("component-datastore-legacy") { root =>
          given ExecutionContext = ExecutionContext.create()
          val canonical = ComponentId("org.simplemodeling.textus.ArtScene")
          val legacy = root.resolve("legacy-art-scene.db")
          val canonicalpath = root
            .resolve("org.simplemodeling.textus")
            .resolve("art-scene")
            .resolve("datastores")
            .resolve("application.db")
          val params = _params(Map(
            "textus.local-data.root" -> root.toString,
            "textus.component.org.simplemodeling.textus.art-scene.datastores.application.policy" -> "local-only",
            "textus.component.art-scene.datastores.application.policy" -> "external-default",
            "textus.local-data.art-scene.application.path" -> legacy.toString
          ))
          val noncanonicalparams = _params(Map(
            "textus.component.datastores.application.policy" -> "local-only"
          ))

          When("a canonical request encounters legacy local-ID keys and a compatibility request selects local-only")
          val canonicalstore = ComponentDataStore.resolve(params, ComponentDataStore.Request.forComponent(canonical))
          val canonicalcreated = canonicalstore.create(
            DataStore.CollectionId("marker"),
            DataStore.StringEntryId("canonical"),
            Record.data("value" -> "canonical")
          )
          val rejection = scala.util.Try {
            ComponentDataStore.resolve(
              noncanonicalparams,
              ComponentDataStore.Request("ArtScene")
            )
          }

          Then("canonical binding ignores the local-ID keys and noncanonical local binding fails closed")
          canonicalcreated shouldBe Consequence.unit
          Files.exists(canonicalpath) shouldBe true
          Files.exists(legacy) shouldBe false
          rejection.failed.get shouldBe a[IllegalArgumentException]
        }
      }
    }

    "E3 project a canonical direct ActionCall ComponentId into its local datastore path" must _e3 {
      "use local-only policy without selecting a configured basic datastore" in {
        Given("Spec: docs/spec/component-local-datastore-layout.md; Rules: R1,R2,R5; Example: E3")
        _with_temp_directory("art-scene-direct-datastore") { root =>
          val basicruntimepath = root.resolve("basic-runtime.db")
          given ExecutionContext = ExecutionContext.create()
          val params = _params(Map(
            "textus.local-data.root" -> root.toString,
            "textus.datastore.sqlite.path" -> basicruntimepath.toString,
            "textus.component.org.simplemodeling.textus.art-scene.datastores.application.policy" -> "local-only"
          ))
          summon[ExecutionContext].runtime.setResolvedParameters(params)
          val call = new ArtSceneApplicationDataStoreCall(summon[ExecutionContext])
          val collection = DataStore.CollectionId("marker")
          val markerid = DataStore.StringEntryId("art-scene-marker")
          val marker = Record.data("value" -> "canonical-art-scene")
          val canonicalpath = root
            .resolve("org.simplemodeling.textus")
            .resolve("art-scene")
            .resolve("datastores")
            .resolve("application.db")

          When("the direct action call writes through its canonical application datastore")
          val selected = call.applicationDataStore
          val created = selected.create(collection, markerid, marker)

          Then("the canonical layout holds the marker and basic storage remains unused")
          created shouldBe Consequence.unit
          Files.exists(canonicalpath) shouldBe true
          Files.exists(basicruntimepath) shouldBe false
        }
      }
    }

    "E4 invoke a managed FunctionalActionCall with canonical component metadata" must _e4 {
      "use the managed local-only component datastore without basic fallback" in {
        Given("Spec: docs/spec/component-local-datastore-layout.md; Rules: R1,R2,R5; Example: E4")
        _with_temp_directory("art-scene-managed-datastore") { root =>
          val basicruntimepath = root.resolve("basic-runtime.db")
          given ExecutionContext = ExecutionContext.create()
          val params = _params(Map("textus.local-data.root" -> root.toString))
          summon[ExecutionContext].runtime.setResolvedParameters(params)
          val subsystem = new Subsystem(
            name = "art-scene-managed",
            configuration = _configuration(Map(
              "textus.datastore.sqlite.path" -> basicruntimepath.toString,
              "textus.component.org.simplemodeling.textus.art-scene.datastores.application.policy" -> "local-only"
            ))
          )
          val component = new ArtSceneComponent
          component.initialize(ComponentInit(subsystem, component.core, ComponentOrigin.Builtin))
          subsystem.add(component)
          val call = new ManagedArtSceneApplicationDataStoreCall(
            summon[ExecutionContext],
            component
          )
          val canonicalpath = root
            .resolve("org.simplemodeling.textus")
            .resolve("art-scene")
            .resolve("datastores")
            .resolve("application.db")

          When("the managed FunctionalActionCall invokes the component datastore binding")
          val executed = call.execute()

          Then("the managed binding selects the canonical path and ignores basic storage")
          executed shouldBe Consequence.success(OperationResponse.void)
          Files.exists(canonicalpath) shouldBe true
          Files.exists(basicruntimepath) shouldBe false
        }
      }
    }
  }

  private def _params(values: Map[String, String]): ResolvedParameters =
    ResolvedParameters.fromResolvedConfiguration(_configuration(values))

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue).toMap),
      ConfigurationTrace.empty
    )

  private def _with_temp_directory(name: String)(f: Path => Unit): Unit = {
    val work = Paths.get("target", "cncf-test", "work").toAbsolutePath.normalize
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, name)
    try f(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.sorted(java.util.Comparator.reverseOrder[Path]()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }

  private final class ArtSceneApplicationDataStoreCall(context: ExecutionContext)
    extends ProcedureActionCall
    with ActionCall.Core.Holder {
    private val _component = new ArtSceneComponent
    private val _action = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used in ActionCallComponentDataStoreIdentitySpec")

      override def request: Request =
        Request(
          component = Some("ArtScene"),
          service = None,
          operation = "application_datastore",
          arguments = Nil,
          switches = Nil,
          properties = Nil
        )
    }

    val core: ActionCall.Core = ActionCall.Core(
      action = _action,
      executionContext = context,
      component = Some(_component),
      correlationId = None
    )

    def applicationDataStore: DataStore =
      component_datastore("application")

    def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.void)
  }

  private final class ManagedArtSceneApplicationDataStoreCall(
    context: ExecutionContext,
    target: Component
  ) extends FunctionalActionCall
    with ActionCall.Core.Holder {
    private val _action = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used in ActionCallComponentDataStoreIdentitySpec")

      override def request: Request =
        Request(
          component = Some("ArtScene"),
          service = None,
          operation = "use_component_application_datastore",
          arguments = Nil,
          switches = Nil,
          properties = Nil
        )
    }

    val core: ActionCall.Core = ActionCall.Core(
      action = _action,
      executionContext = context,
      component = Some(target),
      correlationId = None
    )

    protected def build_Program: ExecUowM[OperationResponse] =
      use_component_application_datastore().map(_ => OperationResponse.void)
  }

  private final class ArtSceneComponent extends Component {
    private val _component_id = ComponentId("org.simplemodeling.textus.ArtScene")

    override val core: Component.Core = Component.Core.create(
      name = _component_id.name,
      componentid = _component_id,
      instanceid = ComponentInstanceId.default(_component_id),
      protocol = Protocol.empty
    )

    override def coreOption: Option[Component.Core] = Some(core)

    override def componentDefinitionRecords: Vector[Record] =
      Vector(Record.data("name" -> "ArtScene"))
  }
}
