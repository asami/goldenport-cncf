package org.goldenport.cncf.cli

import java.nio.file.{Files, Path, Paths}
import scala.util.Try
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.Behavior
import org.goldenport.cncf.component.{Component, ComponentInstanceId}
import org.goldenport.cncf.config.*
import org.goldenport.cncf.context.{GlobalContext, GlobalRuntimeContext}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod, RuntimeHttpExecutionPlan, RuntimeHttpExecutionPlanCodec}
import org.goldenport.cncf.log.LogBackendHolder
import org.goldenport.cncf.operationalobservation.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter}
import org.goldenport.configuration.{ConfigurationBinding, ConfigurationBindingCandidate, ConfigurationBindingCollection, ConfigurationOrigin, ConfigurationProvenance, ConfigurationValue}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Protocol}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 24, 2026
 * @version Oct. 26, 2026
 */
final class CncfRuntimeOperationalObservationInitializationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OperationalObservationFixtures.*
  private val _key = CncfConfigurationParameterCatalog.SUBSYSTEM_OPERATIONAL_OBSERVATION_PLAN_KEY
  private def _wire(value: ConfigurationValue): String = value match {
    case ConfigurationValue.StringValue(text) => text
    case _ => fail("Expected a typed host configuration string")
  }
  private final class ReadBehavior(val behaviorCore: Behavior.Core) extends Behavior {
    def program: ExecUowM[OperationalObservationSnapshot] = operational_observations()
  }
  private final class Driver extends HttpDriver {
    var calls = Vector.empty[String]
    def get(path: String, headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse = {
      calls = calls :+ path
      HttpResponse.text(HttpStatus.Ok, """{"status":"healthy"}""")
    }
    def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse =
      throw new UnsupportedOperationException("Mutation is outside this fixture")
    def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse =
      throw new UnsupportedOperationException("Mutation is outside this fixture")
  }
  private def _with_runtime(configuration: Json)(body: (CncfRuntime, Path) => Unit): Unit = {
    val previousruntime = GlobalRuntimeContext.current
    val previousglobal = Try(GlobalContext.globalContext).toOption
    val previouslog = LogBackendHolder.backend
    val root = Files.createDirectories(Paths.get("target/cncf/work/observation-bootstrap").toAbsolutePath.normalize)
    val cwd = Files.createTempDirectory(root, "episode-")
    Files.writeString(Files.createDirectories(cwd.resolve(".textus")).resolve("config.json"), configuration.noSpaces)
    val runtime = new CncfRuntime()
    try body(runtime, cwd)
    finally {
      runtime.closeEmbedding()
      GlobalRuntimeContext.current = previousruntime
      previousglobal.foreach(GlobalContext.set)
      previouslog.foreach(LogBackendHolder.install)
    }
  }
  private def _arguments(cwd: Path): Array[String] = {
    val path = cwd.resolve("test.yaml")
    Files.writeString(path,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: operational-observation
        |  time:
        |    mode: manual
        |    start-at: '2026-10-24T00:00:00Z'
        |  random:
        |    mode: seeded
        |    seed: operational-observation
        |  ids:
        |    mode: deterministic
        |  scheduler:
        |    mode: manual
        |  ordering:
        |    mode: deterministic
        |""".stripMargin)
    Array(s"--textus.test.descriptor=$path")
  }
  private def _bindings(target: CncfConfigurationTarget, origin: ConfigurationOrigin, source: String) = (for {
    provenance <- ConfigurationProvenance.create(origin, "host", "fixture", Some("config.json"), Some(_key), 10, 0,
      Vector("observation host binding fixture"), isConfidential = true, sourceType = Some(source))
    candidate <- ConfigurationBindingCandidate.create(CncfConfigurationParameterCatalog.subsystemOperationalObservationPlan,
      target, plan(), provenance)
    binding <- ConfigurationBinding.initial(candidate)
    bindings <- ConfigurationBindingCollection.from[CncfConfigurationTarget](Vector(binding))
  } yield bindings).TAKE

  "Operational observation host bootstrap" should {
    "bind an explicit server plan to actual Component UoW and reuse admitted HTTP execution" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R1,R2,R7; independent observation and HTTP host grants")
      val sources = Vector(http("ops-health"), http("dev-health"), http("observability-health"))
      val targets = Vector(target("ops", true, Map(ObservationAxis.Health -> "ops-health")),
        target("dev", true, Map(ObservationAxis.Health -> "dev-health")),
        ObservationTarget("observability-main", "observability", "main", "runtime", true,
          Map(ObservationAxis.Health -> "observability-health")))
      val selected = plan(targets, sources).copy(profile = "server")
      val httpplan = RuntimeHttpExecutionPlan.createC("https://observation.test/", HttpExecutionLimits(1000, 1000, 65536),
        Map(owner -> HttpExecutionGrant(Vector("https://observation.test"), Set(HttpExecutionMethod.Get)))).TAKE
      val configuration = Json.obj(_key -> Json.fromString(_wire(OperationalObservationPlanCodec.encode(selected).TAKE)),
        CncfConfigurationParameterCatalog.SUBSYSTEM_HTTP_EXECUTION_PLAN_KEY -> Json.fromString(_wire(RuntimeHttpExecutionPlanCodec.encode(httpplan).TAKE)))
      _with_runtime(configuration) { (runtime, cwd) =>
        val driver = new Driver
        var outcome = Option.empty[Consequence[OperationalObservationSnapshot]]
        var inherited = false
        When("public embedding initializes the host and the actual owner's Behavior is interpreted")
        val subsystem = runtime.initializeForEmbedding(cwd, _arguments(cwd), extraComponents = s => {
          val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
          component.withScopeContext(s.globalRuntimeContext)
          val execution = context().withScope(component.scopeContext)
          inherited = execution.cncfCore.scope.operationalObservationRuntimeOption.nonEmpty
          val uow = new UnitOfWork(execution) { override def httpDriver: HttpDriver = driver }
          outcome = Some(new UnitOfWorkInterpreter(uow).run(new ReadBehavior(Behavior.Core(execution, Some(component), None)).program))
          Vector.empty
        }).TAKE
        try {
          Then("ops, dev and observability are acquired independently through HTTP admission without deployment inference")
          inherited shouldBe true
          val result = outcome.get.TAKE
          result.profile shouldBe Some("server")
          result.data.map(_.target.targetId).toSet shouldBe targets.map(_.targetId).toSet
          result.data.foreach { target =>
            target.axes(ObservationAxis.Health).asInstanceOf[AxisObservation.Observed].value shouldBe "healthy"
            target.axes(ObservationAxis.Deployment) shouldBe AxisObservation.Unavailable(ObservationReason.SourceNotConfigured)
          }
          result.healthContracts.map(_.sourceId) shouldBe sources.map(_.sourceId).sorted
          driver.calls shouldBe sources.sortBy(_.sourceId).map(_.url)
        } finally Subsystem.shutdownOwned(subsystem)
      }
    }

    "reject malformed files and CLI-supplied authority before Component construction" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R2,R7; selected confidential host configuration")
      Vector(false, true).foreach { malformed =>
        val wire = _wire(OperationalObservationPlanCodec.encode(plan()).TAKE)
        _with_runtime(if (malformed) Json.obj(_key -> Json.fromString("{private-invalid-wire")) else Json.obj()) { (runtime, cwd) =>
          var constructed = false
          val arguments = _arguments(cwd)
          val selected = if (malformed) arguments else arguments :+ s"--${_key}=${wire}"
          When("public initialization performs configuration preflight")
          val result = runtime.initializeForEmbedding(cwd, selected, extraComponents = _ => { constructed = true; Vector.empty })
          try {
            Then("invalid source or provenance fails without construction or confidential diagnostics")
            result.isFaillure shouldBe true
            constructed shouldBe false
            result.toString should not include "private-invalid-wire"
          } finally result.toOption.foreach(Subsystem.shutdownOwned)
        }
      }
    }

    "admit only selected Subsystem file provenance and preserve confidential catalog targeting" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R2,R7; typed host and foreign binding candidates")
      val selected = SubsystemInstanceId.default("platform").TAKE
      val target = CncfConfigurationTarget.SubsystemInstance.create(selected).TAKE
      val foreign = CncfConfigurationTarget.SubsystemInstance.create(SubsystemInstanceId.default("other").TAKE).TAKE
      val component = CncfConfigurationTarget.ComponentClass.create(owner).TAKE
      When("actual typed bindings are admitted by the selected host")
      val accepted = Vector(ConfigurationOrigin.Home, ConfigurationOrigin.Project, ConfigurationOrigin.Cwd)
        .map(origin => SubsystemOperationalObservationConfiguration.admitC(_bindings(target, origin, "file"), selected))
      val denied = Vector(_bindings(foreign, ConfigurationOrigin.Cwd, "file"), _bindings(component, ConfigurationOrigin.Cwd, "file"),
        _bindings(target, ConfigurationOrigin.Arguments, "arguments"), _bindings(target, ConfigurationOrigin.Environment, "environment"))
        .map(SubsystemOperationalObservationConfiguration.admitC(_, selected))
      val definition = CncfConfigurationParameterCatalog.closed.schema.definition(_key).TAKE
      Then("only exact file provenance is admitted, with no alias or Component targeting")
      accepted.foreach(_.TAKE.nonEmpty shouldBe true)
      denied.foreach(_.isFaillure shouldBe true)
      definition.decodeOnlyAliases shouldBe empty
      definition.allowedTargetKinds shouldBe Set(CncfConfigurationTargetKind.SubsystemInstance)
    }
  }
}
