package org.goldenport.cncf.workflow

import cats.data.NonEmptyVector
import cats.syntax.functor.*
import io.circe.Json
import org.goldenport.{Conclusion, Consequence}
import cats.free.Free
import org.goldenport.ConsequenceT
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkTermination}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 21, 2026
 * @version Sep. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedWorkflowAbiSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  import GeneratedWorkflowAbi.*

  "Generated workflow ABI admission" should {
    "route one declared Provided Operation through the public Service and ActionEngine commit" in {
      Given("declared Start and completion Operations, a program Provider, and persistent suspension ports")
      val workflow = _definition()
      val providedBase = _provided_definition(workflow)
      val provided = providedBase.copy(providedOperations = providedBase.providedOperations :+
        GeneratedProvidedApiAbi.Operation(
          "WorkflowService", "submitReview", Some("ReviewResult"), Some("WorkflowInteraction"),
          GeneratedProvidedApiAbi.SourceIdentity(Some(73))
        )
      )
      val request = StateMachineProvidedApiRequest(
        "WorkflowProducer", "workflow-producer-v1", StateMachineRunIdentity("service-run"),
        StateMachineOperationIdentity("WorkflowService", "beginReview"),
        Some(StateMachineOperationInput(StateMachineInputTypeReference("ReviewContext"), ContextReference("input", "1"))),
        ContextBundle("review", Vector.empty, Vector.empty, ContextSnapshot("workflow-producer-v1"))
      )
      val required = StateMachineRequiredOperation(
        StateMachineRequiredOperationIdentity("review-change-capability"), "ReviewChange",
        StateMachineOperationIdentity("WorkflowService", "reviewChange"),
        Some(StateMachineInputTypeReference("ReviewContext")), Some(StateMachineResultTypeReference("ReviewResult")),
        StateMachineRequiredOperationMetadata(
          ContextContract("review-context", Vector.empty, Vector.empty),
          CompletionContract("review-completion", Vector.empty),
          EvidenceContract("review-evidence", Vector.empty), Vector.empty
        )
      )
      val backing = new ContinuationRuntimePersistence.InMemory
      var rejectWrite = false
      val persistence = new ContinuationRuntimePersistence {
        def createC(record: ContinuationRuntimePersistence.Record): Consequence[Unit] =
          if (rejectWrite) Consequence.stateConflict("planned public Service continuation write failure")
          else backing.createC(record)
        def loadC(identity: ContinuationIdentity): Consequence[Option[ContinuationRuntimePersistence.Record]] =
          backing.loadC(identity)
        def claimC(identity: ContinuationIdentity): Consequence[ContinuationRuntimePersistence.Record] =
          backing.claimC(identity)
        def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
          backing.releaseC(identity, claimId)
        def completeC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
          backing.completeC(identity, claimId)
      }
      val runtime = new PersistentContinuationRuntime(persistence)
      var selected = Continuation(
        request.runId, ContinuationIdentity("public-service-continuation"), StateMachineRevision("1"), required, request.context
      )
      val binding = WorkflowInstancePersistence.bindDefinitionC(workflow).toOption.getOrElse(
        fail("public Service Workflow definition must bind to the instance store")
      )
      val storeIdentity = WorkflowInstancePersistence.WorkflowStoreIdentity("service-workflow-store")
      val configuration = WorkflowInstancePersistence.Configuration(
        WorkflowInstancePersistence.NamedWorkflowStore("service-workflow", storeIdentity),
        WorkflowInstancePersistence.MigrationPolicy("fixture-v1"),
        WorkflowInstancePersistence.RetentionPolicy("fixture-v1"),
        WorkflowInstancePersistence.LeasePolicy("fixture-v1"),
        WorkflowInstancePersistence.DeliveryConfiguration.SameStore(storeIdentity)
      )
      val records = scala.collection.mutable.Map.empty[
        WorkflowInstancePersistence.InstanceIdentity, WorkflowInstancePersistence.InstanceRecord
      ]
      var rejectInstanceWrite = false
      val instances = new WorkflowInstancePersistence {
        def create(
          actual: WorkflowInstancePersistence.Configuration,
          record: WorkflowInstancePersistence.InstanceRecord
        ): Consequence[WorkflowInstancePersistence.InstanceRecord] =
          if (rejectInstanceWrite) Consequence.stateConflict("planned public Service instance write failure")
          else if (actual != configuration || records.contains(record.identity))
            Consequence.stateConflict("public Service instance create is duplicate or misconfigured")
          else record.validateC.map { admitted =>
            records.update(admitted.identity, admitted)
            admitted
          }
        def load(
          actual: WorkflowInstancePersistence.Configuration,
          identity: WorkflowInstancePersistence.InstanceIdentity
        ): Consequence[Option[WorkflowInstancePersistence.InstanceRecord]] =
          if (actual != configuration) Consequence.stateConflict("public Service instance store mismatch")
          else Consequence.success(records.get(identity))
        def append(
          actual: WorkflowInstancePersistence.Configuration,
          identity: WorkflowInstancePersistence.InstanceIdentity,
          expectedRevision: WorkflowInstancePersistence.InstanceRevision,
          entry: WorkflowInstancePersistence.HistoryEntry
        ): Consequence[WorkflowInstancePersistence.InstanceRecord] =
          if (actual != configuration) Consequence.stateConflict("public Service instance store mismatch")
          else records.get(identity) match {
            case Some(current) => current.appendC(expectedRevision, entry).map { next =>
              records.update(identity, next)
              next
            }
            case None => Consequence.stateConflict("public Service instance is unavailable for append")
          }
      }
      val suspensionPersistence = new StateMachineProvidedApiServiceOperation.SuspensionPersistence {
        def persistC(actual: StateMachineProvidedApiRequest, continuation: Continuation): Consequence[Unit] = {
          actual shouldBe request
          val initial = WorkflowInstancePersistence.InstanceRecord(
            WorkflowInstancePersistence.InstanceIdentity(s"public-service-${continuation.continuationId.value}"), binding,
            WorkflowInstancePersistence.initialRevision, WorkflowInstancePersistence.Lifecycle.NotStarted,
            None, WorkflowInstancePersistence.CorrelationReference("service-start"),
            WorkflowInstancePersistence.CausationReference("service-request"), None, None, None, Vector.empty
          )
          val suspension = WorkflowInstancePersistence.SuspensionBoundary(
            WorkflowInstancePersistence.ContinuationIdentity(continuation.continuationId.value),
            WorkflowInstancePersistence.InstanceRevision(1L),
            WorkflowInstancePersistence.ContextSnapshotReference("service-snapshot"),
            WorkflowInstancePersistence.CompletionReference(continuation.requiredOperation.metadata.completionContract.identity),
            WorkflowInstancePersistence.EvidenceReference(continuation.requiredOperation.metadata.evidenceContract.identity),
            WorkflowInstancePersistence.CorrelationReference("service-resume")
          )
          val entry = WorkflowInstancePersistence.HistoryEntry(
            WorkflowInstancePersistence.HistorySequence(1L), WorkflowInstancePersistence.InstanceRevision(1L),
            WorkflowInstancePersistence.Lifecycle.Active,
            Some(WorkflowInstancePersistence.ProgressionReference("review-awaiting-result")),
            WorkflowInstancePersistence.CorrelationReference("service-start"),
            WorkflowInstancePersistence.CausationReference("service-request"), None, None, Some(suspension)
          )
          initial.appendC(WorkflowInstancePersistence.initialRevision, entry)
            .flatMap(instances.create(configuration, _)).map(_ => ())
        }
      }
      val provider = new StateMachineProgramProvider {
        val identity = ProviderIdentity("public-service-review-provider")
        def program(actual: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          actual.runId shouldBe request.runId
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Suspended(selected)
          )
        }
      }
      val program = new StateMachineProvidedApiProgram {
        val workflowIdentity = request.workflowIdentity
        val operation = request.operation
        def program(actual: StateMachineProvidedApiRequest): ExecUowM[ActionExecution] = {
          actual shouldBe request
          ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(
            ProviderExecutionRequest(request.runId, required, request.input, request.context)
          )))
        }
      }
      val inputCodec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewContext"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] = value.asString.toRight("expected ReviewContext")
      }
      val resultCodec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewResult"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] = value.asString.toRight("expected ReviewResult")
      }
      val startRequest = WorkflowStartRequest(
        request.operation,
        WorkflowInstancePersistence.WorkflowDefinitionIdentity(workflow.workflow.identity.value),
        WorkflowInstancePersistence.WorkflowDefinitionRevision(workflow.workflow.revision.value),
        TypedValue("ReviewContext", "review-input"), "service-start", request.runId.value
      )
      val startJson = WorkflowStartJsonV1.encodeC(startRequest, inputCodec).toOption.getOrElse(
        fail("public Service profile Start must encode")
      )
      val issuedStore = new IssuedWorkOrderPersistence.InMemory[String]
      var componentIdentity: Option[ComponentId] = None
      var postCommitResponses = 0
      var rejectProjection = false
      val codec = new StateMachineProvidedApiServiceOperation.Codec {
        def decodeC(raw: Request): Consequence[StateMachineProvidedApiRequest] =
          raw.arguments match {
            case List(Argument("arg1", wire: String, _)) =>
              WorkflowStartJsonV1.decodeBoundC(
                wire, inputCodec, request.operation, startRequest.workflowIdentity, startRequest.workflowRevision
              ).flatMap { decoded =>
                if (decoded == startRequest) Consequence.success(request)
                else Consequence.stateConflict("public Service profile Start payload or invocation differs")
              }
            case _ => Consequence.stateConflict("public Service profile Start JSON is missing")
          }
        def encodeC(outcome: ActionExecution): Consequence[OperationResponse] = {
          runtime.claimC(selected.continuationId) shouldBe a[Consequence.Failure[_]]
          outcome match {
            case ActionExecution.Suspended(value) if value == selected =>
              Consequence.success(OperationResponse.Scalar("SUSPENDED"))
            case _ => Consequence.stateConflict("unexpected public Service outcome")
          }
        }
        override def encodeAfterCommitC(
          actual: StateMachineProvidedApiRequest,
          outcome: ActionExecution,
          encoded: OperationResponse
        ): Consequence[OperationResponse] = {
          postCommitResponses += 1
          actual shouldBe request
          encoded shouldBe OperationResponse.Scalar("SUSPENDED")
          if (rejectProjection) Consequence.stateConflict("planned public Service response projection failure")
          else outcome match {
            case ActionExecution.Suspended(continuation) =>
              for {
                component <- componentIdentity match {
                  case Some(value) => Consequence.success(value)
                  case None => Consequence.stateConflict("public Service component identity is unavailable")
                }
                _ <- runtime.claimC(continuation.continuationId)
                stored <- instances.load(configuration, WorkflowInstancePersistence.InstanceIdentity(
                  s"public-service-${continuation.continuationId.value}"
                ))
                active <- stored match {
                  case Some(value) => Consequence.success(value)
                  case None => Consequence.stateConflict("public Service WorkflowInstance is unavailable")
                }
                handle <- WorkflowHandle.fromRecordC(component, active)
                issued <- issueRecoveredWorkOrderC(
                  handle, continuation.continuationId, Some(TypedValue("ReviewContext", "review-input")),
                  StateMachineOperationIdentity("WorkflowService", "submitReview"),
                  ExecutionRequirement(Vector(CapabilityRequirement("review")), RiskLevel("standard"), ReasoningLevel.Deep, true),
                  MinimalPresentation("Review change", "Waiting for a reviewer"), runtime, issuedStore
                )
                json <- WorkflowWorkOrderJsonV1.encodeC(issued, inputCodec)
              } yield OperationResponse.Scalar(json)
            case _ => Consequence.stateConflict("public Service post-commit outcome is not suspended")
          }
        }
      }
      val operation = StateMachineProvidedApiServiceOperation.bindC(
        provided, provided.providedOperations.head, spec.RequestDefinition(),
        spec.ResponseDefinition.void, codec, Some(suspensionPersistence)
      ).toOption.getOrElse(fail("declared public Service operation must bind"))
      val completionCodec = new WorkflowCompletionServiceOperation.Codec[String] {
        val resultTypeIdentity = "ReviewResult"
        def decodeC(raw: Request): Consequence[ContinuationResult[String]] =
          raw.arguments match {
            case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, resultCodec)
            case _ => Consequence.stateConflict("public completion Result JSON is missing")
          }
        def encodeC(resume: ContinuationRuntime.Resume): Consequence[OperationResponse] =
          Consequence.success(OperationResponse.Scalar(s"RESUMED:${resume.continuation.continuationId.value}"))
      }
      var closingExecutions = 0
      val closingProgram = new WorkflowCompletionServiceOperation.ClosingProgram[String] {
        def selectC(
          submitted: ContinuationResult[String],
          current: WorkflowInstancePersistence.InstanceRecord
        ): Consequence[WorkflowCompletionServiceOperation.SelectedClosing] =
          if (submitted.result.value != "approved" ||
              !current.suspension.exists(_.continuationIdentity.value == submitted.continuationId.value))
            Consequence.stateConflict("public Service closing Action is not selected by the current StateMachine facts")
          else current.appendC(current.revision, WorkflowInstancePersistence.HistoryEntry(
            WorkflowInstancePersistence.HistorySequence(current.history.size.toLong + 1L),
            current.revision.next, WorkflowInstancePersistence.Lifecycle.Completed,
            None,
            current.correlation,
            WorkflowInstancePersistence.CausationReference(
              s"review-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}"
            ), current.derivedCompositeOccurrence, current.committedPredecessor, None
          )).map { next => WorkflowCompletionServiceOperation.SelectedClosing(
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(
                StateMachineResultTypeReference("ReviewResult"), submitted.resultReference
              ))
            ).map { outcome =>
              closingExecutions += 1
              outcome
            }, Some(next)
          ) }
      }
      val completionOperation = WorkflowCompletionServiceOperation.bindC[String, String](
        provided, provided.providedOperations.last, spec.RequestDefinition(),
        spec.ResponseDefinition.void, completionCodec, new PersistentContinuationRuntime(persistence),
        issuedStore, instances, configuration, () => new UnitOfWork(ExecutionContext.create()),
        Some(closingProgram)
      ).toOption.getOrElse(fail("declared public completion Operation must bind"))
      val service = spec.ServiceDefinition(
        name = "WorkflowService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation, completionOperation))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = TestComponentFactory.admittedEmptySubsystem("public-provided-api")
      try {
        val component = _initialized_component(
          subsystem,
          new Component() with GeneratedWorkflowMetadataProvider with GeneratedProvidedApiMetadataProvider
            with StateMachineProvidedApiProgramSource with StateMachineProviderSource with ContinuationRuntimeSource {
            override def generatedWorkflowDefinitions: Vector[Definition] = Vector(workflow)
            override def generatedProvidedApiDefinitions: Vector[GeneratedProvidedApiAbi.Definition] = Vector(provided)
            override def stateMachineProvidedApiPrograms: Vector[StateMachineProvidedApiProgram] = Vector(program)
            override def stateMachineProviderBindings: Vector[StateMachineProviderBinding] =
              Vector(StateMachineProviderBinding(required.identity, provider.identity))
            override def stateMachineProviders: Vector[StateMachineProvider] = Vector(provider)
            override def continuationRuntimeOption: Option[ContinuationRuntime] = Some(runtime)
          },
          protocol = protocol
        )
        componentIdentity = Some(component.componentId)
        subsystem.add(component)
        val raw = Request.of(
          component = component.componentId.name, service = "WorkflowService", operation = "beginReview",
          arguments = List(Argument("arg1", startJson))
        )
        When("the public Start request is missing, mismatched, or malformed")
        subsystem.executeOperationResponse(raw.copy(arguments = Nil)) shouldBe a[Consequence.Failure[_]]
        subsystem.executeOperationResponse(raw.copy(arguments = List(Argument(
          "arg1", startJson.replace("\"beginReview\"", "\"other\"")
        )))) shouldBe a[Consequence.Failure[_]]
        subsystem.executeOperationResponse(raw.copy(arguments = List(Argument(
          "arg1", startJson.replace("\"review-input\"", "1")
        )))) shouldBe a[Consequence.Failure[_]]
        subsystem.executeOperationResponse(raw.copy(arguments = List(Argument(
          "arg1", startJson.replace("\"kind\":", "\"unknown\":1,\"kind\":")
        )))) shouldBe a[Consequence.Failure[_]]
        Then("no Continuation is claimable before a valid Start commits")
        runtime.claimC(selected.continuationId) shouldBe a[Consequence.Failure[_]]
        When("the declared Start Operation runs through the public Service")
        val startResponse = subsystem.executeOperationResponse(raw).toOption.getOrElse(
          fail("public Service Start must return after commit")
        )
        Then("the committed response exposes a persisted Handle and issued WorkOrder")
        postCommitResponses shouldBe 1
        val orderJson = startResponse match {
          case OperationResponse.Scalar(value: String) => value
          case _ => fail("public Service Start must return a WorkOrder JSON scalar")
        }
        val instanceIdentity = WorkflowInstancePersistence.InstanceIdentity(
          s"public-service-${selected.continuationId.value}"
        )
        val active = instances.load(configuration, instanceIdentity).toOption.flatten.getOrElse(
          fail("public Service must create the suspended WorkflowInstance before Continuation publication")
        )
        val handle = WorkflowHandle.fromRecordC(component.componentId, active).toOption.getOrElse(
          fail("stored WorkflowInstance must produce the public Handle")
        )
        val issued = WorkflowWorkOrderJsonV1.decodeC(orderJson, inputCodec).toOption.getOrElse(
          fail("public Service Start WorkOrder must decode")
        )
        issued.handle shouldBe handle
        val submitted = ContinuationResult(
          handle, selected.runId, selected.continuationId, selected.expectedRevision, selected.context.snapshot,
          TypedValue("ReviewResult", "approved"), ContextReference("review-result", "1"),
          Vector.empty, ExecutionEvidence(Vector.empty)
        )
        val resultJson = WorkflowResultJsonV1.encodeC(submitted, resultCodec).toOption.getOrElse(
          fail("public Service result must encode")
        )
        val decoded = WorkflowResultJsonV1.decodeC(resultJson, resultCodec).toOption.getOrElse(
          fail("public Service result must decode")
        )
        decoded shouldBe submitted
        val completionRaw = Request.of(
          component = component.componentId.name, service = "WorkflowService", operation = "submitReview",
          arguments = List(Argument("arg1", resultJson))
        )
        When("completion input is missing, type-incompatible, or the saved instance is unsuspended")
        subsystem.executeOperationResponse(completionRaw.copy(arguments = Nil)) shouldBe a[Consequence.Failure[_]]
        subsystem.executeOperationResponse(completionRaw.copy(arguments = List(Argument(
          "arg1", resultJson.replace("\"ReviewResult\"", "\"WrongResult\"")
        )))) shouldBe a[Consequence.Failure[_]]
        records.update(instanceIdentity, active.copy(
          suspension = None,
          history = active.history.map(_.copy(suspension = None))
        ))
        Then("the unsuspended instance rejects completion before the closing Action can resume")
        subsystem.executeOperationResponse(completionRaw) shouldBe a[Consequence.Failure[_]]
        When("the stored suspension is restored and the matching Result is submitted")
        records.update(instanceIdentity, active)
        subsystem.executeOperationResponse(completionRaw) shouldBe Consequence.success(
          OperationResponse.Scalar(s"RESUMED:${selected.continuationId.value}")
        )
        Then("the closing Action and terminal history append occur exactly once")
        closingExecutions shouldBe 1
        val completed = records(instanceIdentity)
        completed.revision shouldBe WorkflowInstancePersistence.InstanceRevision(2L)
        completed.lifecycle shouldBe WorkflowInstancePersistence.Lifecycle.Completed
        completed.suspension shouldBe None
        completed.history.size shouldBe 2
        subsystem.executeOperationResponse(completionRaw) shouldBe a[Consequence.Failure[_]]
        closingExecutions shouldBe 1
        records(instanceIdentity) shouldBe completed

        When("post-commit instance persistence, Continuation persistence, or response projection fails")
        selected = selected.copy(continuationId = ContinuationIdentity("failed-instance-public-service-continuation"))
        rejectInstanceWrite = true
        subsystem.executeOperationResponse(raw) shouldBe a[Consequence.Failure[_]]
        runtime.claimC(selected.continuationId) shouldBe a[Consequence.Failure[_]]
        postCommitResponses shouldBe 1
        rejectInstanceWrite = false
        selected = selected.copy(continuationId = ContinuationIdentity("failed-public-service-continuation"))
        rejectWrite = true
        subsystem.executeOperationResponse(raw) shouldBe a[Consequence.Failure[_]]
        runtime.claimC(selected.continuationId) shouldBe a[Consequence.Failure[_]]
        postCommitResponses shouldBe 1
        rejectWrite = false
        selected = selected.copy(continuationId = ContinuationIdentity("failed-projection-public-service-continuation"))
        rejectProjection = true
        subsystem.executeOperationResponse(raw) shouldBe a[Consequence.Failure[_]]
        Then("failed writes expose no work, while a failed projection preserves committed work")
        postCommitResponses shouldBe 2
        runtime.claimC(selected.continuationId).isSuccess shouldBe true
        instances.load(configuration, WorkflowInstancePersistence.InstanceIdentity(
          s"public-service-${selected.continuationId.value}"
        )).toOption.flatten.isDefined shouldBe true
      } finally {
        subsystem.shutdown()
      }
    }

    "commit an explicitly declared Provided suspension before external claim and typed resume" in {
      val workflow = _definition()
      val providedBase = _provided_definition(workflow)
      val provided = providedBase.copy(providedOperations =
        providedBase.providedOperations.map(_.copy(resultType = Some("WorkflowStartResult")))
      )
      val operation = StateMachineOperationIdentity("WorkflowService", "beginReview")
      val input = Some(StateMachineOperationInput(
        StateMachineInputTypeReference("ReviewContext"), ContextReference("input-1", "1")
      ))
      val contextBundle = ContextBundle("review", Vector.empty, Vector.empty, ContextSnapshot("workflow-producer-v1"))
      val request = StateMachineProvidedApiRequest(
        "WorkflowProducer", "workflow-producer-v1", StateMachineRunIdentity("run-1"), operation,
        input, contextBundle
      )
      val required = StateMachineRequiredOperation(
        StateMachineRequiredOperationIdentity("review-change-capability"), "ReviewChange",
        StateMachineOperationIdentity("WorkflowService", "reviewChange"),
        Some(StateMachineInputTypeReference("ReviewContext")), Some(StateMachineResultTypeReference("ReviewResult")),
        StateMachineRequiredOperationMetadata(
          ContextContract("review-context", Vector.empty, Vector.empty),
          CompletionContract("review-completion", Vector.empty),
          EvidenceContract("review-evidence", Vector.empty), Vector.empty
        )
      )
      val continuation = Continuation(
        request.runId, ContinuationIdentity("review-continuation"), StateMachineRevision("1"),
        required, request.context
      )
      var selectedContinuation = continuation
      val providerRequest = ProviderExecutionRequest(request.runId, required, request.input, request.context)
      val program = new StateMachineProvidedApiProgram {
        val workflowIdentity = "WorkflowProducer"
        val operation = StateMachineOperationIdentity("WorkflowService", "beginReview")
        def program(actual: StateMachineProvidedApiRequest): ExecUowM[ActionExecution] = {
          actual shouldBe request
          ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(providerRequest)))
        }
      }
      val provider = new StateMachineProgramProvider {
        val identity = ProviderIdentity("external-review-provider")
        def program(actual: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          actual shouldBe providerRequest
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Suspended(selectedContinuation)
          )
        }
      }
      val backing = new ContinuationRuntimePersistence.InMemory
      var failCreate = false
      val persistence = new ContinuationRuntimePersistence {
        def createC(record: ContinuationRuntimePersistence.Record): Consequence[Unit] =
          if (failCreate) Consequence.Failure(Conclusion.from(new IllegalStateException("planned post-commit write failure")))
          else backing.createC(record)
        def loadC(identity: ContinuationIdentity): Consequence[Option[ContinuationRuntimePersistence.Record]] =
          backing.loadC(identity)
        def claimC(identity: ContinuationIdentity): Consequence[ContinuationRuntimePersistence.Record] =
          backing.claimC(identity)
        def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
          backing.releaseC(identity, claimId)
        def completeC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
          backing.completeC(identity, claimId)
      }
      val runtime = new PersistentContinuationRuntime(persistence)
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("provided_api_suspension"),
        new Component() with GeneratedWorkflowMetadataProvider with GeneratedProvidedApiMetadataProvider
          with StateMachineProvidedApiProgramSource with StateMachineProviderSource with ContinuationRuntimeSource {
          override def generatedWorkflowDefinitions: Vector[Definition] = Vector(workflow)
          override def generatedProvidedApiDefinitions: Vector[GeneratedProvidedApiAbi.Definition] = Vector(provided)
          override def stateMachineProvidedApiPrograms: Vector[StateMachineProvidedApiProgram] = Vector(program)
          override def stateMachineProviderBindings: Vector[StateMachineProviderBinding] =
            Vector(StateMachineProviderBinding(required.identity, provider.identity))
          override def stateMachineProviders: Vector[StateMachineProvider] = Vector(provider)
          override def continuationRuntimeOption: Option[ContinuationRuntime] = Some(runtime)
        }
      )
      new ComponentFactory().bootstrapC(component).toOption shouldBe Some(component)
      val root = ExecutionContext.create()
      val execution = root.withScope(Component.Context("provided-api-suspension", root.scope, component, ComponentOrigin.Embed))

      val directUow = new UnitOfWork(execution)
      val direct = new UnitOfWorkInterpreter(directUow).run(ConsequenceT.liftF(
        Free.liftF(UnitOfWorkOp.StateMachineProvidedApiExecute(request))
      ))
      direct shouldBe a[Consequence.Failure[_]]
      runtime.claimC(continuation.continuationId) shouldBe a[Consequence.Failure[_]]

      selectedContinuation = continuation.copy(continuationId = ContinuationIdentity("aborted-service-continuation"))
      val abortedServiceUow = new UnitOfWork(execution)
      new UnitOfWorkInterpreter(abortedServiceUow).stageProvidedForExternalCommitC(request) shouldBe
        Consequence.success(ActionExecution.Suspended(selectedContinuation))
      runtime.claimC(selectedContinuation.continuationId) shouldBe a[Consequence.Failure[_]]
      abortedServiceUow.rollback().isSuccess shouldBe true
      runtime.claimC(selectedContinuation.continuationId) shouldBe a[Consequence.Failure[_]]

      selectedContinuation = continuation.copy(continuationId = ContinuationIdentity("committed-service-continuation"))
      val serviceOwnedUow = new UnitOfWork(execution)
      new UnitOfWorkInterpreter(serviceOwnedUow).stageProvidedForExternalCommitC(request) shouldBe
        Consequence.success(ActionExecution.Suspended(selectedContinuation))
      runtime.claimC(selectedContinuation.continuationId) shouldBe a[Consequence.Failure[_]]
      serviceOwnedUow.commit().isSuccess shouldBe true
      runtime.claimC(selectedContinuation.continuationId).isSuccess shouldBe true

      selectedContinuation = continuation
      val startUow = new UnitOfWork(execution)
      val suspended = new UnitOfWorkInterpreter(startUow).runProvidedCommittingC(request)
      suspended shouldBe Consequence.success(ActionExecution.Suspended(continuation))
      startUow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      val recoveredRuntime = new PersistentContinuationRuntime(persistence)
      val claim = recoveredRuntime.claimC(continuation.continuationId).toOption.getOrElse(
        fail("committed external continuation must survive runtime recreation")
      )
      runtime.claimC(continuation.continuationId) shouldBe a[Consequence.Failure[_]]
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("ReviewResult"), ContextReference("review-result", "1")
      )
      val resumedRuntime = new PersistentContinuationRuntime(persistence)
      val recoveredClaim = resumedRuntime.recoverClaimC(continuation.continuationId).toOption.getOrElse(
        fail("restart must recover the private claim from persistence")
      )
      recoveredClaim shouldBe claim
      resumedRuntime.resumeC(recoveredClaim, result, () => new UnitOfWork(execution)).isSuccess shouldBe true
      runtime.claimC(continuation.continuationId) shouldBe a[Consequence.Failure[_]]
      new PersistentContinuationRuntime(persistence).recoverClaimC(continuation.continuationId) shouldBe
        a[Consequence.Failure[_]]
      new PersistentContinuationRuntime(persistence).resumeC(
        claim, result, () => new UnitOfWork(execution)
      ) shouldBe a[Consequence.Failure[_]]

      val foreign = continuation.copy(
        continuationId = ContinuationIdentity("foreign-continuation"),
        requiredOperation = required.copy(identity = StateMachineRequiredOperationIdentity("foreign-capability"))
      )
      component.stateMachineProvidedApiDispatcher.admitCommittingOutcomeC(
        request, ActionExecution.Suspended(foreign)
      ) shouldBe a[Consequence.Failure[_]]

      selectedContinuation = continuation.copy(continuationId = ContinuationIdentity("failed-continuation"))
      failCreate = true
      val failedUow = new UnitOfWork(execution)
      val failed = new UnitOfWorkInterpreter(failedUow).runProvidedCommittingC(request)
      failed shouldBe a[Consequence.Failure[_]]
      failed.display should include ("planned post-commit write failure")
      failedUow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      backing.loadC(selectedContinuation.continuationId).toOption.flatten shouldBe None
      runtime.claimC(selectedContinuation.continuationId) shouldBe a[Consequence.Failure[_]]
    }

    "dispatch one declared Provided API program through the active UnitOfWork and Required SPI Provider" in {
      Given("matching generated metadata, a component-owned Provided program, and a bound Required SPI Provider")
      val workflow = _definition()
      val provided = _provided_definition(workflow)
      val operation = StateMachineOperationIdentity("WorkflowService", "beginReview")
      val result = ActionExecution.Completed(StateMachineOperationResult(
        StateMachineResultTypeReference("ReviewResult"), ContextReference("result-1", "1")
      ))
      val request = StateMachineProvidedApiRequest(
        "WorkflowProducer", "workflow-producer-v1", StateMachineRunIdentity("run-1"), operation,
        Some(StateMachineOperationInput(StateMachineInputTypeReference("ReviewContext"), ContextReference("input-1", "1"))),
        ContextBundle("review", Vector.empty, Vector.empty, ContextSnapshot("workflow-producer-v1"))
      )
      val required = StateMachineRequiredOperation(
        StateMachineRequiredOperationIdentity("review-change-capability"), "ReviewChange",
        StateMachineOperationIdentity("WorkflowService", "reviewChange"),
        Some(StateMachineInputTypeReference("ReviewContext")), Some(StateMachineResultTypeReference("ReviewResult")),
        StateMachineRequiredOperationMetadata(
          ContextContract("review-context", Vector.empty, Vector.empty),
          CompletionContract("review-completion", Vector.empty),
          EvidenceContract("review-evidence", Vector.empty), Vector.empty
        )
      )
      val providerRequest = ProviderExecutionRequest(request.runId, required, request.input, request.context)
      var providedCalls = 0
      var providerCalls = 0
      val providedProgram = new StateMachineProvidedApiProgram {
        val workflowIdentity = "WorkflowProducer"
        val operation = StateMachineOperationIdentity("WorkflowService", "beginReview")
        def program(actual: StateMachineProvidedApiRequest): ExecUowM[ActionExecution] = {
          actual shouldBe request
          providedCalls += 1
          ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(providerRequest)))
        }
      }
      val provider = new StateMachineProgramProvider {
        val identity = ProviderIdentity("review-provider")
        def program(actual: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          actual shouldBe providerRequest
          providerCalls += 1
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](result)
        }
      }
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("generated_provided_api_dispatch"),
        new Component() with GeneratedWorkflowMetadataProvider with GeneratedProvidedApiMetadataProvider
          with StateMachineProvidedApiProgramSource with StateMachineProviderSource {
          override def generatedWorkflowDefinitions: Vector[Definition] = Vector(workflow)
          override def generatedProvidedApiDefinitions: Vector[GeneratedProvidedApiAbi.Definition] = Vector(provided)
          override def stateMachineProvidedApiPrograms: Vector[StateMachineProvidedApiProgram] = Vector(providedProgram)
          override def stateMachineProviderBindings: Vector[StateMachineProviderBinding] =
            Vector(StateMachineProviderBinding(required.identity, provider.identity))
          override def stateMachineProviders: Vector[StateMachineProvider] = Vector(provider)
        }
      )

      When("ComponentFactory boots the dispatcher and the typed operation is interpreted")
      new ComponentFactory().bootstrapC(component).toOption shouldBe Some(component)
      val root = ExecutionContext.create()
      val context = root.withScope(Component.Context("provided-api-dispatch", root.scope, component, ComponentOrigin.Embed))
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(context))
      val dispatched = interpreter.run(ConsequenceT.liftF(
        Free.liftF(UnitOfWorkOp.StateMachineProvidedApiExecute(request))
      ))
      val unknown = component.stateMachineProvidedApiDispatcher.resolveC(
        request.copy(operation = StateMachineOperationIdentity("WorkflowService", "undeclared"))
      )
      val wrongInput = component.stateMachineProvidedApiDispatcher.resolveC(
        request.copy(input = Some(StateMachineOperationInput(
          StateMachineInputTypeReference("WrongContext"), ContextReference("input-1", "1")
        )))
      )
      val wrongResult = component.stateMachineProvidedApiDispatcher.admitResultC(
        request,
        ActionExecution.Completed(StateMachineOperationResult(
          StateMachineResultTypeReference("WrongResult"), ContextReference("result-1", "1")
        ))
      )

      Then("only the admitted operation reaches its program and bound Provider")
      dispatched shouldBe Consequence.success(result)
      unknown shouldBe a[Consequence.Failure[_]]
      wrongInput shouldBe a[Consequence.Failure[_]]
      wrongResult shouldBe a[Consequence.Failure[_]]
      providedCalls shouldBe 1
      providerCalls shouldBe 1
    }

    "admit an additive Provided API only when it matches the accepted Workflow ABI" in {
      Given("a generated Workflow and an explicit Cozy Provided operation")
      val workflow = _definition()
      val provided = GeneratedProvidedApiAbi.Definition(
        schemaVersion = GeneratedProvidedApiAbi.schemaVersion,
        generator = GeneratedProvidedApiAbi.generatorIdentity,
        identity = workflow.workflow.identity.value,
        version = workflow.workflow.revision.value,
        source = GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
          GeneratedProvidedApiAbi.SourceIdentity(Some(workflow.workflow.source.root.line)),
          GeneratedProvidedApiAbi.SourceIdentity(Some(workflow.workflow.source.definition.line))
        ),
        providedOperations = Vector(GeneratedProvidedApiAbi.Operation(
          "WorkflowService", "beginReview", Some("ReviewContext"), Some("ReviewResult"),
          GeneratedProvidedApiAbi.SourceIdentity(Some(72))
        ))
      )
      def component(value: GeneratedProvidedApiAbi.Definition): Component =
        _initialized_component(
          TestComponentFactory.emptySubsystem("generated_provided_api_admission"),
          new Component() with GeneratedWorkflowMetadataProvider with GeneratedProvidedApiMetadataProvider {
            override def generatedWorkflowDefinitions: Vector[Definition] = Vector(workflow)
            override def generatedProvidedApiDefinitions: Vector[GeneratedProvidedApiAbi.Definition] = Vector(value)
          }
        )

      When("ComponentFactory admits matching metadata and rejects incompatible producer facts")
      val admitted = component(provided)
      val malformed = Vector(
        provided.copy(version = "other-revision"),
        provided.copy(schemaVersion = "unsupported"),
        provided.copy(generator = "unknown-generator"),
        provided.copy(source = provided.source.copy(definition = GeneratedProvidedApiAbi.SourceIdentity(Some(21)))),
        provided.copy(providedOperations = Vector.empty),
        provided.copy(providedOperations = provided.providedOperations ++ provided.providedOperations)
      ).map(component)
      val success = new ComponentFactory().bootstrapC(admitted)
      val failures = malformed.map(new ComponentFactory().bootstrapC(_))

      Then("the explicit operation is retained only after matching Workflow admission")
      success.toOption shouldBe Some(admitted)
      admitted.admittedGeneratedProvidedApiMetadata shouldBe Vector(provided)
      failures.foreach {
        case Consequence.Failure(_) => succeed
        case _ => fail("incompatible Provided API metadata must stop bootstrap")
      }
      malformed.foreach { rejected =>
        rejected.admittedGeneratedProvidedApiMetadata shouldBe empty
        rejected.collectionsBootstrapped shouldBe false
      }
    }

    "admit the complete pinned WorkflowProducer declaration without runtime adoption" in {
      Given("the Cozy 62.3 WorkflowProducer declaration with source-correlated structure")
      val subsystem = TestComponentFactory.emptySubsystem("generated_workflow_abi_success")
      val definition = _definition()
      val component = _initialized_component(
        subsystem,
        new Component() with GeneratedWorkflowMetadataProvider {
          override def generatedWorkflowDefinitions: Vector[Definition] = Vector(definition)
        }
      )

      When("ComponentFactory admits component-provided generated metadata")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the complete typed metadata is retained without workflow runtime registration")
      result.toOption shouldBe Some(component)
      component.admittedGeneratedWorkflowMetadata shouldBe Vector(definition)
      definition.workflow.identity shouldBe WorkflowIdentity("WorkflowProducer")
      definition.workflow.revision shouldBe WorkflowRevision("workflow-producer-v1")
      definition.compositeStateMachine.states.map(_.identity.value) shouldBe Vector("Pending", "Approved")
      definition.actions.map(_.identity.value) shouldBe Vector(
        "BuildProject",
        "RunTests",
        "ReviewChange",
        "CommitChanges"
      )
      definition.actions.foreach(_.kind shouldBe ActionKind.Operation)
      definition.actions.map(_.operation.key) shouldBe Vector(
        "WorkflowService.buildProject",
        "WorkflowService.runTests",
        "WorkflowService.reviewChange",
        "WorkflowService.commitChanges"
      )
      definition.actions.map(_.operation.inputType) shouldBe Vector.fill(4)(Some(TypeIdentity("ReviewContext")))
      definition.actions.map(_.operation.resultType) shouldBe Vector.fill(4)(Some(TypeIdentity("ReviewResult")))
      definition.requiredSpis.map(_.identity.value) shouldBe Vector("review-change-capability")
      definition.schemaShapes.map(_.identity).toSet shouldBe requiredSchemaShapes.keySet
      component.eventReception shouldBe None
      subsystem.workflowEngine.definitions shouldBe empty
    }

    "discover valid generated metadata from the factory when the component has no provider" in {
      Given("a Component whose factory alone supplies the complete pinned declaration")
      val definition = _definition()
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("generated_workflow_abi_factory_success"),
        new Component() {},
        Some(new GeneratedMetadataFactory(Vector(definition)))
      )

      When("ComponentFactory bootstraps the component")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the factory fallback admits and retains only its metadata")
      result.toOption shouldBe Some(component)
      component.admittedGeneratedWorkflowMetadata shouldBe Vector(definition)
    }

    "prefer component generated metadata over factory generated metadata" in {
      Given("different complete declarations on a component and its factory")
      val componentdefinition = _definition(workflow = "ComponentWorkflow")
      val factorydefinition = _definition(workflow = "FactoryWorkflow")
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("generated_workflow_abi_precedence"),
        new Component() with GeneratedWorkflowMetadataProvider {
          override def generatedWorkflowDefinitions: Vector[Definition] = Vector(componentdefinition)
        },
        Some(new GeneratedMetadataFactory(Vector(factorydefinition)))
      )

      When("ComponentFactory bootstraps the component")
      val result = new ComponentFactory().bootstrapC(component)

      Then("the component provider retains precedence over the factory fallback")
      result.toOption shouldBe Some(component)
      component.admittedGeneratedWorkflowMetadata shouldBe Vector(componentdefinition)
    }

    "fail closed for empty and malformed factory-only metadata" in {
      Given("factory-only providers with no declaration and with an unknown schema shape")
      val emptycomponent = _factory_component(Vector.empty)
      val malformedcomponent = _factory_component(Vector(
        _definition().copy(schemaShapes = _definition().schemaShapes :+
          SchemaShapeDescriptor("UnknownShape", Set("value"), _abi_source))
      ))

      When("ComponentFactory bootstraps each component")
      val emptyresult = new ComponentFactory().bootstrapC(emptycomponent)
      val malformedresult = new ComponentFactory().bootstrapC(malformedcomponent)

      Then("both failures stop bootstrap before metadata retention")
      _diagnostic(emptyresult).code shouldBe DiagnosticCode.MissingDefinitions
      _diagnostic(malformedresult).code shouldBe DiagnosticCode.UnknownSchemaShape
      emptycomponent.admittedGeneratedWorkflowMetadata shouldBe empty
      malformedcomponent.admittedGeneratedWorkflowMetadata shouldBe empty
      emptycomponent.collectionsBootstrapped shouldBe false
      malformedcomponent.collectionsBootstrapped shouldBe false
    }

    "fail closed for missing unknown or incompatible structure and schema shapes" in {
      Given("complete declarations whose required structural or ABI-schema facts are changed")
      val missingstates = _definition().copy(
        compositeStateMachine = _definition().compositeStateMachine.copy(states = Vector.empty)
      )
      val missingshape = _definition().copy(
        schemaShapes = _definition().schemaShapes.filterNot(_.identity == "Continuation")
      )
      val unknownshape = _definition().copy(
        schemaShapes = _definition().schemaShapes :+
          SchemaShapeDescriptor("Unexpected", Set("value"), _abi_source)
      )
      val incompatibleshape = _definition().copy(
        schemaShapes = _definition().schemaShapes.map {
          case shape if shape.identity == "ActionExecution" =>
            shape.copy(members = Set("Completed", "Failed"))
          case shape => shape
        }
      )

      When("ComponentFactory admits the altered declarations")
      val actual = Vector(
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(missingstates)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(missingshape)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(unknownshape)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(incompatibleshape)))).code
      )

      Then("the stable diagnostics identify the structural and version-bound shape violations")
      actual shouldBe Vector(
        DiagnosticCode.MissingRequiredStructure,
        DiagnosticCode.MissingRequiredSchemaShape,
        DiagnosticCode.UnknownSchemaShape,
        DiagnosticCode.IncompatibleSchemaShape
      )
    }

    "fail closed when a Required SPI does not bind a declared Action and its Operation" in {
      Given("complete declarations with a missing action reference and an incompatible operation")
      val missingaction = _definition().copy(
        requiredSpis = Vector(_definition().requiredSpis.head.copy(
          actionIdentity = ActionIdentity("NotDeclared")
        ))
      )
      val mismatchedoperation = _definition().copy(
        requiredSpis = Vector(_definition().requiredSpis.head.copy(
          operation = _operation("differentOperation", _review_change_source)
        ))
      )

      When("ComponentFactory admits each Required SPI descriptor")
      val actual = Vector(
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(missingaction)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(mismatchedoperation)))).code
      )

      Then("each descriptor fails with its stable binding diagnostic")
      actual shouldBe Vector(
        DiagnosticCode.RequiredSpiActionMismatch,
        DiagnosticCode.RequiredSpiOperationMismatch
      )
    }

    "fail closed for incompatible producer identity and duplicate action identity" in {
      Given("an unsupported producer declaration and otherwise complete duplicate actions")
      val unsupported = _definition().copy(
        producerAbiIdentity = ProducerAbiIdentity("workflow-producer-v2")
      )
      val duplicateactions = Vector(
        _definition(workflow = "ActionWorkflowOne"),
        _definition(workflow = "ActionWorkflowTwo").copy(
          requiredSpis = Vector(_definition().requiredSpis.head.copy(
            identity = RequiredSpiIdentity("duplicate-action-capability")
          ))
        )
      )

      When("each declaration set crosses ComponentFactory")
      val actual = Vector(
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(unsupported)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(duplicateactions))).code
      )

      Then("compatibility and duplicate checks preserve their stable diagnostics")
      actual shouldBe Vector(
        DiagnosticCode.UnsupportedProducerAbi,
        DiagnosticCode.DuplicateActionIdentity
      )
    }

    "fail closed for incompatible Workflow revision, duplicate State, and duplicate schema-shape identity" in {
      Given("complete declarations with one incompatible revision and per-Workflow duplicate descriptors")
      val revisiondefinition = _definition()
      val incompatiblerevision = revisiondefinition.copy(
        workflow = revisiondefinition.workflow.copy(
          revision = WorkflowRevision("workflow-producer-v2")
        )
      )
      val statedefinition = _definition()
      val duplicatestate = statedefinition.compositeStateMachine.states.head
      val duplicatestates = statedefinition.copy(
        compositeStateMachine = statedefinition.compositeStateMachine.copy(
          states = statedefinition.compositeStateMachine.states :+ duplicatestate
        )
      )
      val shapedefinition = _definition()
      val duplicateshapes = shapedefinition.copy(
        schemaShapes = shapedefinition.schemaShapes :+ shapedefinition.schemaShapes.head
      )

      When("ComponentFactory admits each altered generated declaration")
      val actual = Vector(
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(incompatiblerevision)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(duplicatestates)))).code,
        _diagnostic(new ComponentFactory().bootstrapC(_provider_component(Vector(duplicateshapes)))).code
      )

      Then("each version or duplicate descriptor violation returns its stable diagnostic")
      actual shouldBe Vector(
        DiagnosticCode.UnsupportedWorkflowRevision,
        DiagnosticCode.DuplicateStateIdentity,
        DiagnosticCode.DuplicateSchemaShapeIdentity
      )
    }

    "not adopt legacy handwritten workflow definitions as generated metadata" in {
      Given("a legacy workflow definition without a generated metadata provider")
      val legacy = WorkflowDefinition(
        name = "legacy-workflow",
        registrations = Vector.empty
      )
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("generated_workflow_abi_legacy"),
        new Component() {
          override def workflowDefinitions: Vector[WorkflowDefinition] = Vector(legacy)
        }
      )

      When("the ordinary component bootstrap runs")
      val result = new ComponentFactory().bootstrapC(component)

      Then("legacy workflow behavior remains separate from generated ABI admission")
      result.toOption shouldBe Some(component)
      component.workflowDefinitions shouldBe Vector(legacy)
      component.admittedGeneratedWorkflowMetadata shouldBe empty
    }
  }

  private final class GeneratedMetadataFactory(
    definitions: Vector[Definition]
  ) extends Component.Factory
    with GeneratedWorkflowMetadataProvider {
    override def generatedWorkflowDefinitions: Vector[Definition] = definitions

    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("test fixture factory does not create components")

    override protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      throw new UnsupportedOperationException("test fixture factory does not create component cores")
  }

  private def _provider_component(
    definitions: Vector[Definition]
  ): Component =
    _initialized_component(
      TestComponentFactory.emptySubsystem("generated_workflow_abi_provider"),
      new Component() with GeneratedWorkflowMetadataProvider {
        override def generatedWorkflowDefinitions: Vector[Definition] = definitions
      }
    )

  private def _factory_component(
    definitions: Vector[Definition]
  ): Component =
    _initialized_component(
      TestComponentFactory.emptySubsystem("generated_workflow_abi_factory"),
      new Component() {},
      Some(new GeneratedMetadataFactory(definitions))
    )

  private def _initialized_component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: Component,
    componentfactory: Option[Component.Factory] = None,
    protocol: Protocol = Protocol.empty
  ): Component = {
    val componentid = ComponentId("org.goldenport.cncf.test.GeneratedWorkflowAbiSpec")
    val core = componentfactory match {
      case Some(factory) =>
        Component.Core.create(
          name = componentid.name,
          componentId = componentid,
          instanceId = ComponentInstanceId.default(componentid),
          protocol = protocol,
          factory = factory
        )
      case None =>
        Component.Core.create(
          name = componentid.name,
          componentId = componentid,
          instanceId = ComponentInstanceId.default(componentid),
          protocol = protocol
        )
    }
    component.initialize(ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    ))
  }

  private def _provided_definition(
    workflow: Definition
  ): GeneratedProvidedApiAbi.Definition =
    GeneratedProvidedApiAbi.Definition(
      schemaVersion = GeneratedProvidedApiAbi.schemaVersion,
      generator = GeneratedProvidedApiAbi.generatorIdentity,
      identity = workflow.workflow.identity.value,
      version = workflow.workflow.revision.value,
      source = GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
        GeneratedProvidedApiAbi.SourceIdentity(Some(workflow.workflow.source.root.line)),
        GeneratedProvidedApiAbi.SourceIdentity(Some(workflow.workflow.source.definition.line))
      ),
      providedOperations = Vector(GeneratedProvidedApiAbi.Operation(
        "WorkflowService", "beginReview", Some("ReviewContext"), Some("ReviewResult"),
        GeneratedProvidedApiAbi.SourceIdentity(Some(72))
      ))
    )

  private def _definition(
    workflow: String = "WorkflowProducer"
  ): Definition = {
    val root = SourceLocation(
      "src/test/resources/modeler/skill-driven-workflow-producer.cml",
      1
    )
    val definition = root.copy(line = 20)
    val composite = root.copy(line = 23)
    val states = Vector(
      StateDescriptor(StateIdentity("Pending"), root.copy(line = 31)),
      StateDescriptor(StateIdentity("Approved"), root.copy(line = 33))
    )
    val actionnames = Vector(
      "BuildProject" -> "buildProject",
      "RunTests" -> "runTests",
      "ReviewChange" -> "reviewChange",
      "CommitChanges" -> "commitChanges"
    )
    val actions = actionnames.zipWithIndex.map { case ((name, operation), index) =>
      val source = root.copy(line = 46 + index * 5)
      ActionDescriptor(
        ActionIdentity(name),
        ActionKind.Operation,
        _operation(operation, source),
        Some("review.subject"),
        source
      )
    }
    val reviewaction = actions.find(_.identity == ActionIdentity("ReviewChange")).get
    Definition(
      producerAbiIdentity = acceptedProducerAbiIdentity,
      workflowAbiIdentity = acceptedWorkflowAbiIdentity,
      bootstrapSchemaIdentity = acceptedBootstrapSchemaIdentity,
      producerRevision = acceptedProducerRevision,
      fixtureSha256 = acceptedFixtureSha256,
      workflow = WorkflowDescriptor(
        WorkflowIdentity(workflow),
        WorkflowRevision("workflow-producer-v1"),
        WorkflowSourceCorrelation(root, definition)
      ),
      compositeStateMachine = CompositeStateMachineDescriptor(
        CompositeStateMachineIdentity("review"),
        StateMachineIdentity("ReviewLifecycle"),
        states,
        composite
      ),
      actions = actions,
      requiredSpis = Vector(RequiredSpiDescriptor(
        RequiredSpiIdentity("review-change-capability"),
        reviewaction.identity,
        reviewaction.operation,
        root.copy(line = 68),
        reviewaction.sourceLocation
      )),
      schemaShapes = requiredSchemaShapes.toVector.sortBy(_._1).map {
        case (identity, members) => SchemaShapeDescriptor(identity, members, _abi_source)
      }
    )
  }

  private def _operation(
    operation: String,
    source: SourceLocation
  ): OperationDescriptor =
    OperationDescriptor(
      ServiceIdentity("WorkflowService"),
      OperationIdentity(operation),
      Some(TypeIdentity("ReviewContext")),
      Some(TypeIdentity("ReviewResult")),
      source
    )

  private val _review_change_source = SourceLocation(
    "src/test/resources/modeler/skill-driven-workflow-producer.cml",
    56
  )

  private val _abi_source = SourceLocation(
    "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala",
    1
  )

  private def _diagnostic(
    result: Consequence[Component]
  ): Diagnostic =
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.getException match {
          case Some(exception: GeneratedWorkflowAbiAdmissionException) =>
            exception.diagnostic
          case other =>
            fail(s"expected generated workflow admission diagnostic but got $other")
        }
      case _ =>
        fail("expected generated workflow admission to fail")
    }
}
