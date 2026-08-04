package org.goldenport.cncf.operation.evaluation

import cats.data.NonEmptyVector
import java.util.concurrent.CopyOnWriteArrayList
import scala.jdk.CollectionConverters.*

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{
  Component,
  ComponentId,
  ComponentInit,
  ComponentInstanceId,
  ComponentOrigin
}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.job.{ActionId, ActionTask}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.spi.evaluation.{
  CorpusEvaluationSinkSocket,
  DeterministicCorpusEvaluationSink
}
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, Subsystem}
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.http.{HttpPath, HttpRequest}
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.record.Record
import org.scalatest.BeforeAndAfterEach
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for declared operation-evaluation admission.
 *
 * @since   Jul. 23, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationEvaluationAdmissionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterEach {
  private var _subsystems = Vector.empty[Subsystem]
  private val _e1_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E1, rules:R1, phase:48")
  private val _e2_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E2, rules:R2,R3,R8, phase:48")
  private val _e3_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E3, rules:R4,R8, phase:48")
  private val _e4_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E4, rules:R5,R6, phase:48")
  private val _e5_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E5, rules:R7, phase:48")
  private val _e6_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E6, rules:R9, phase:48")
  private val _e7_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E7, rules:R7,R8, phase:48")
  private val _e8_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E8, rules:R3,R8, phase:48")
  private val _e9_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E9, rules:R2,R6, phase:48")
  private val _e10_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E10, rules:R2,R6, phase:48")
  private val _e11_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E11, rules:R5,R7, phase:48")
  private val _e12_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E12, rules:R5,R7, phase:48")
  private val _e13_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E13, rules:R6, phase:48")
  private val _e14_metadata =
    afterWord("in spec:operation-evaluation-capture, example:E14, rules:R3, phase:48")

  override protected def afterEach(): Unit =
    try {
      _subsystems.foreach(_.shutdown())
      _subsystems = Vector.empty
    } finally
      super.afterEach()

  "Declared operation evaluation admission" should {
    "separate control and admitted execution" which {
      "E1 leave undeclared operations outside resolver admission while retaining automatic facts" must _e1_metadata {
        "when the authorized operation executes" in {
          Given("an operation without evaluation metadata and an installed resolver and sink")
          val resolver = DeterministicOperationEvaluationResolver.fixed(
            OperationEvaluationAdmission.unavailable()
          )
          val fixture = _fixture(resolver, None)

          When("the authorized operation executes")
          val result = fixture.subsystem.executeOperationResponse(_request)

          Then("the resolver is not called and the ordinary automatic attempt remains complete")
          result shouldBe Consequence.success(OperationResponse.Scalar("control"))
          resolver.requests shouldBe empty
          fixture.events.asScala.toVector shouldBe Vector("request", "business:control")
          fixture.sink.facts.map(_.factKind.token) shouldBe Vector(
            "operation-start",
            "operation-terminal"
          )
          fixture.sink.facts.foreach { fact =>
            fact.correlation.corpus shouldBe None
            fact.correlation.experiment shouldBe None
          }
        }
      }

      "E2 continue through the control branch when optional admission is unavailable" must _e2_metadata {
        "when optional admission is unavailable" in {
          Given("an optionally admitted Corpus declaration and the disabled resolver result")
          val events = new CopyOnWriteArrayList[String]()
          val resolver = DeterministicOperationEvaluationResolver { _ =>
            events.add("resolver")
            Consequence.success(OperationEvaluationAdmission.unavailable())
          }
          val fixture = _fixture(
            resolver,
            Some(_corpus_declaration(EvaluationAdmissionRequirement.Optional)),
            events
          )

          When("the operation crosses admission")
          val result = _success(fixture.subsystem.executeWithMetadata(_request))

          Then("admission precedes request construction and records one bounded limitation")
          result.response shouldBe OperationResponse.Scalar("control")
          resolver.requests should have size 1
          fixture.events.asScala.toVector shouldBe Vector("resolver", "request", "business:control")
          val report =
            result.metadata.operationEvaluation.getOrElse(fail("evaluation report missing"))
          report.admissions.map(_.status) shouldBe Vector(
            OperationEvaluationAdmissionStatus.Unavailable
          )
          report.admissions.flatMap(_.limitations).map(_.kind) shouldBe
            Vector(OperationEvaluationLimitationKind.Unavailable)
          fixture.sink.facts.map(_.factKind.token) shouldBe Vector(
            "operation-start",
            "operation-terminal"
          )
        }
      }

      "E3 reject required unavailable admission before request construction and business execution" must _e3_metadata {
        "when required admission is unavailable" in {
          Given("a required Experiment declaration with no available assignment")
          val resolver = DeterministicOperationEvaluationResolver.fixed(
            OperationEvaluationAdmission.unavailable()
          )
          val fixture = _fixture(
            resolver,
            Some(_experiment_declaration(EvaluationAdmissionRequirement.Required))
          )

          When("the operation reaches the declared admission boundary")
          val result = _success(fixture.subsystem.executeWithMetadata(_request, _http_request))

          Then("the structured failure retains one automatic attempt and no business code runs")
          result.response shouldBe a[OperationResponse.Http]
          val report =
            result.metadata.operationEvaluation.getOrElse(fail("evaluation report missing"))
          report.admissions.map(_.status) shouldBe Vector(
            OperationEvaluationAdmissionStatus.Rejected
          )
          report.aggregateStatus shouldBe Some(OperationEvaluationDeliveryStatus.Failed)
          resolver.requests should have size 1
          fixture.events.asScala.toVector shouldBe empty
          fixture.sink.facts.map(_.factKind.token) shouldBe Vector(
            "operation-start",
            "operation-terminal"
          )
          fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe
            OperationEvaluationOutcome.Failure
        }
      }

      "E4 bind one immutable admitted assignment before application execution" must _e4_metadata {
        "when the resolver supplies one complete assignment" in {
          Given("a required Experiment declaration and a deterministic offline assignment")
          val revision = _success(CorpusRevisionReference.parseC("revision-9"))
          val experiment = _success(ExperimentEvaluationCorrelation.createC(
            _success(ExperimentReference.parseC("experiment-17")),
            Some(_success(ExperimentArmReference.parseC("arm-b"))),
            Some(_success(ExperimentRunReference.parseC("run-4"))),
            Some(revision)
          ))
          val assignment = OperationEvaluationAssignment(
            _success(OperationEvaluationName.parseC("variant-b")),
            Some(_success(OperationEvaluationText.parseC("execution-plan-b")))
          )
          val events = new CopyOnWriteArrayList[String]()
          val resolver = DeterministicOperationEvaluationResolver { _ =>
            events.add("resolver")
            OperationEvaluationAdmission.admittedC(
              experiment = Some(experiment),
              assignment = Some(assignment)
            )
          }
          val fixture = _fixture(
            resolver,
            Some(_experiment_declaration(EvaluationAdmissionRequirement.Required)),
            events
          )

          When("the operation executes under the admitted context")
          val result = _success(fixture.subsystem.executeWithMetadata(_request))

          Then(
            "resolver ordering, protected access, and automatic correlation retain the assignment"
          )
          result.response shouldBe OperationResponse.Scalar("variant-b:execution-plan-b")
          resolver.requests should have size 1
          fixture.events.asScala.toVector shouldBe Vector(
            "resolver",
            "request",
            "business:variant-b:execution-plan-b"
          )
          val report =
            result.metadata.operationEvaluation.getOrElse(fail("evaluation report missing"))
          report.admissions.map(_.status) shouldBe Vector(
            OperationEvaluationAdmissionStatus.Admitted
          )
          fixture.sink.facts.map(_.correlation.experiment).distinct shouldBe Vector(
            Some(experiment)
          )
          fixture.sink.facts.map(_.factKind.token) shouldBe Vector(
            "operation-start",
            "operation-terminal"
          )
        }
      }

      "E14 keep the disabled resolver on the bounded control path" must _e14_metadata {
        "when no evaluation resolver provider is installed" in {
          Given("an optional Corpus declaration and the framework disabled resolver")
          val fixture = _fixture(
            OperationEvaluationResolver.disabled,
            Some(_corpus_declaration(EvaluationAdmissionRequirement.Optional))
          )

          When("the declared operation crosses evaluation admission")
          val result = _success(fixture.subsystem.executeWithMetadata(_request))

          Then("the control branch runs and records only bounded unavailability")
          result.response shouldBe OperationResponse.Scalar("control")
          fixture.events.asScala.toVector shouldBe Vector("request", "business:control")
          val report =
            result.metadata.operationEvaluation.getOrElse(fail("evaluation report missing"))
          report.admissions.map(_.status) shouldBe Vector(
            OperationEvaluationAdmissionStatus.Unavailable
          )
          report.admissions.flatMap(_.limitations).map(_.kind) shouldBe
            Vector(OperationEvaluationLimitationKind.Unavailable)
        }
      }

    }

    "reject invalid admission without weakening canonical execution" which {
      "E5 preserve resolver failures instead of silently selecting control" must _e5_metadata {
        "when the resolver returns a structured failure" in {
          Given("an optional declaration whose resolver rejects an unknown assignment")
          val resolver = DeterministicOperationEvaluationResolver.failing(
            Conclusion.simple("unknown admitted variant")
          )
          val fixture = _fixture(
            resolver,
            Some(_experiment_declaration(EvaluationAdmissionRequirement.Optional))
          )

          When("the resolver rejects the declared evaluation")
          val result = fixture.subsystem.executeOperationResponse(_request)

          Then("the canonical failure is returned before application code and is captured once")
          result shouldBe a[Consequence.Failure[_]]
          result match {
            case Consequence.Failure(conclusion) =>
              conclusion.display should include("unknown admitted variant")
            case _ =>
              fail("resolver failure missing")
          }
          fixture.events.asScala.toVector shouldBe empty
          fixture.sink.facts.map(_.factKind.token) shouldBe Vector(
            "operation-start",
            "operation-terminal"
          )
        }
      }

      "E6 authorize before invoking the external admission resolver" must _e6_metadata {
        "when operation authorization denies the request" in {
          Given("a declared operation denied by subsystem authorization policy")
          val resolver = DeterministicOperationEvaluationResolver.fixed(
            OperationEvaluationAdmission.unavailable()
          )
          val fixture =
            _fixture(resolver, Some(_corpus_declaration(EvaluationAdmissionRequirement.Optional)))
          fixture.subsystem.withDescriptor(GenericSubsystemDescriptor(
            path = java.nio.file.Path.of("<operation-evaluation-admission-auth>"),
            subsystemName = "operation-evaluation-admission-auth",
            operationAuthorization = Map(
              "evaluation_admission.operation.evaluate" -> OperationAuthorizationRule(deny = true)
            )
          ))

          When("the anonymous invocation is denied")
          val result = fixture.subsystem.executeOperationResponse(_request)

          Then("neither resolver nor business nor evaluation sink observes the denied request")
          result shouldBe a[Consequence.Failure[_]]
          resolver.requests shouldBe empty
          fixture.events.asScala.toVector shouldBe empty
          fixture.sink.facts shouldBe empty
        }
      }

      "E7 normalize thrown resolver exceptions without losing automatic terminal capture" must _e7_metadata {
        "when the resolver throws across the runtime capability boundary" in {
          Given("an optional declaration and a resolver implementation that throws")
          val resolver = DeterministicOperationEvaluationResolver { _ =>
            throw new IllegalStateException("planned resolver exception")
          }
          val fixture = _fixture(
            resolver,
            Some(_experiment_declaration(EvaluationAdmissionRequirement.Optional))
          )

          When("the operation crosses the resolver boundary")
          val result = fixture.subsystem.executeOperationResponse(_request)

          Then(
            "the exception becomes a structured failure and exactly one automatic attempt is retained"
          )
          result shouldBe a[Consequence.Failure[_]]
          fixture.events.asScala.toVector shouldBe empty
          fixture.sink.facts.map(_.factKind.token) shouldBe
            Vector("operation-start", "operation-terminal")
        }
      }

      "E8 project resolver limitations without retaining diagnostic payloads" must _e8_metadata {
        "when optional unavailability carries an application-owned diagnostic history" in {
          Given("a resolver limitation containing values that execution metadata must not retain")
          val secret = "private-admission-payload"
          val diagnostic = ConclusionDiagnostics.unknown.copy(
            diagnosticKey = "argument",
            interpretation = secret,
            reason = Some(secret),
            previous = Vector(Record.data("secret" -> secret))
          )
          val admission = _success(OperationEvaluationAdmission.unavailableC(Vector(
            OperationEvaluationLimitation(
              OperationEvaluationLimitationKind.Unavailable,
              diagnostic = Some(diagnostic)
            )
          )))
          val resolver = DeterministicOperationEvaluationResolver.fixed(admission)
          val fixture = _fixture(
            resolver,
            Some(_corpus_declaration(EvaluationAdmissionRequirement.Optional))
          )

          When("the optional operation completes and projects its admission report")
          val result = _success(fixture.subsystem.executeWithMetadata(_request))
          val report =
            result.metadata.operationEvaluation.getOrElse(fail("evaluation report missing"))
          val rendered = report.toRecord.print

          Then("only the normalized diagnostic key remains in bounded metadata")
          admission.toRecord.print should not include secret
          rendered should include("argument")
          rendered should not include secret
          report.admissions.flatMap(_.limitations).flatMap(_.diagnostic).map(_.token) shouldBe
            Vector("argument")
        }
      }

      "E11 reject an empty admitted result instead of reporting successful admission" must _e11_metadata {
        "when an optional resolver incorrectly returns no admitted values" in {
          Given("an optional Corpus declaration and a structurally empty provider result")
          val resolver = DeterministicOperationEvaluationResolver.fixed(
            OperationEvaluationAdmission.Admitted()
          )
          val fixture = _fixture(
            resolver,
            Some(_corpus_declaration(EvaluationAdmissionRequirement.Optional))
          )

          When("the empty result crosses runtime admission validation")
          val result = fixture.subsystem.executeOperationResponse(_request)

          Then(
            "the operation fails before business execution and empty checked construction is unavailable"
          )
          result shouldBe a[Consequence.Failure[_]]
          OperationEvaluationAdmission.admittedC() shouldBe a[Consequence.Failure[_]]
          resolver.requests should have size 1
          fixture.events.asScala.toVector shouldBe empty
          fixture.sink.facts.map(_.factKind.token) shouldBe
            Vector("operation-start", "operation-terminal")
        }
      }

      "E12 reject undeclared and incomplete values returned by a resolver" must _e12_metadata {
        "when provider output broadens or incompletely satisfies a declaration" in {
          Given(
            "a Corpus-only declaration and resolver results containing invalid Experiment state"
          )
          val admitted           = _experiment_admission()
          val undeclaredresolver = DeterministicOperationEvaluationResolver.fixed(admitted)
          val undeclaredfixture = _fixture(
            undeclaredresolver,
            Some(_corpus_declaration(EvaluationAdmissionRequirement.Optional))
          )
          val incompleteresolver = DeterministicOperationEvaluationResolver.fixed(
            OperationEvaluationAdmission.Admitted(experiment = admitted.experiment)
          )
          val incompletefixture = _fixture(
            incompleteresolver,
            Some(_experiment_declaration(EvaluationAdmissionRequirement.Optional))
          )

          When("both invalid provider results reach the framework validation boundary")
          val undeclaredresult = undeclaredfixture.subsystem.executeOperationResponse(_request)
          val incompleteresult = incompletefixture.subsystem.executeOperationResponse(_request)

          Then("both operations fail before request construction or business behavior")
          undeclaredresult shouldBe a[Consequence.Failure[_]]
          incompleteresult shouldBe a[Consequence.Failure[_]]
          undeclaredresolver.requests should have size 1
          incompleteresolver.requests should have size 1
          undeclaredfixture.events.asScala.toVector shouldBe empty
          incompletefixture.events.asScala.toVector shouldBe empty
        }
      }

      "E13 keep admission a one-time transition before attempt creation" must _e13_metadata {
        "when framework code tries to replace or delay an admitted assignment" in {
          Given("one prepared invocation with a complete immutable Experiment admission")
          val context = ExecutionContext.create()
          val operation = _success(
            OperationEvaluationOperationIdentity.createC("evaluation", "operation", "admit")
          )
          val prepared = _success(
            ExecutionContext.prepareOperationEvaluation(context, operation)
          )
          val admitted = _experiment_admission()
          val active = _success(ExecutionContext.admitOperationEvaluation(
            prepared,
            admitted.corpus,
            admitted.experiment,
            admitted.assignment
          ))
          val replacement = OperationEvaluationAssignment(
            _success(OperationEvaluationName.parseC("variant-c")),
            Some(_success(OperationEvaluationText.parseC("execution-plan-c")))
          )

          When("a second admission and a post-attempt admission are requested")
          val replaced = ExecutionContext.admitOperationEvaluation(
            active,
            admitted.corpus,
            admitted.experiment,
            Some(replacement)
          )
          val attempted = _success(ExecutionContext.beginOperationEvaluationAttempt(active))
          val delayed = ExecutionContext.admitOperationEvaluation(
            attempted,
            admitted.corpus,
            admitted.experiment,
            admitted.assignment
          )

          Then("both state transitions fail and the original assignment remains unchanged")
          replaced shouldBe a[Consequence.Failure[_]]
          delayed shouldBe a[Consequence.Failure[_]]
          active.operationEvaluation.invocation.flatMap(_.assignment) shouldBe admitted.assignment
          attempted.operationEvaluation.correlation should not be empty
        }
      }

    }

    "admit preconstructed execution paths before business behavior" which {
      "E9 admit a preconstructed direct Action before ActionCall execution" must _e9_metadata {
        "when public direct execution receives an already constructed Action" in {
          Given("an Action constructed independently of evaluation assignment")
          val events = new CopyOnWriteArrayList[String]()
          val resolver = DeterministicOperationEvaluationResolver { _ =>
            events.add("resolver")
            Consequence.success(_experiment_admission())
          }
          val fixture = _fixture(
            resolver,
            Some(_experiment_declaration(EvaluationAdmissionRequirement.Required)),
            events
          )
          val action = _action(fixture)
          events.clear()

          When("the subsystem executes the preconstructed Action")
          val result = fixture.subsystem.executeAction(action)

          Then("admission occurs once before ActionCall business behavior")
          result shouldBe Consequence.success(
            OperationResponse.Scalar("variant-b:execution-plan-b")
          )
          resolver.requests should have size 1
          events.asScala.toVector shouldBe Vector("resolver", "business:variant-b:execution-plan-b")
        }
      }

      "E10 admit a prepared Job Task before its ActionCall executes" must _e10_metadata {
        "when a framework Job path receives an already constructed Action" in {
          Given("an ActionTask prepared independently of evaluation assignment")
          val events = new CopyOnWriteArrayList[String]()
          val resolver = DeterministicOperationEvaluationResolver { _ =>
            events.add("resolver")
            Consequence.success(_experiment_admission())
          }
          val fixture = _fixture(
            resolver,
            Some(_experiment_declaration(EvaluationAdmissionRequirement.Required)),
            events
          )
          val action  = _action(fixture)
          val context = fixture.component.logic.executionContext()
          val rawtask = ActionTask(
            ActionId.create("evaluation.admission", context.clock.instant(), context.idGeneration),
            action,
            fixture.component.actionEngine,
            Some(fixture.component)
          )
          events.clear()

          When("the subsystem admits and runs the prepared task")
          val (task, activecontext) = _success(
            fixture.subsystem._prepare_operation_task(action, rawtask, context)
          )
          val result = task.run(activecontext).result

          Then("admission occurs once before the prepared ActionCall business behavior")
          result shouldBe Consequence.success(
            OperationResponse.Scalar("variant-b:execution-plan-b")
          )
          resolver.requests should have size 1
          events.asScala.toVector shouldBe Vector("resolver", "business:variant-b:execution-plan-b")
        }
      }
    }
  }

  private final case class Fixture(
      subsystem: Subsystem,
      component: AdmissionComponent,
      sink: DeterministicCorpusEvaluationSink,
      events: CopyOnWriteArrayList[String]
  )

  private def _fixture(
      resolver: OperationEvaluationResolver,
      declaration: Option[CmlOperationEvaluationDeclaration],
      events: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
  ): Fixture = {
    val scope = ScopeContext(
      kind = ScopeKind.Subsystem,
      name = "evaluation-admission",
      parent = None,
      observabilityContext = ExecutionContext.create().observability,
      operationEvaluationResolverOption = Some(resolver)
    )
    val subsystem = new Subsystem(
      name = "evaluation-admission",
      scopeContext = Some(scope),
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty,
      runMode = RunMode.Command
    )
    RuntimeBindingAdmissionFixture.admit(subsystem)
    _subsystems = _subsystems :+ subsystem
    val operation = AdmissionOperation("evaluate", events)
    val protocol = Protocol(services =
      spec.ServiceDefinitionGroup(Vector(
        spec.ServiceDefinition(
          "operation",
          spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
        )
      ))
    )
    val metadata = declaration.toVector.map(value =>
      CmlOperationDefinition(
        name = "evaluate",
        kind = "QUERY",
        inputType = "record",
        outputType = "record",
        inputValueKind = "record",
        evaluation = Some(value)
      )
    )
    val component   = new AdmissionComponent(metadata)
    val componentid = ComponentId("evaluation_admission")
    val core = Component.Core.create(
      "evaluation_admission",
      componentid,
      ComponentInstanceId.default(componentid),
      protocol
    )
    val sink = _success(
      DeterministicCorpusEvaluationSink.createC("evaluation_admission", "test-corpus")
    )
    component.installSpi(sink)
    val initialized = component
      .initialize(ComponentInit(subsystem, core, ComponentOrigin.Main))
      .asInstanceOf[AdmissionComponent]
    subsystem.add(initialized)
    Fixture(subsystem, initialized, sink, events)
  }

  private def _corpus_declaration(
      requirement: EvaluationAdmissionRequirement
  ): CmlOperationEvaluationDeclaration =
    CmlOperationEvaluationDeclaration(corpus =
      Some(CmlCorpusEvaluationDeclaration(
        capture = CorpusCaptureMode.Candidate,
        profile = _success(OperationEvaluationName.parseC("evaluation-corpus")),
        admission = requirement
      ))
    )

  private def _experiment_declaration(
      requirement: EvaluationAdmissionRequirement
  ): CmlOperationEvaluationDeclaration =
    CmlOperationEvaluationDeclaration(experiment =
      Some(CmlExperimentEvaluationDeclaration(
        eligible = true,
        purpose = _success(OperationEvaluationName.parseC("evaluation-experiment")),
        admission = requirement,
        variantProfile = Some(_success(OperationEvaluationName.parseC("execution-plan")))
      ))
    )

  private def _experiment_admission(): OperationEvaluationAdmission.Admitted = {
    val revision = _success(CorpusRevisionReference.parseC("revision-9"))
    val experiment = _success(ExperimentEvaluationCorrelation.createC(
      _success(ExperimentReference.parseC("experiment-17")),
      Some(_success(ExperimentArmReference.parseC("arm-b"))),
      Some(_success(ExperimentRunReference.parseC("run-4"))),
      Some(revision)
    ))
    val assignment = OperationEvaluationAssignment(
      _success(OperationEvaluationName.parseC("variant-b")),
      Some(_success(OperationEvaluationText.parseC("execution-plan-b")))
    )
    _success(OperationEvaluationAdmission.admittedC(
      experiment = Some(experiment),
      assignment = Some(assignment)
    ))
  }

  private def _action(
      fixture: Fixture
  ): QueryAction =
    _success(fixture.component.logic.makeOperationRequest(_request)) match {
      case action: QueryAction => action
      case other               => fail(s"query Action missing: $other")
    }

  private def _http_request: HttpRequest =
    HttpRequest(
      HttpPath.parse("/form-api/evaluation_admission/operation/evaluate"),
      HttpRequest.POST,
      Record.empty,
      Record.empty,
      Record.empty
    )

  private def _success[A](result: Consequence[A]): A =
    result.fold(
      conclusion => fail(conclusion.toString),
      identity
    )

  private def _request: Request =
    Request.of(
      component = "evaluation_admission",
      service = "operation",
      operation = "evaluate"
    )
}

private final class AdmissionComponent(
    definitions: Vector[CmlOperationDefinition]
) extends Component with CorpusEvaluationSinkSocket {
  override def operationDefinitions: Vector[CmlOperationDefinition] = definitions
}

private final case class AdmissionOperation(
    operationname: String,
    events: CopyOnWriteArrayList[String]
) extends spec.OperationDefinition {
  val specification = spec.OperationDefinition.Specification(
    name = operationname,
    request = spec.RequestDefinition(),
    response = spec.ResponseDefinition.void
  )

  def createOperationRequest(request: Request): Consequence[OperationRequest] = {
    events.add("request")
    Consequence.success(AdmissionQueryAction(request, events))
  }
}

private final case class AdmissionQueryAction(
    request: Request,
    events: CopyOnWriteArrayList[String]
) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall = AdmissionActionCall(core, events)
}

private final case class AdmissionActionCall(
    core: ActionCall.Core,
    events: CopyOnWriteArrayList[String]
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] = {
    val value = (
      operation_evaluation_variant,
      operation_evaluation_execution_plan
    ) match {
      case (Some(variant), Some(plan)) => s"$variant:$plan"
      case (Some(variant), None)       => variant
      case _                           => "control"
    }
    events.add(s"business:$value")
    Consequence.success(OperationResponse.Scalar(value))
  }
}
