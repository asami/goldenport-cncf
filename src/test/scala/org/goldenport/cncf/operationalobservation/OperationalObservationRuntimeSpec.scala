package org.goldenport.cncf.operationalobservation

import java.time.Instant
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, SecurityContext, SessionContext}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
private[cncf] object OperationalObservationFixtures {
  val owner = ComponentId("org.example.ObservationOwner")
  def target(id: String, enabled: Boolean = false, sources: Map[ObservationAxis, String] = Map.empty): ObservationTarget =
    ObservationTarget("postgresql-" + id, "postgresql", id, "infrastructure", enabled, sources)
  def http(id: String, axis: ObservationAxis = ObservationAxis.Health): ObservationSource.Http =
    ObservationSource.Http(id, "https://observation.test/" + id, axis, "component",
      if (axis == ObservationAxis.Readiness) Vector("ready", "not-ready")
      else if (axis == ObservationAxis.Reachability) Vector("reachable", "unreachable")
      else Vector("healthy", "unhealthy"), "current", 30000)
  def plan(
    targets: Vector[ObservationTarget] = Vector(target("ops"), target("dev")),
    sources: Vector[ObservationSource] = Vector.empty
  ): OperationalObservationPlan = OperationalObservationPlan("client", Vector(owner), ObservationLimits(), sources, targets)
  def context(
    runtime: Option[OperationalObservationRuntime] = None,
    privilege: SecurityContext.Privilege = SecurityContext.Privilege.System,
    authenticated: Boolean = true,
    selectedOwner: ComponentId = owner
  ): ExecutionContext = {
    val initial = ExecutionContext.create(privilege)
    val base = if (authenticated) ExecutionContext.withSecurityContext(initial,
      initial.security.copy(session = Some(SessionContext(authenticatedAt = Some(Instant.now()))))) else initial
    val root = ScopeContext.Instance(base.cncfCore.scope.core.copy(operationalObservationRuntimeOption = runtime))
    val component = Component.create(selectedOwner.name, selectedOwner, ComponentInstanceId.default(selectedOwner), Protocol.empty)
    base.withScope(Component.Context("observation", root, component, ComponentOrigin.Embed))
  }
  def runtime(value: OperationalObservationPlan): OperationalObservationRuntime = OperationalObservationRuntime.createC(value).TAKE
  final class Effects(body: String = """{"status":"healthy"}""") extends (UnitOfWorkOp ~> Consequence) {
    var paths = Vector.empty[String]
    var failure = false
    def apply[A](op: UnitOfWorkOp[A]): Consequence[A] = op match {
      case UnitOfWorkOp.HttpGet(path, _, _) =>
        paths = paths :+ path
        if (failure) Consequence.serviceUnavailable("secret provider diagnostic")
        else Consequence.success(HttpResponse.text(HttpStatus.Ok, body))
      case _ => Consequence.operationInvalid("Unexpected fixture effect")
    }
  }
}

final class OperationalObservationRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OperationalObservationFixtures.*
  "Runtime-bound operational reads" should {
    "deny anonymous, registration-only and foreign Component reads before any effect" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R1,R2,R7; a configured enabled source")
      val value = runtime(plan(Vector(target("ops", true, Map(ObservationAxis.Health -> "health"))), Vector(http("health"))))
      val effects = new Effects
      val contexts = Vector(context(Some(value), SecurityContext.Privilege.Anonymous, false),
        context(Some(value), SecurityContext.Privilege.User),
        context(Some(value), selectedOwner = ComponentId("org.example.Other")))
      When("each unprivileged or foreign invocation requests observations")
      val outcomes = contexts.map(value.readC(50, _, effects))
      Then("the operation rejects before provider acquisition")
      outcomes.foreach(_.isFaillure shouldBe true)
      effects.paths shouldBe empty
    }
    "keep absent configuration distinct from a disabled explicit profile with zero reads" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R2,R4; absent and disabled configuration")
      val effects = new Effects
      When("both are read through their typed runtime capability")
      val absent = OperationalObservationRuntime.unconfigured.readC(50, context(), effects).TAKE
      val configured = runtime(plan()).readC(50, context(), effects).TAKE
      Then("only the explicit profile has independent ops/dev inventory")
      absent.profile shouldBe None
      absent.total shouldBe 0
      configured.profile shouldBe Some("client")
      configured.data.map(_.target.instance) shouldBe Vector("dev", "ops")
      configured.data.flatMap(_.axes.values).forall(_ == AxisObservation.NotApplicable) shouldBe true
      configured.healthContracts shouldBe empty
      io.circe.parser.parse(configured.json).toOption.get.hcursor.downField("configuration")
        .get[Vector[io.circe.Json]]("healthContracts").toOption shouldBe Some(Vector.empty)
      effects.paths shouldBe empty
    }
    "sort and limit before acquiring sources, independently of inventory permutation" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R6,R7; independently selected targets")
      val property = Prop.forAll(Gen.oneOf(true, false)) { reversed =>
        val targets = Vector(target("ops", true, Map(ObservationAxis.Health -> "ops")),
          target("dev", true, Map(ObservationAxis.Health -> "dev")))
        val value = runtime(plan(if (reversed) targets.reverse else targets, Vector(http("ops"), http("dev"))))
        val effects = new Effects
        val result = value.readC(1, context(Some(value)), effects).TAKE
        result.total == 2 && result.data.map(_.target.instance) == Vector("dev") &&
          effects.paths == Vector("https://observation.test/dev") &&
          result.data.head.axes(ObservationAxis.Deployment) == AxisObservation.Unavailable(ObservationReason.SourceNotConfigured)
      }
      When("one result is requested for either permutation")
      Then("only dev is acquired, with all configured targets retained in total")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property).passed shouldBe true
    }
    "isolate source failure and stop acquisition for cancelled or exhausted work" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R3,R7; one failing selected source")
      val value = runtime(plan(Vector(target("ops", true, Map(ObservationAxis.Health -> "health"))), Vector(http("health"))))
      val effects = new Effects
      effects.failure = true
      When("the source fails without exposing its diagnostic")
      val result = value.readC(50, context(Some(value)), effects).TAKE
      Then("the health axis is unavailable and other axes retain their own reasons")
      result.data.head.axes(ObservationAxis.Health) shouldBe AxisObservation.Unavailable(ObservationReason.SourceUnavailable, Some("health"))
      result.json should not include "secret"
      val cancellation = new org.goldenport.cncf.job.JobCancellationScope
      cancellation.cancel()
      val cancelled = ExecutionContext.withJobContext(context(Some(value)),
        org.goldenport.cncf.job.JobContext.empty.copy(cancellationScope = Some(cancellation)))
      val untouched = new Effects
      value.readC(50, cancelled, untouched).TAKE.data.head.axes(ObservationAxis.Health) shouldBe
        AxisObservation.Unavailable(ObservationReason.BudgetExhausted, Some("health"))
      untouched.paths shouldBe empty
    }
    "reject invalid limits, nonexistent associations and oversized business output" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R1,R2,R6; bounded read requests")
      val effects = new Effects
      val value = runtime(plan())
      When("invalid inputs are evaluated")
      Then("they fail explicitly, without observations or fabricated runtime identities")
      Vector(0, -1, 101, Int.MaxValue).foreach(limit => value.readC(limit, context(), effects).isFaillure shouldBe true)
      val association = ComponentInstanceId.createC(owner, "not-assembled").TAKE
      runtime(plan(Vector(target("ops").copy(runtimeAssociation = Some(association)))))
        .readC(50, context(), effects).isFaillure shouldBe true
      runtime(plan().copy(limits = ObservationLimits(responseBytes = 1))).readC(50, context(), effects).isFaillure shouldBe true
      effects.paths shouldBe empty
    }

    "publish only selected enabled health contracts from the same host Plan" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R6,R8; shared, disabled and unreturned health sources")
      val health = http("health").copy(vocabulary = Vector("unhealthy", "healthy"))
      val unreturned = http("unreturned")
      val disabled = http("disabled")
      val targets = Vector(target("a", true, Map(ObservationAxis.Health -> "health")),
        target("b", true, Map(ObservationAxis.Health -> "health")),
        target("c", false, Map(ObservationAxis.Health -> "disabled")),
        target("z", true, Map(ObservationAxis.Health -> "unreturned")))
      val value = runtime(plan(targets, Vector(unreturned, health, disabled)))
      val effects = new Effects
      effects.failure = true
      When("a bounded result returns a shared failed source and a disabled target")
      val result = value.readC(3, context(Some(value)), effects).TAKE
      val json = result.json
      Then("one sorted public declaration remains available even though its observation is unavailable")
      result.healthContracts shouldBe Vector(HealthObservationContract("health", "component", Vector("healthy", "unhealthy")))
      result.total shouldBe 4
      effects.paths shouldBe Vector(health.url)
      json should not include "https://"
      json should not include "disabled"
      json should not include "unreturned"
      json should include ("healthContracts")
      json should include ("sourceId")
    }

    "require the observation algebra to remain an external UoW planning boundary" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R1,R7; the existing UoW planner")
      When("the observation instruction is classified")
      Then("it cannot be merged into a local atomic segment")
      org.goldenport.cncf.unitofwork.UnitOfWorkOperationClassifier.classify(UnitOfWorkOp.ReadOperationalObservations()) shouldBe
        org.goldenport.cncf.unitofwork.UnitOfWorkEffectClass.External
    }
  }
}
