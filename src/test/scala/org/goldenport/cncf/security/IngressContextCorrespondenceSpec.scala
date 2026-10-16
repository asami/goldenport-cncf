package org.goldenport.cncf.security

import cats.~>
import java.time.Instant
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.*
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.{EntityStore, EntityStoreSpace}
import org.goldenport.cncf.event.EventReception.StandardAttribute
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.SubsystemExecutionProfile
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Property, Request}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
object IngressContextCorrespondenceSpec {
  final case class Sample(major: String, minor: String, millis: Long, entropy: String, entry: Int)
  private val _label = for {
    length <- Gen.chooseNum(0, 10)
    tail <- Gen.listOfN(length, Gen.oneOf('a', 'Z', '0', '9', '_'))
  } yield "a" + tail.mkString
  val samples: Gen[Sample] = for {
    major <- _label
    minor <- _label
    millis <- Gen.chooseNum(0L, 4102444800000L)
    entropy <- _label
    entry <- Gen.chooseNum(0, 3)
  } yield Sample(major, minor, millis, entropy, entry)
  given Shrink[Sample] = Shrink { sample =>
    val major = if (sample.major.length <= 1) Stream.empty else Stream(sample.copy(major = sample.major.take(math.max(1, sample.major.length / 2))))
    val minor = if (sample.minor.length <= 1) Stream.empty else Stream(sample.copy(minor = sample.minor.take(math.max(1, sample.minor.length / 2))))
    val millis = if (sample.millis == 0L) Stream.empty else Stream(sample.copy(millis = sample.millis / 2L))
    val entropy = if (sample.entropy.length <= 1) Stream.empty else Stream(sample.copy(entropy = sample.entropy.take(math.max(1, sample.entropy.length / 2))))
    val entry = if (sample.entry == 0) Stream.empty else Stream(sample.copy(entry = sample.entry / 2))
    major #::: minor #::: millis #::: entropy #::: entry
  }
}

final class IngressContextCorrespondenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import IngressContextCorrespondenceSpec.*
  import IngressContextCorrespondenceSpec.given
  private val _instant = Instant.parse("2026-10-15T09:10:11.123456789Z")
  private val _trace = "tenant-entry-trace-1792055411123-trace_17"
  private val _span = "tenant-scope-span-operation-1792055411124-span_29"
  private val _correlation = "tenant-boundary-correlation-1792055411125-correlation_31"
  private val _job = "tenant-ingress-job-1792055411126-job_37"
  private val _task = "tenant-ingress-task-1792055411127-task_41"
  private val _action = "tenant-ingress-action-1792055411128-action_43"
  private val _parent_job = "tenant-parent-job-1792055411129-parent_47"
  private val _identities = Map(StandardAttribute.TraceId -> _trace, StandardAttribute.SpanId -> _span,
    StandardAttribute.CorrelationId -> _correlation)
  private val _principal = Map("cncf.security.privilege" -> "user", "cncf.security.principalId" -> "owner",
    "cncf.security.authenticated" -> "true")
  private val _attributes = _principal ++ _identities ++ Map(StandardAttribute.JobId -> _job, StandardAttribute.TaskId -> _task,
    StandardAttribute.ActionId -> _action, StandardAttribute.ParentJobId -> _parent_job,
    StandardAttribute.CausationId -> "caused-by-input", StandardAttribute.SagaId -> "saga-from-input", "locale" -> "fr-FR")
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)

  private def _base(): ExecutionContext = {
    val configuration = ResolvedConfiguration(Configuration(Map(
      "textus.execution.profile" -> ConfigurationValue.StringValue("controlled"),
      "textus.execution.key" -> ConfigurationValue.StringValue("ingress-correspondence"),
      "textus.execution.random.seed" -> ConfigurationValue.StringValue("ingress-correspondence-seed"),
      "textus.execution.time.mode" -> ConfigurationValue.StringValue("manual"),
      "textus.execution.time.start-at" -> ConfigurationValue.StringValue(_instant.toString))), ConfigurationTrace.empty)
    val config = RuntimeConfig.default.copy(
      operationMode = OperationMode.Test,
      executionProfile = ExecutionProfileResolver.resolve(configuration, OperationMode.Test).toOption.get,
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace = new EntityStoreSpace().addEntityStore(EntityStore.standard()))
    val observability = ObservabilityContext(
      TraceId("base", "fallback", Some(Instant.EPOCH), Some("base_trace")),
      Some(SpanId("base", "fallback", "operation", Some(Instant.EPOCH), Some("base_span"))),
      Some(CorrelationId("base", "fallback", Some(Instant.EPOCH), Some("base_correlation"))), sagaId = Some("base-saga"))
    val global = GlobalRuntimeContext.create("ingress-correspondence", config, configuration, observability, AliasResolver.empty)
    lazy val context: ExecutionContext = ExecutionContext.withObservabilityContext(ExecutionContext.create(runtime), observability)
    lazy val uow: UnitOfWork = new UnitOfWork(context)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core("ingress-correspondence", Some(global), observability),
      unitofworksupplier = () => uow,
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = new UnitOfWorkInterpreter(uow).interpret(operation)
      }, commitaction = _ => (), abortaction = _ => (), disposeaction = _ => (),
      token = "ingress-correspondence", operationMode = OperationMode.Test)
    context
  }

  private def _request(attributes: Map[String, String]): Request = Request.of(component = "fixture", service = "display", operation = "update",
    properties = attributes.toList.map { case (name, value) => Property(name, value, None) })

  private def _check_context(context: ExecutionContext, trace: String, span: Option[String], correlation: Option[String]): Unit = {
    val active = context.unitOfWork.executionContext
    (active eq context) shouldBe true
    context.observability.traceId.value shouldBe trace
    context.observability.spanId.map(_.value) shouldBe span
    context.observability.correlationId.map(_.value) shouldBe correlation
    (active.clock eq context.clock) shouldBe true
    (active.security eq context.security) shouldBe true
    active.observability shouldBe context.observability
    active.jobContext shouldBe context.jobContext
    active.runtime.context.formatting shouldBe context.runtime.context.formatting
  }

  "Ingress context correspondence" should {
    "every ordinary entry point" which {
      "preserve independent full IDs, restored jobs and the final current UnitOfWork across attributes, Request and ControlledTest ingress" in {
        for (entry <- Vector("attributes", "request", "base-attributes", "base-request", "controlled")) {
          Given(s"$entry ingress with independently declared full trace/span/correlation/job strings, owner and formatting facts")
          val base = _base()
          val request = _request(_attributes)
          When("the actual common resolver restores security, formatting, operational identity and job metadata")
          val result = entry match {
            case "attributes" => IngressSecurityResolver.resolve(_attributes)
            case "request" => IngressSecurityResolver.resolve(request)
            case "base-attributes" => IngressSecurityResolver.resolve(base, _attributes)
            case "base-request" => IngressSecurityResolver.resolve(base, request)
            case _ => IngressSecurityResolver.resolve(SubsystemExecutionProfile.ControlledTest, base, _attributes)
          }
          Then("the returned context is the exact active UnitOfWork context with complete restored values and unchanged security authority")
          result shouldBe a[Consequence.Success[?]]
          val resolved = result.toOption.get
          val context = resolved.executionContext
          _check_context(context, _trace, Some(_span), Some(_correlation))
          context.security.principal.id.value shouldBe "owner"
          context.security.subjectKind shouldBe SubjectKind.User
          resolved.privilege shouldBe SecurityContext.Privilege.User
          resolved.requestedCapabilities shouldBe Set.empty
          SecuritySubject.from(context.security).isAuthenticated shouldBe (entry == "controlled")
          SecuritySubject.from(context.security).isProviderAuthenticated shouldBe false
          context.runtime.context.formatting.locale shouldBe Locale.forLanguageTag("fr-FR")
          context.jobContext.jobId.map(_.value) shouldBe Some(_job)
          context.jobContext.taskId.map(_.value) shouldBe Some(_task)
          context.jobContext.currentTask.map(_.value) shouldBe Some(_task)
          context.jobContext.actionId.map(_.value) shouldBe Some(_action)
          context.jobContext.parentJobId.map(_.value) shouldBe Some(_parent_job)
          context.jobContext.causationId shouldBe Some("caused-by-input")
          context.jobContext.taskStack shouldBe Vector.empty
          context.jobContext.traceMetadata shouldBe Map("traceId" -> _trace, "spanId" -> _span, "correlationId" -> _correlation)
          context.observability.sagaId shouldBe Some("saga-from-input")
          if (entry.startsWith("base-") || entry == "controlled") {
            context.clock.instant() shouldBe _instant
            (context.clock eq base.clock) shouldBe true
            (context.runtime.dataStoreSpace eq base.runtime.dataStoreSpace) shouldBe true
            (context.runtime.entityStoreSpace eq base.runtime.entityStoreSpace) shouldBe true
            context.observability.callTreeContext shouldBe base.observability.callTreeContext
          }
        }
      }

      "retain request argument precedence over properties and canonical-key precedence over legacy observability keys" in {
        Given("distinct complete trace inputs in an argument, canonical property and legacy property on a fresh manual base")
        val base = _base()
        val argumenttrace = "argument-entry-trace-1792055411130-argument_53"
        val legacytrace = "legacy-entry-trace-1792055411131-legacy_59"
        val attributes = _attributes.updated(StandardAttribute.LegacyTraceId, legacytrace)
        val request = _request(attributes).copy(arguments = List(Argument(StandardAttribute.TraceId, argumenttrace)))
        When("the actual request and attribute entry points restore the independently ordered candidates")
        val requested = IngressSecurityResolver.resolve(base, request)
        val canonical = IngressSecurityResolver.resolve(base, attributes)
        Then("argument and canonical precedence retain their complete chosen identity on the final UnitOfWork")
        _check_context(requested.toOption.get.executionContext, argumenttrace, Some(_span), Some(_correlation))
        requested.toOption.get.executionContext.jobContext.traceMetadata("traceId") shouldBe argumenttrace
        _check_context(canonical.toOption.get.executionContext, _trace, Some(_span), Some(_correlation))
      }
    }

    "existing fallback and authority" which {
      "leave the base observability intact when the supplied trace is absent, malformed, wrong-kind or unexpectedly subkinded" in {
        val candidates = Vector(None, Some("malformed"), Some("tenant-entry-job-1792055411123-trace_17"),
          Some("tenant-entry-trace-operation-1792055411123-trace_17"))
        candidates.foreach { trace =>
          Given("a fresh base with independent fallback IDs and otherwise valid supplied span/correlation values")
          val base = _base()
          val attributes = _principal ++ (_identities - StandardAttribute.TraceId) ++ trace.map(StandardAttribute.TraceId -> _)
          When("the real resolver receives no valid normal-shape trace")
          val result = IngressSecurityResolver.resolve(base, attributes)
          Then("restoration uses the original complete base observability and cannot create authentication provenance")
          result shouldBe a[Consequence.Success[?]]
          val context = result.toOption.get.executionContext
          (context.observability eq base.observability) shouldBe true
          _check_context(context, base.observability.traceId.value, base.observability.spanId.map(_.value), base.observability.correlationId.map(_.value))
          SecuritySubject.from(context.security).isAuthenticated shouldBe false
          SecuritySubject.from(context.security).isProviderAuthenticated shouldBe false
        }
      }

      "restore a valid trace while preserving partial span, correlation, saga and calltree fallback rules" in {
        val candidates = Vector(
          Map.empty[String, String],
          Map(StandardAttribute.SpanId -> "malformed", StandardAttribute.CorrelationId -> "malformed"),
          Map(StandardAttribute.SpanId -> "tenant-scope-trace-1792055411124-span_29",
            StandardAttribute.CorrelationId -> "tenant-boundary-job-1792055411125-correlation_31"),
          Map(StandardAttribute.SpanId -> "tenant-scope-span-1792055411124-span_29",
            StandardAttribute.CorrelationId -> "tenant-boundary-correlation-operation-1792055411125-correlation_31"))
        candidates.foreach { partial =>
          Given("a valid full trace plus absent or invalid optional identities on a fresh manual base")
          val base = _base()
          val attributes = _principal ++ partial + (StandardAttribute.TraceId -> _trace)
          When("the common resolver restores the trace and independently admits optional ID shapes")
          val result = IngressSecurityResolver.resolve(base, attributes)
          Then("Span requires its subkind, malformed correlation falls back and existing saga/calltree values survive")
          val context = result.toOption.get.executionContext
          _check_context(context, _trace, None, base.observability.correlationId.map(_.value))
          context.observability.sagaId shouldBe base.observability.sagaId
          context.observability.callTreeContext shouldBe base.observability.callTreeContext
          context.clock.instant() shouldBe _instant
        }
      }

      "deny missing capabilities and provider authentication despite valid operational identities and a client authentication flag" in {
        Given("valid complete IDs and an owner with no required capability or authentication provider")
        val base = _base()
        val attributes = _principal ++ _identities
        When("ordinary and ControlledTest capability gates and the Authenticated profile receive those facts")
        val required = attributes.updated("cncf.security.capability", "required_not_granted")
        val ordinary = IngressSecurityResolver.resolve(base, required)
        val controlled = IngressSecurityResolver.resolve(SubsystemExecutionProfile.ControlledTest, base, required)
        val provider = IngressSecurityResolver.resolve(SubsystemExecutionProfile.Authenticated, base, attributes)
        val untrusted = IngressSecurityResolver.resolve(base, attributes)
        Then("neither IDs nor the flag supply capability or provider authority and ordinary ingress remains unauthenticated")
        ordinary shouldBe a[Consequence.Failure[?]]
        controlled shouldBe a[Consequence.Failure[?]]
        provider shouldBe a[Consequence.Failure[?]]
        ordinary.display should include("required capability")
        controlled.display should include("required capability")
        provider.display should include("authentication provider")
        val context = untrusted.toOption.get.executionContext
        _check_context(context, _trace, Some(_span), Some(_correlation))
        SecuritySubject.from(context.security).isAuthenticated shouldBe false
        SecuritySubject.from(context.security).isProviderAuthenticated shouldBe false
      }
    }

    "total full-ID restoration law" which {
      "retain independent canonical values and exact current-context correspondence for at least one hundred reducing samples" in {
        Given("total bounded labels, epoch milliseconds, entropy and entry choices with canonical reducing shrinkers")
        val property = Prop.forAll(samples) { sample =>
          Given("complete expected ID strings declared solely from the sample and a fresh independent manual runtime")
          val base = _base()
          val trace = s"${sample.major}-${sample.minor}-trace-${sample.millis}-${sample.entropy}"
          val span = s"${sample.major}-${sample.minor}-span-operation-${sample.millis}-${sample.entropy}"
          val correlation = s"${sample.major}-${sample.minor}-correlation-${sample.millis}-${sample.entropy}"
          val attributes = _principal ++ Map(StandardAttribute.TraceId -> trace, StandardAttribute.SpanId -> span,
            StandardAttribute.CorrelationId -> correlation, StandardAttribute.JobId -> _job)
          When("the actual selected ingress entry restores the complete generated IDs")
          val result = sample.entry match {
            case 0 => IngressSecurityResolver.resolve(attributes)
            case 1 => IngressSecurityResolver.resolve(base, attributes)
            case 2 => IngressSecurityResolver.resolve(base, _request(attributes))
            case _ => IngressSecurityResolver.resolve(SubsystemExecutionProfile.ControlledTest, base, attributes)
          }
          Then("the full input identity oracle and current UnitOfWork context agree without capability or provider grants")
          result shouldBe a[Consequence.Success[?]]
          val context = result.toOption.get.executionContext
          _check_context(context, trace, Some(span), Some(correlation))
          context.observability.traceId.parts.timestamp shouldBe Instant.ofEpochMilli(sample.millis)
          context.observability.traceId.parts.entropy shouldBe sample.entropy
          context.jobContext.traceMetadata shouldBe Map("traceId" -> trace, "spanId" -> span, "correlationId" -> correlation)
          context.jobContext.jobId.map(_.value) shouldBe Some(_job)
          context.security.principal.id.value shouldBe "owner"
          SecuritySubject.from(context.security).isAuthenticated shouldBe (sample.entry == 3)
          SecuritySubject.from(context.security).isProviderAuthenticated shouldBe false
          if (sample.entry != 0) {
            context.clock.instant() shouldBe _instant
            (context.clock eq base.clock) shouldBe true
          }
          true
        }
        When("the sequential resolver law runs with a minimum of one hundred successes")
        val result = Test.check(_parameters, property)
        Then("the full-ID ingress correspondence law passes with no discarded inputs")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }
}
