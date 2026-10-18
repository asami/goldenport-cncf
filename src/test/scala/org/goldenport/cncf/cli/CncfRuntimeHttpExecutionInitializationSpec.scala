package org.goldenport.cncf.cli

import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.action.{Behavior, BehaviorHttpPart}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationTarget, SubsystemHttpExecutionConfiguration, SubsystemInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, GlobalRuntimeContext, ScopeKind}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod, RuntimeHttpExecutionPlan, RuntimeHttpExecutionPlanCodec}
import org.goldenport.cncf.log.LogBackendHolder
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter}
import org.goldenport.configuration.{ConfigurationBinding, ConfigurationBindingCandidate, ConfigurationBindingCollection, ConfigurationOrigin, ConfigurationProvenance, ConfigurationValue}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Protocol}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeHttpExecutionInitializationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _owner = ComponentId("org.example.RuntimeHttpOwner")
  private val _other = ComponentId("org.example.RuntimeHttpOther")
  private val _key = CncfConfigurationParameterCatalog.SUBSYSTEM_HTTP_EXECUTION_PLAN_KEY
  private def _take[A](value: Consequence[A]): A = value.getOrElse(fail("HTTP bootstrap fixture failed"))
  private val _plan = _take(RuntimeHttpExecutionPlan.createC("https://example.test/api/", HttpExecutionLimits(1000, 2000, 4096),
    Map(_owner -> HttpExecutionGrant(Vector("https://example.test"), Set(HttpExecutionMethod.Get)))))
  private val _text = _take(RuntimeHttpExecutionPlanCodec.encode(_plan)) match {
    case ConfigurationValue.StringValue(value) => value
    case _ => fail("HTTP host codec did not encode a string")
  }

  private final class HttpBehavior(val behaviorCore: Behavior.Core) extends Behavior with BehaviorHttpPart {
    def program(): ExecUowM[HttpResponse] = http_get("selected?q=%2B", Map("X-Workflow" -> "original"))
  }

  private final class CountedDriver extends HttpDriver {
    val response: HttpResponse = HttpResponse.Text(HttpStatus.Ok,
      ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)), Bag.text("runtime-response", StandardCharsets.UTF_8))
    var calls: Vector[(String, Map[String, String], Vector[Property])] = Vector.empty
    def get(path: String, headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse = {
      calls = calls :+ ((path, headers, properties))
      response
    }
    def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse =
      throw new UnsupportedOperationException("POST is outside this fixture")
    def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse =
      throw new UnsupportedOperationException("PUT is outside this fixture")
  }

  private def _with_runtime(configured: Boolean)(body: (CncfRuntime, Path) => Unit): Unit = {
    val previousruntime = GlobalRuntimeContext.current
    val previousglobal = Try(GlobalContext.globalContext).toOption
    val previouslog = LogBackendHolder.backend
    val workroot = Files.createDirectories(Paths.get("target/cncf/work/runtime-http-bootstrap").toAbsolutePath.normalize)
    val cwd = Files.createTempDirectory(workroot, "episode-")
    if (configured) Files.writeString(Files.createDirectories(cwd.resolve(".textus")).resolve("config.json"),
      Json.obj(_key -> Json.fromString(_text)).noSpaces)
    val runtime = new CncfRuntime()
    try body(runtime, cwd)
    finally {
      runtime.closeEmbedding()
      GlobalRuntimeContext.current = previousruntime
      previousglobal.foreach(GlobalContext.set)
      previouslog.foreach(LogBackendHolder.install)
    }
  }

  private def _controlled_arguments(cwd: Path): Array[String] = {
    val descriptor = cwd.resolve("test.yaml")
    Files.writeString(descriptor,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: http-runtime
        |  time:
        |    mode: manual
        |    start-at: '2026-10-16T00:00:00Z'
        |  random:
        |    mode: seeded
        |    seed: http-runtime
        |  ids:
        |    mode: deterministic
        |  scheduler:
        |    mode: manual
        |  ordering:
        |    mode: deterministic
        |""".stripMargin)
    Array(s"--textus.test.descriptor=$descriptor")
  }

  private def _bindings(
    target: CncfConfigurationTarget,
    origin: ConfigurationOrigin,
    sourcetype: String
  ): ConfigurationBindingCollection[CncfConfigurationTarget] = _take(for {
    provenance <- ConfigurationProvenance.create(origin, "host", "test-owned-source", Some("config.json"), Some(_key),
      10, 0, Vector("HTTP host binding fixture"), isConfidential = true, sourceType = Some(sourcetype))
    candidate <- ConfigurationBindingCandidate.create(CncfConfigurationParameterCatalog.subsystemHttpExecutionPlan,
      target, _plan, provenance)
    binding <- ConfigurationBinding.initial(candidate)
    bindings <- ConfigurationBindingCollection.from[CncfConfigurationTarget](Vector(binding))
  } yield bindings)

  "Canonical HTTP host bootstrap" should {
    "inherit the selected file admission into actual Components and return the normal Behavior HTTP response only for its owner" in {
      Given("a selected .textus/config.json HTTP plan and two actual Components sharing the same display label")
      _with_runtime(configured = true) { (runtime, cwd) =>
        val driver = new CountedDriver
        val acquisitions = new AtomicInteger
        var outcomes = Vector.empty[Consequence[HttpResponse]]
        var inherited = false
        var scopes = Vector.empty[String]
        When("public embedding initialization assembles the host and the owning UoW runs the normal Behavior HTTP DSL")
        val subsystem = _take(runtime.initializeForEmbedding(cwd, _controlled_arguments(cwd), extraComponents = s => {
          val root = s.globalRuntimeContext
          val components = Vector(_owner, _other).map { id =>
            val component = Component.create(id.name, id, ComponentInstanceId.default(id), Protocol.empty)
            component.withScopeContext(root)
            component
          }
          outcomes = components.map { component =>
            val scope = org.goldenport.cncf.component.Component.Context("same-display-label", root, component,
              org.goldenport.cncf.component.ComponentOrigin.Embed).createChildScope(ScopeKind.Action, "fetch")
            inherited = inherited || scope.httpExecutionAdmissionOption.exists(value => root.httpExecutionAdmissionOption.contains(value))
            scopes = scopes :+ scope.parent.get.name
            val context = ExecutionContext.create().withScope(scope)
            val uow = new UnitOfWork(context) {
              override def httpDriver: HttpDriver = { acquisitions.incrementAndGet(); driver }
            }
            new UnitOfWorkInterpreter(uow).run(new HttpBehavior(Behavior.Core(context, Some(component), None)).program())
          }
          Vector.empty
        }))
        try {
          Then("the owner receives the original response, the same-label foreign owner is denied and the driver is acquired once")
          inherited shouldBe true
          scopes shouldBe Vector("same-display-label", "same-display-label")
          _take(outcomes.head) should be theSameInstanceAs driver.response
          outcomes(1).isFaillure shouldBe true
          acquisitions.get() shouldBe 1
          driver.calls.size shouldBe 1
          driver.calls.head._1 shouldBe "https://example.test/api/selected?q=%2B"
          driver.calls.head._2 shouldBe Map("X-Workflow" -> "original")
          driver.calls.head._3 shouldBe Vector(Property("http.connect-timeout-ms", "1000", None),
            Property("http.read-timeout-ms", "2000", None), Property("http.max-response-bytes", "4096", None),
            Property("http.follow-redirects", "false", None))
        } finally Subsystem.shutdownOwned(subsystem)
      }
    }

    "deny an ordinary unconfigured runtime before acquiring a driver" in {
      Given("a controlled public runtime with no HTTP host plan")
      _with_runtime(configured = false) { (runtime, cwd) =>
        val acquisitions = new AtomicInteger
        val driver = new CountedDriver
        var outcome = Option.empty[Consequence[HttpResponse]]
        When("the actual owner builds and interprets its ordinary Behavior HTTP program")
        val subsystem = _take(runtime.initializeForEmbedding(cwd, _controlled_arguments(cwd), extraComponents = s => {
          val component = Component.create(_owner.name, _owner, ComponentInstanceId.default(_owner), Protocol.empty)
          component.withScopeContext(s.globalRuntimeContext)
          val context = ExecutionContext.create().withScope(component.scopeContext)
          val uow = new UnitOfWork(context) {
            override def httpDriver: HttpDriver = { acquisitions.incrementAndGet(); driver }
          }
          outcome = Some(new UnitOfWorkInterpreter(uow).run(new HttpBehavior(Behavior.Core(context, Some(component), None)).program()))
          Vector.empty
        }))
        try {
          Then("missing host authority denies the operation without any transport effect")
          subsystem.globalRuntimeContext.httpExecutionAdmissionOption shouldBe None
          outcome.exists(_.isFaillure) shouldBe true
          acquisitions.get() shouldBe 0
          driver.calls shouldBe empty
        } finally Subsystem.shutdownOwned(subsystem)
      }
    }

    "refuse CLI authority and malformed selected host files before Component construction" in {
      Vector(false, true).foreach { malformed =>
        Given(s"a valid plan supplied by CLI or a malformed selected host wire, malformed=$malformed")
        _with_runtime(configured = false) { (runtime, cwd) =>
          var constructed = false
          val arguments = _controlled_arguments(cwd)
          if (malformed) Files.writeString(Files.createDirectories(cwd.resolve(".textus")).resolve("config.json"),
            Json.obj(_key -> Json.fromString("{invalid-confidential-http-wire")).noSpaces)
          val selectedarguments = if (malformed) arguments else arguments :+ s"--${_key}=${_text}"
          When("public embedding initialization preflights its selected configuration sources")
          val result = runtime.initializeForEmbedding(cwd, selectedarguments,
            extraComponents = _ => { constructed = true; Vector.empty })
          try {
            Then("the initializer refuses without constructing Components or exposing confidential wire data")
            result.isFaillure shouldBe true
            constructed shouldBe false
            result.toString should not include "invalid-confidential-http-wire"
          } finally result.toOption.foreach(Subsystem.shutdownOwned)
        }
      }
    }

    "admit only selected Subsystem host file provenance using actual typed bindings" in {
      Given("typed selected, foreign Subsystem and Component bindings plus non-file provenance")
      val selected = _take(SubsystemInstanceId.default("platform"))
      val target = _take(CncfConfigurationTarget.SubsystemInstance.create(selected))
      val foreign = _take(CncfConfigurationTarget.SubsystemInstance.create(_take(SubsystemInstanceId.default("other"))))
      val component = _take(CncfConfigurationTarget.ComponentClass.create(_owner))
      val accepted = Vector(ConfigurationOrigin.Home, ConfigurationOrigin.Project, ConfigurationOrigin.Cwd)
        .map(origin => _bindings(target, origin, "file"))
      val rejected = Vector(_bindings(foreign, ConfigurationOrigin.Cwd, "file"),
        _bindings(component, ConfigurationOrigin.Cwd, "file"), _bindings(target, ConfigurationOrigin.Cwd, "environment"),
        _bindings(target, ConfigurationOrigin.Arguments, "arguments"),
        _bindings(target, ConfigurationOrigin.Environment, "environment"))
      When("the owning Subsystem preflight admits each real typed binding collection")
      val admitted = accepted.map(bindings => SubsystemHttpExecutionConfiguration.admitC(bindings, selected))
      val denied = rejected.map(bindings => SubsystemHttpExecutionConfiguration.admitC(bindings, selected))
      val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(_key))
      Then("only selected Home, Project or Cwd file provenance yields the plan and catalog targeting remains exact")
      admitted.foreach(result => _take(result).map(_.admission) shouldBe Some(_plan.admission))
      denied.foreach(_.isFaillure shouldBe true)
      definition.decodeOnlyAliases shouldBe empty
      definition.allowedTargetKinds shouldBe Set(org.goldenport.cncf.config.CncfConfigurationTargetKind.SubsystemInstance)
      definition.input(_key, component, ConfigurationValue.StringValue(_text), "component.config").isFaillure shouldBe true
    }
  }
}
