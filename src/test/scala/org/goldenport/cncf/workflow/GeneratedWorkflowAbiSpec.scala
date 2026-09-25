package org.goldenport.cncf.workflow

import cats.data.NonEmptyVector
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.{Conclusion, Consequence}
import cats.free.Free
import org.goldenport.ConsequenceT
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.statemachine.{ExecutionPlan, ExecutionPlanExecutor, ResolvedAction, StateMachineRequiredOperationAction}
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
    "bind the CNCF test declaration to the pinned Cozy 62.3 generated ABI" in {
      Given("the pinned Cozy 62.3 Workflow ABI resource")
      val source = scala.io.Source.fromInputStream(
        classOf[GeneratedWorkflowAbiSpec].getResourceAsStream(
          "/workflow/skill-driven-workflow-producer-abi.json"
        ), "UTF-8"
      )
      val wire = try source.mkString.stripSuffix("\n") finally source.close()
      When("the receiver parses its source-correlated declaration")
      val sha256 = MessageDigest.getInstance("SHA-256")
        .digest(wire.getBytes(StandardCharsets.UTF_8))
        .map(b => f"${b & 0xff}%02x").mkString
      val generated = parse(wire).toOption.getOrElse(fail("pinned Cozy ABI must parse")).hcursor
      val workflow = generated.downField("workflows").downN(0)
      val admitted = _definition()
      Then("the generated identity, source locations, Actions, and Required SPI match CNCF admission")
      sha256 shouldBe "c3f11c6160691dc747ab9b614ab040cb288d25bd83196b205891e1b6e365c013"
      generated.get[String]("schemaVersion").toOption shouldBe Some(acceptedWorkflowAbiIdentity.value)
      workflow.get[String]("identity").toOption shouldBe Some(admitted.workflow.identity.value)
      workflow.get[String]("version").toOption shouldBe Some(admitted.workflow.revision.value)
      workflow.downField("source").downField("root").get[Int]("line").toOption shouldBe
        Some(admitted.workflow.source.root.line)
      workflow.downField("source").downField("definition").get[Int]("line").toOption shouldBe
        Some(admitted.workflow.source.definition.line)
      val states = workflow.downField("states").as[Vector[Json]].toOption.getOrElse(
        fail("pinned Cozy states must decode")
      )
      states.map { state =>
        val cursor = state.hcursor
        (cursor.get[String]("name").toOption, cursor.downField("source").get[Int]("line").toOption)
      } shouldBe admitted.compositeStateMachine.states.map { state =>
        (Some(state.identity.value), Some(state.sourceLocation.line))
      }
      val actions = workflow.downField("actions").as[Vector[Json]].toOption.getOrElse(
        fail("pinned Cozy actions must decode")
      )
      actions.map { action =>
        val cursor = action.hcursor
        (
          cursor.get[String]("identity").toOption,
          cursor.get[String]("kind").toOption,
          cursor.downField("operation").get[String]("service").toOption,
          cursor.downField("operation").get[String]("name").toOption,
          cursor.downField("operation").get[String]("inputType").toOption,
          cursor.downField("operation").get[String]("resultType").toOption,
          cursor.get[String]("inputBinding").toOption,
          cursor.downField("source").get[Int]("line").toOption
        )
      } shouldBe admitted.actions.map { action =>
        (
          Some(action.identity.value), Some(action.kind.value), Some(action.operation.serviceIdentity.value),
          Some(action.operation.operationIdentity.value), action.operation.inputType.map(_.value),
          action.operation.resultType.map(_.value), action.inputBinding, Some(action.sourceLocation.line)
        )
      }
      val required = workflow.downField("requiredSpi").as[Vector[Json]].toOption.getOrElse(
        fail("pinned Cozy Required SPI must decode")
      )
      required.map { spi =>
        val cursor = spi.hcursor
        (
          cursor.get[String]("capability").toOption,
          cursor.get[String]("actionIdentity").toOption,
          cursor.downField("operation").get[String]("service").toOption,
          cursor.downField("operation").get[String]("name").toOption,
          cursor.downField("source").get[Int]("line").toOption
        )
      } shouldBe admitted.requiredSpis.map { spi =>
        (
          Some(spi.identity.value), Some(spi.actionIdentity.value),
          Some(spi.operation.serviceIdentity.value), Some(spi.operation.operationIdentity.value),
          Some(spi.capabilitySource.line)
        )
      }
    }

    "execute the pinned internal actions, suspend ReviewChange, and resume CommitChanges" in {
      Given("the pinned Workflow with deterministic internal Actions and a bound ReviewChange Provider")
      val workflow = _definition()
      workflow.actions.map(_.identity.value) shouldBe Vector(
        "BuildProject", "RunTests", "ReviewChange", "CommitChanges"
      )
      val declared = workflow.requiredSpis.head
      val required = StateMachineRequiredOperation(
        StateMachineRequiredOperationIdentity(declared.identity.value),
        declared.actionIdentity.value,
        StateMachineOperationIdentity(
          declared.operation.serviceIdentity.value, declared.operation.operationIdentity.value
        ),
        declared.operation.inputType.map(x => StateMachineInputTypeReference(x.value)),
        declared.operation.resultType.map(x => StateMachineResultTypeReference(x.value)),
        StateMachineRequiredOperationMetadata(
          ContextContract("review-context", Vector.empty, Vector.empty),
          CompletionContract("review-completion", Vector.empty),
          EvidenceContract("review-evidence", Vector.empty), Vector.empty
        )
      )
      val run = StateMachineRunIdentity("fixture-run")
      val context = ContextBundle(
        "review", Vector.empty, Vector.empty, ContextSnapshot(workflow.workflow.revision.value)
      )
      val continuation = Continuation(
        run, ContinuationIdentity("fixture-review"), StateMachineRevision("1"), required, context
      )
      val input = Some(StateMachineOperationInput(
        StateMachineInputTypeReference("ReviewContext"), ContextReference("review-input", "1")
      ))
      val request = ProviderExecutionRequest(run, required, input, context)
      val trace = scala.collection.mutable.ArrayBuffer.empty[String]
      val provider = new StateMachineProgramProvider {
        val identity = ProviderIdentity("fixture-review-provider")
        def program(actual: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          actual shouldBe request
          trace += "ReviewChange"
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Suspended(continuation)
          )
        }
      }
      val continuationstore = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(continuationstore)
      val subsystem = TestComponentFactory.emptySubsystem("cozy-fixture-vertical-slice")
      try {
        val component = _initialized_component(
          subsystem,
          new Component() with GeneratedWorkflowMetadataProvider with StateMachineProviderSource
            with ContinuationRuntimeSource {
            override def generatedWorkflowDefinitions: Vector[Definition] = Vector(workflow)
            override def stateMachineProviderBindings: Vector[StateMachineProviderBinding] =
              Vector(StateMachineProviderBinding(required.identity, provider.identity))
            override def stateMachineProviders: Vector[StateMachineProvider] = Vector(provider)
            override def continuationRuntimeOption: Option[ContinuationRuntime] = Some(runtime)
          }
        )
        new ComponentFactory().bootstrapC(component).toOption shouldBe Some(component)
        val root = ExecutionContext.create()
        val execution = root.withScope(Component.Context(
          "cozy-fixture-vertical-slice", root.scope, component, ComponentOrigin.Embed
        ))
        def internal(name: String): ResolvedAction[Unit, Unit] =
          new ResolvedAction[Unit, Unit] {
            def program(state: Unit, event: Unit): ExecUowM[ActionExecution] =
              ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
                ActionExecution.Completed(StateMachineOperationResult(
                  StateMachineResultTypeReference("ReviewResult"), ContextReference(name, "1")
                ))
              ).map { outcome =>
                trace += name
                outcome
              }
          }
        val prefix = ExecutionPlan[Unit, Unit](
          Vector.empty,
          Vector(
            internal(workflow.actions(0).identity.value),
            internal(workflow.actions(1).identity.value),
            new StateMachineRequiredOperationAction[Unit, Unit]((_, _) => request),
            internal(workflow.actions(3).identity.value)
          ),
          Vector.empty
        )
        val binding = WorkflowInstancePersistence.bindDefinitionC(workflow).toOption.getOrElse(
          fail("pinned fixture must bind to WorkflowInstance")
        )
        val instance = WorkflowInstancePersistence.InstanceRecord(
          WorkflowInstancePersistence.InstanceIdentity("fixture-instance"), binding,
          WorkflowInstancePersistence.initialRevision, WorkflowInstancePersistence.Lifecycle.NotStarted,
          None, WorkflowInstancePersistence.CorrelationReference("fixture-start"),
          WorkflowInstancePersistence.CausationReference("fixture-request"), None, None, None, Vector.empty
        )
        val storeidentity = WorkflowInstancePersistence.WorkflowStoreIdentity("fixture-store")
        val configuration = WorkflowInstancePersistence.Configuration(
          WorkflowInstancePersistence.NamedWorkflowStore("fixture", storeidentity),
          WorkflowInstancePersistence.MigrationPolicy("fixture-v1"),
          WorkflowInstancePersistence.RetentionPolicy("fixture-v1"),
          WorkflowInstancePersistence.LeasePolicy("fixture-v1"),
          WorkflowInstancePersistence.DeliveryConfiguration.SameStore(storeidentity)
        )
        val records = scala.collection.mutable.Map.empty[
          WorkflowInstancePersistence.InstanceIdentity, WorkflowInstancePersistence.InstanceRecord
        ]
        val instances = new WorkflowInstancePersistence {
          def create(
            actual: WorkflowInstancePersistence.Configuration,
            record: WorkflowInstancePersistence.InstanceRecord
          ): Consequence[WorkflowInstancePersistence.InstanceRecord] =
            if (actual != configuration || records.contains(record.identity))
              Consequence.stateConflict("fixture WorkflowInstance create is duplicate or misconfigured")
            else record.validateC.map { admitted =>
              records.update(admitted.identity, admitted)
              admitted
            }
          def load(
            actual: WorkflowInstancePersistence.Configuration,
            identity: WorkflowInstancePersistence.InstanceIdentity
          ): Consequence[Option[WorkflowInstancePersistence.InstanceRecord]] =
            if (actual != configuration) Consequence.stateConflict("fixture WorkflowInstance store mismatch")
            else Consequence.success(records.get(identity))
          def append(
            actual: WorkflowInstancePersistence.Configuration,
            identity: WorkflowInstancePersistence.InstanceIdentity,
            expectedRevision: WorkflowInstancePersistence.InstanceRevision,
            entry: WorkflowInstancePersistence.HistoryEntry
          ): Consequence[WorkflowInstancePersistence.InstanceRecord] =
            if (actual != configuration) Consequence.stateConflict("fixture WorkflowInstance store mismatch")
            else records.get(identity) match {
              case Some(current) => current.appendC(expectedRevision, entry).map { next =>
                records.update(identity, next)
                next
              }
              case None => Consequence.stateConflict("fixture WorkflowInstance is unavailable for append")
            }
        }
        When("the generated Action prefix commits and stores its suspension boundary")
        val startunitofwork = new UnitOfWork(execution)
        trace.toVector shouldBe empty
        runtime.claimC(continuation.continuationId) shouldBe a[Consequence.Failure[_]]
        ExecutionPlanExecutor.executeCommittingAfterC(
          prefix, (), (), startunitofwork, runtime,
          suspended => {
            suspended shouldBe continuation
            val boundary = WorkflowInstancePersistence.SuspensionBoundary(
              WorkflowInstancePersistence.ContinuationIdentity(suspended.continuationId.value),
              WorkflowInstancePersistence.InstanceRevision(1L),
              WorkflowInstancePersistence.ContextSnapshotReference("fixture-snapshot"),
              WorkflowInstancePersistence.CompletionReference(required.metadata.completionContract.identity),
              WorkflowInstancePersistence.EvidenceReference(required.metadata.evidenceContract.identity),
              WorkflowInstancePersistence.CorrelationReference("fixture-resume")
            )
            instance.appendC(WorkflowInstancePersistence.initialRevision,
              WorkflowInstancePersistence.HistoryEntry(
                WorkflowInstancePersistence.HistorySequence(1L),
                WorkflowInstancePersistence.InstanceRevision(1L),
                WorkflowInstancePersistence.Lifecycle.Active,
                Some(WorkflowInstancePersistence.ProgressionReference("ReviewChange")),
                WorkflowInstancePersistence.CorrelationReference("fixture-start"),
                WorkflowInstancePersistence.CausationReference("fixture-request"),
                None, None, Some(boundary)
              )
            ).flatMap(instances.create(configuration, _)).map(_ => ())
          }
        ) shouldBe Consequence.success(Some(continuation))
        Then("only BuildProject, RunTests, and ReviewChange have executed")
        startunitofwork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
        trace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange")
        val active = instances.load(configuration, instance.identity).toOption.flatten.getOrElse(
          fail("suspended fixture WorkflowInstance must be durable before work is issued")
        )
        active.lifecycle shouldBe WorkflowInstancePersistence.Lifecycle.Active
        active.suspension.map(_.continuationIdentity.value) shouldBe Some(continuation.continuationId.value)
        val recovered = new PersistentContinuationRuntime(continuationstore)
        recovered.claimC(continuation.continuationId).toOption.getOrElse(
          fail("committed fixture ReviewChange must be claimable after runtime recreation")
        )
        val handle = WorkflowHandle.fromRecordC(component.componentId, active).toOption.getOrElse(
          fail("fixture instance must yield a WorkflowHandle")
        )
        When("the recovered claim is projected to a typed WorkOrder")
        val issuedstore = new IssuedWorkOrderPersistence.InMemory[String]
        val issued = issueRecoveredWorkOrderC(
          handle, continuation.continuationId, Some(TypedValue("ReviewContext", "review-input")),
          StateMachineOperationIdentity("WorkflowService", "submitReview"),
          ExecutionRequirement(Vector(CapabilityRequirement("review")), RiskLevel("standard"),
            ReasoningLevel.Deep, true),
          MinimalPresentation("Review change", "Waiting for a reviewer"), recovered, issuedstore
        ).toOption.getOrElse(fail("claimed fixture ReviewChange must project to WorkOrder"))
        val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
          val typeIdentity = "ReviewContext"
          def encode(value: String): Json = Json.fromString(value)
          def decode(value: Json): Either[String, String] = value.asString.toRight("expected ReviewContext")
        }
        val orderwire = WorkflowWorkOrderJsonV1.encodeC(issued, codec).toOption.getOrElse(
          fail("fixture WorkOrder must encode")
        )
        Then("the WorkOrder round-trips without exposing an internal Action")
        WorkflowWorkOrderJsonV1.decodeC(orderwire, codec) shouldBe Consequence.success(issued)
        When("a Skill completion is normalized through the persisted Continuation SPI")
        val skillresult = SkillWorkResult(
          run, continuation.continuationId, continuation.expectedRevision, context.snapshot,
          TypedValue("ReviewResult", "approved"), ContextReference("review-result", "1"),
          Vector.empty, Vector.empty, Some("codex-fixture"), Some("fixture-model"),
          "reviewer-profile-v1", "mapping-v1"
        )
        val skilladapter = ContinuationSpiAdapter.bindC(
          component.componentId, recovered, issuedstore,
          new SkillWorkResultNormalizerV1[String, String]
        ).toOption.getOrElse(fail("recovered fixture Skill adapter must bind"))
        skilladapter.admitC(continuation.continuationId,
          skillresult.copy(expectedRevision = StateMachineRevision("stale"))) shouldBe
          a[Consequence.Failure[_]]
        skilladapter.admitC(continuation.continuationId,
          skillresult.copy(mappingPolicyVersion = "")) shouldBe a[Consequence.Failure[_]]
        val result = skilladapter.admitC(continuation.continuationId, skillresult).toOption.getOrElse(
          fail("recovered fixture Skill completion must normalize")
        )
        Then("the normalized result preserves the requested requirement and dispatch evidence")
        result.evidence.skillDispatch shouldBe Some(SkillDispatchEvidence(
          issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]].requirement,
          "reviewer-profile-v1", "mapping-v1"
        ))
        val resultcodec = new WorkflowResultJsonV1.PayloadCodec[String] {
          val typeIdentity = "ReviewResult"
          def encode(value: String): Json = Json.fromString(value)
          def decode(value: Json): Either[String, String] = value.asString.toRight("expected ReviewResult")
        }
        val resultwire = WorkflowResultJsonV1.encodeC(result, resultcodec).toOption.getOrElse(
          fail("fixture ReviewChange result must encode")
        )
        val submitted = WorkflowResultJsonV1.decodeC(resultwire, resultcodec).toOption.getOrElse(
          fail("fixture ReviewChange result must decode")
        )
        val freshunits = scala.collection.mutable.ArrayBuffer.empty[UnitOfWork]
        def fresh(): UnitOfWork = {
          val value = new UnitOfWork(execution)
          freshunits += value
          value
        }
        records.update(instance.identity, active.copy(
          suspension = None, history = active.history.map(_.copy(suspension = None))
        ))
        When("the saved suspension is absent and then restored for a fresh-UnitOfWork resume")
        resumePersistedWorkOrderC(
          component.componentId, submitted, issuedstore, recovered, instances, configuration, () => fresh(),
          Some(internal(workflow.actions(3).identity.value).program((), ()))
        ) shouldBe a[Consequence.Failure[_]]
        trace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange")
        freshunits shouldBe empty
        records.update(instance.identity, active)
        resumePersistedWorkOrderC(
          component.componentId, submitted, issuedstore, recovered, instances, configuration, () => fresh(),
          Some(internal(workflow.actions(3).identity.value).program((), ()))
        ).isSuccess shouldBe true
        Then("only the restored boundary runs CommitChanges once in a fresh UnitOfWork")
        freshunits.size shouldBe 1
        freshunits.head should not be startunitofwork
        freshunits.head.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
        trace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange", "CommitChanges")
        resumePersistedWorkOrderC(
          component.componentId, submitted, issuedstore, recovered, instances, configuration, () => fresh(),
          Some(internal(workflow.actions(3).identity.value).program((), ()))
        ) shouldBe a[Consequence.Failure[_]]
        trace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange", "CommitChanges")
      } finally {
        subsystem.shutdown()
      }
    }

    "route one declared Provided Operation through the public Service and ActionEngine commit" in {
      val placedoutcomes = scala.collection.mutable.ArrayBuffer.empty[
        (WorkflowDescriptor, Vector[String], ProviderIdentity)
      ]
      Vector((false, false), (true, false), (true, true)).foreach { case (placed, replacementProvider) =>
      Given("declared Start and completion Operations, a program Provider, and persistent suspension ports")
      val workflow = if (placed) _placed_definition() else _definition()
      val providedbase = _provided_definition(workflow)
      val provided = providedbase.copy(providedOperations =
        Vector(
          providedbase.providedOperations.head.copy(
            source = GeneratedProvidedApiAbi.SourceIdentity(Some(if (placed) 151 else 72))
          ),
          GeneratedProvidedApiAbi.Operation(
            "WorkflowService", "submitReview", Some("ReviewResult"), Some("WorkflowInteraction"),
            GeneratedProvidedApiAbi.SourceIdentity(Some(if (placed) 155 else 73))
          )
        )
      )
      val request = StateMachineProvidedApiRequest(
        workflow.workflow.identity.value, workflow.workflow.revision.value,
        StateMachineRunIdentity("service-run"),
        StateMachineOperationIdentity("WorkflowService", "beginReview"),
        Some(StateMachineOperationInput(StateMachineInputTypeReference("ReviewContext"), ContextReference("input", "1"))),
        ContextBundle("review", Vector.empty, Vector.empty,
          ContextSnapshot(workflow.workflow.revision.value))
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
      var rejectwrite = false
      var indeterminatewrite = false
      val persistence = new ContinuationRuntimePersistence {
        def createC(record: ContinuationRuntimePersistence.Record): Consequence[Unit] =
          if (rejectwrite) Consequence.stateConflict("planned public Service continuation write failure")
          else if (indeterminatewrite) backing.createC(record).flatMap(_ =>
            Consequence.stateConflict("planned indeterminate public Service continuation write")
          )
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
      val storeidentity = WorkflowInstancePersistence.WorkflowStoreIdentity("service-workflow-store")
      val configuration = WorkflowInstancePersistence.Configuration(
        WorkflowInstancePersistence.NamedWorkflowStore("service-workflow", storeidentity),
        WorkflowInstancePersistence.MigrationPolicy("fixture-v1"),
        WorkflowInstancePersistence.RetentionPolicy("fixture-v1"),
        WorkflowInstancePersistence.LeasePolicy("fixture-v1"),
        WorkflowInstancePersistence.DeliveryConfiguration.SameStore(storeidentity)
      )
      val records = scala.collection.mutable.Map.empty[
        WorkflowInstancePersistence.InstanceIdentity, WorkflowInstancePersistence.InstanceRecord
      ]
      var rejectinstancewrite = false
      val instances = new WorkflowInstancePersistence {
        def create(
          actual: WorkflowInstancePersistence.Configuration,
          record: WorkflowInstancePersistence.InstanceRecord
        ): Consequence[WorkflowInstancePersistence.InstanceRecord] =
          if (rejectinstancewrite) Consequence.stateConflict("planned public Service instance write failure")
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
      val suspensionpersistence = new StateMachineProvidedApiServiceOperation.SuspensionPersistence {
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
      val actiontrace = scala.collection.mutable.ArrayBuffer.empty[String]
      def internalAction(name: String): ExecUowM[Unit] =
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
          .map { _ =>
            actiontrace += name
            ()
          }
      val provider = new StateMachineProgramProvider {
        val identity = ProviderIdentity(
          if (replacementProvider) "replacement-public-service-review-provider"
          else "public-service-review-provider"
        )
        def program(actual: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          actual.runId shouldBe request.runId
          actiontrace += workflow.actions(2).identity.value
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Suspended(selected)
          )
        }
      }
      var closingexecutions = 0
      val placedprogram = if (placed) Some(GeneratedWorkflowActionProgram.parseC(
        _workflow_resource("skill-driven-workflow-placed-abi.json"),
        _workflow_resource("skill-driven-workflow-placed-action-program.json")
      ).toOption.getOrElse(fail("placed generated plan must parse"))) else None
      def completedAction(name: String, closing: Boolean = false): ResolvedAction[StateMachineProvidedApiRequest, Unit] =
        new ResolvedAction[StateMachineProvidedApiRequest, Unit] {
          def program(actual: StateMachineProvidedApiRequest, event: Unit): ExecUowM[ActionExecution] = {
            actual shouldBe request
            internalAction(name).map { _ =>
              if (closing) closingexecutions += 1
              ActionExecution.Completed(StateMachineOperationResult(
                StateMachineResultTypeReference("ReviewResult"), ContextReference(name, "1")
              ))
            }
          }
        }
      val placedbindings = Vector(
        GeneratedWorkflowActionProgram.Binding("CommitChanges", completedAction("CommitChanges", closing = true)),
        GeneratedWorkflowActionProgram.Binding("ReviewChange",
          new ResolvedAction[StateMachineProvidedApiRequest, Unit] {
            def program(actual: StateMachineProvidedApiRequest, event: Unit): ExecUowM[ActionExecution] = {
              actual shouldBe request
              ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(
                ProviderExecutionRequest(request.runId, required, request.input, request.context)
              )))
            }
          }),
        GeneratedWorkflowActionProgram.Binding("RunTests", completedAction("RunTests")),
        GeneratedWorkflowActionProgram.Binding("BuildProject", completedAction("BuildProject"))
      )
      val placedplan = placedprogram.map(definition => GeneratedWorkflowActionProgram.bindC(
        definition, placedbindings
      ).toOption.getOrElse(fail("placed generated plan must bind")))
      val program: StateMachineProvidedApiProgram = placedprogram match {
        case Some(definition) => GeneratedWorkflowActionProgram.bindProvidedApiProgramC(
          definition, request.operation, placedbindings
        ).toOption.getOrElse(fail("placed Provided program must bind"))
        case None => new StateMachineProvidedApiProgram {
          val workflowIdentity = request.workflowIdentity
          val operation = request.operation
          def program(actual: StateMachineProvidedApiRequest): ExecUowM[ActionExecution] = {
            actual shouldBe request
            internalAction(workflow.actions(0).identity.value).flatMap { _ =>
              internalAction(workflow.actions(1).identity.value).flatMap { _ =>
                ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(
                  ProviderExecutionRequest(request.runId, required, request.input, request.context)
                )))
              }
            }
          }
        }
      }
      val inputcodec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewContext"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] = value.asString.toRight("expected ReviewContext")
      }
      val resultcodec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewResult"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] = value.asString.toRight("expected ReviewResult")
      }
      val startrequest = WorkflowStartRequest(
        request.operation,
        WorkflowInstancePersistence.WorkflowDefinitionIdentity(workflow.workflow.identity.value),
        WorkflowInstancePersistence.WorkflowDefinitionRevision(workflow.workflow.revision.value),
        TypedValue("ReviewContext", "review-input"), "service-start", request.runId.value
      )
      val startjson = WorkflowStartJsonV1.encodeC(startrequest, inputcodec).toOption.getOrElse(
        fail("public Service profile Start must encode")
      )
      val issuedstore = new IssuedWorkOrderPersistence.InMemory[String]
      var componentidentity: Option[ComponentId] = None
      var postcommitresponses = 0
      var rejectprojection = false
      val codec = new StateMachineProvidedApiServiceOperation.Codec {
        def decodeC(raw: Request): Consequence[StateMachineProvidedApiRequest] =
          raw.arguments match {
            case List(Argument("arg1", wire: String, _)) =>
              WorkflowStartJsonV1.decodeBoundC(
                wire, inputcodec, request.operation, startrequest.workflowIdentity, startrequest.workflowRevision
              ).flatMap { decoded =>
                if (decoded == startrequest) Consequence.success(request)
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
          postcommitresponses += 1
          actual shouldBe request
          encoded shouldBe OperationResponse.Scalar("SUSPENDED")
          if (rejectprojection) Consequence.stateConflict("planned public Service response projection failure")
          else outcome match {
            case ActionExecution.Suspended(continuation) =>
              for {
                component <- componentidentity match {
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
                  MinimalPresentation("Review change", "Waiting for a reviewer"), runtime, issuedstore
                )
                json <- WorkflowInteractionJsonV1.encodeC(
                  WorkflowInteraction[String, String](issued.handle, issued.current), inputcodec, resultcodec
                )
              } yield OperationResponse.Scalar(json)
            case _ => Consequence.stateConflict("public Service post-commit outcome is not suspended")
          }
        }
      }
      val operation = StateMachineProvidedApiServiceOperation.bindC(
        provided, provided.providedOperations.head, spec.RequestDefinition(),
        spec.ResponseDefinition.void, codec, Some(suspensionpersistence)
      ).toOption.getOrElse(fail("declared public Service operation must bind"))
      val completioncodec = new WorkflowCompletionServiceOperation.Codec[String] {
        val resultTypeIdentity = "ReviewResult"
        def decodeC(raw: Request): Consequence[ContinuationResult[String]] =
          raw.arguments match {
            case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, resultcodec)
            case _ => Consequence.stateConflict("public completion Result JSON is missing")
          }
        def encodeC(resume: ContinuationRuntime.Resume): Consequence[OperationResponse] =
          Consequence.success(OperationResponse.Scalar(s"RESUMED:${resume.continuation.continuationId.value}"))
        override def encodeAfterCommitC(
          resume: ContinuationRuntime.Resume,
          submitted: ContinuationResult[String],
          completed: Option[WorkflowInstancePersistence.InstanceRecord]
        ): Consequence[OperationResponse] =
          completed match {
            case Some(record) => for {
              terminal <- projectTerminalC(
                submitted.handle, record, submitted.result,
                MinimalPresentation("Review complete", "Approved")
              )
              wire <- WorkflowInteractionJsonV1.encodeC(
                WorkflowInteraction[String, String](terminal.handle, terminal.current), inputcodec, resultcodec
              )
            } yield OperationResponse.Scalar(wire)
            case None => Consequence.stateConflict("public completion did not persist a terminal WorkflowInstance")
          }
      }
      val closingprogram = new WorkflowCompletionServiceOperation.ClosingProgram[String] {
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
          )).map { next =>
            val closing = placedplan match {
              case Some(plan) => ExecutionPlanExecutor.program(
                ExecutionPlan(Vector.empty, Vector.empty, plan.entryActions), request, ()
              )
              case None => ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
                ActionExecution.Completed(StateMachineOperationResult(
                  StateMachineResultTypeReference("ReviewResult"), submitted.resultReference
                ))
              ).map { outcome =>
                closingexecutions += 1
                actiontrace += workflow.actions(3).identity.value
                outcome
              }
            }
            WorkflowCompletionServiceOperation.SelectedClosing(closing, Some(next))
          }
      }
      val completionoperation = WorkflowCompletionServiceOperation.bindC[String, String](
        provided, provided.providedOperations.last, spec.RequestDefinition(),
        spec.ResponseDefinition.void, completioncodec, new PersistentContinuationRuntime(persistence),
        issuedstore, instances, configuration, () => new UnitOfWork(ExecutionContext.create()),
        Some(closingprogram)
      ).toOption.getOrElse(fail("declared public completion Operation must bind"))
      val service = spec.ServiceDefinition(
        name = "WorkflowService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation, completionoperation))
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
        componentidentity = Some(component.componentId)
        subsystem.add(component)
        val raw = Request.of(
          component = component.componentId.name, service = "WorkflowService", operation = "beginReview",
          arguments = List(Argument("arg1", startjson))
        )
        When("the public Start request is missing, mismatched, or malformed")
        subsystem.executeOperationResponse(raw.copy(arguments = Nil)) shouldBe a[Consequence.Failure[_]]
        subsystem.executeOperationResponse(raw.copy(arguments = List(Argument(
          "arg1", startjson.replace("\"beginReview\"", "\"other\"")
        )))) shouldBe a[Consequence.Failure[_]]
        subsystem.executeOperationResponse(raw.copy(arguments = List(Argument(
          "arg1", startjson.replace("\"review-input\"", "1")
        )))) shouldBe a[Consequence.Failure[_]]
        subsystem.executeOperationResponse(raw.copy(arguments = List(Argument(
          "arg1", startjson.replace("\"kind\":", "\"unknown\":1,\"kind\":")
        )))) shouldBe a[Consequence.Failure[_]]
        Then("no Continuation is claimable before a valid Start commits")
        runtime.claimC(selected.continuationId) shouldBe a[Consequence.Failure[_]]
        actiontrace.toVector shouldBe empty
        When("the declared Start Operation runs through the public Service")
        val startresponse = subsystem.executeOperationResponse(raw).toOption.getOrElse(
          fail("public Service Start must return after commit")
        )
        Then("the committed response exposes a persisted Handle and issued WorkOrder")
        postcommitresponses shouldBe 1
        actiontrace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange")
        val orderjson = startresponse match {
          case OperationResponse.Scalar(value: String) => value
          case _ => fail("public Service Start must return a WorkOrder JSON scalar")
        }
        val instanceidentity = WorkflowInstancePersistence.InstanceIdentity(
          s"public-service-${selected.continuationId.value}"
        )
        val active = instances.load(configuration, instanceidentity).toOption.flatten.getOrElse(
          fail("public Service must create the suspended WorkflowInstance before Continuation publication")
        )
        val handle = WorkflowHandle.fromRecordC(component.componentId, active).toOption.getOrElse(
          fail("stored WorkflowInstance must produce the public Handle")
        )
        projectTerminalC(
          handle, active, TypedValue("ReviewResult", "approved"),
          MinimalPresentation("Review complete", "Approved")
        ) shouldBe a[Consequence.Failure[_]]
        val issued = WorkflowInteractionJsonV1.decodeC(orderjson, inputcodec, resultcodec).toOption.getOrElse(
          fail("public Service Start WorkOrder must decode")
        )
        issued.handle shouldBe handle
        issued.current.kind shouldBe "WORK_ORDER"
        val submitted = ContinuationResult(
          handle, selected.runId, selected.continuationId, selected.expectedRevision, selected.context.snapshot,
          TypedValue("ReviewResult", "approved"), ContextReference("review-result", "1"),
          Vector.empty, ExecutionEvidence(Vector.empty)
        )
        val resultjson = WorkflowResultJsonV1.encodeC(submitted, resultcodec).toOption.getOrElse(
          fail("public Service result must encode")
        )
        val decoded = WorkflowResultJsonV1.decodeC(resultjson, resultcodec).toOption.getOrElse(
          fail("public Service result must decode")
        )
        decoded shouldBe submitted
        val completionraw = Request.of(
          component = component.componentId.name, service = "WorkflowService", operation = "submitReview",
          arguments = List(Argument("arg1", resultjson))
        )
        When("completion input is missing, type-incompatible, or the saved instance is unsuspended")
        subsystem.executeOperationResponse(completionraw.copy(arguments = Nil)) shouldBe a[Consequence.Failure[_]]
        subsystem.executeOperationResponse(completionraw.copy(arguments = List(Argument(
          "arg1", resultjson.replace("\"ReviewResult\"", "\"WrongResult\"")
        )))) shouldBe a[Consequence.Failure[_]]
        records.update(instanceidentity, active.copy(
          suspension = None,
          history = active.history.map(_.copy(suspension = None))
        ))
        Then("the unsuspended instance rejects completion before the closing Action can resume")
        subsystem.executeOperationResponse(completionraw) shouldBe a[Consequence.Failure[_]]
        When("the stored suspension is restored and the matching Result is submitted")
        records.update(instanceidentity, active)
        val completionresponse = subsystem.executeOperationResponse(completionraw).toOption.getOrElse(
          fail("public completion must return after terminal persistence")
        )
        val terminalwire = completionresponse match {
          case OperationResponse.Scalar(value: String) => value
          case _ => fail("public completion must return typed Terminal JSON")
        }
        val terminal = WorkflowInteractionJsonV1.decodeC(terminalwire, inputcodec, resultcodec).toOption.getOrElse(
          fail("public completion Terminal must decode")
        )
        terminal.handle shouldBe handle
        terminal.current shouldBe WorkflowContinuation.Terminal(
          TypedValue("ReviewResult", "approved"), MinimalPresentation("Review complete", "Approved")
        )
        Then("the closing Action and terminal history append occur exactly once")
        closingexecutions shouldBe 1
        actiontrace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange", "CommitChanges")
        val completed = records(instanceidentity)
        completed.revision shouldBe WorkflowInstancePersistence.InstanceRevision(2L)
        completed.lifecycle shouldBe WorkflowInstancePersistence.Lifecycle.Completed
        completed.suspension shouldBe None
        completed.history.size shouldBe 2
        if (placed) placedoutcomes += ((workflow.workflow, actiontrace.toVector, provider.identity))
        subsystem.executeOperationResponse(completionraw) shouldBe a[Consequence.Failure[_]]
        closingexecutions shouldBe 1
        records(instanceidentity) shouldBe completed

        When("post-commit instance persistence, Continuation persistence, or response projection fails")
        selected = selected.copy(continuationId = ContinuationIdentity("failed-instance-public-service-continuation"))
        rejectinstancewrite = true
        subsystem.executeOperationResponse(raw) shouldBe a[Consequence.Failure[_]]
        runtime.claimC(selected.continuationId) shouldBe a[Consequence.Failure[_]]
        postcommitresponses shouldBe 1
        rejectinstancewrite = false
        selected = selected.copy(continuationId = ContinuationIdentity("failed-public-service-continuation"))
        rejectwrite = true
        subsystem.executeOperationResponse(raw) shouldBe a[Consequence.Failure[_]]
        runtime.claimC(selected.continuationId) shouldBe a[Consequence.Failure[_]]
        postcommitresponses shouldBe 1
        rejectwrite = false
        selected = selected.copy(continuationId = ContinuationIdentity("indeterminate-public-service-continuation"))
        indeterminatewrite = true
        subsystem.executeOperationResponse(raw) shouldBe a[Consequence.Failure[_]]
        postcommitresponses shouldBe 1
        issuedstore.loadC(selected.continuationId) shouldBe Consequence.success(None)
        backing.loadC(selected.continuationId).toOption.flatten.map(_.status) shouldBe
          Some(ContinuationRuntimePersistence.Status.Available)
        indeterminatewrite = false
        selected = selected.copy(continuationId = ContinuationIdentity("failed-projection-public-service-continuation"))
        rejectprojection = true
        subsystem.executeOperationResponse(raw) shouldBe a[Consequence.Failure[_]]
        Then("failed responses issue no WorkOrder, while indeterminate storage and failed projection retain committed data")
        postcommitresponses shouldBe 2
        runtime.claimC(selected.continuationId).isSuccess shouldBe true
        instances.load(configuration, WorkflowInstancePersistence.InstanceIdentity(
          s"public-service-${selected.continuationId.value}"
        )).toOption.flatten.isDefined shouldBe true
      } finally {
        subsystem.shutdown()
      }
      }
      placedoutcomes.size shouldBe 2
      placedoutcomes(0)._1 shouldBe placedoutcomes(1)._1
      placedoutcomes(0)._2 shouldBe placedoutcomes(1)._2
      placedoutcomes(0)._3 should not be placedoutcomes(1)._3
    }

    "commit an explicitly declared Provided suspension before external claim and typed resume" in {
      val workflow = _definition()
      val providedbase = _provided_definition(workflow)
      val provided = providedbase.copy(providedOperations =
        providedbase.providedOperations.map(_.copy(resultType = Some("WorkflowStartResult")))
      )
      val operation = StateMachineOperationIdentity("WorkflowService", "beginReview")
      val input = Some(StateMachineOperationInput(
        StateMachineInputTypeReference("ReviewContext"), ContextReference("input-1", "1")
      ))
      val contextbundle = ContextBundle("review", Vector.empty, Vector.empty, ContextSnapshot("workflow-producer-v1"))
      val request = StateMachineProvidedApiRequest(
        "WorkflowProducer", "workflow-producer-v1", StateMachineRunIdentity("run-1"), operation,
        input, contextbundle
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
      var selectedcontinuation = continuation
      val providerrequest = ProviderExecutionRequest(request.runId, required, request.input, request.context)
      val program = new StateMachineProvidedApiProgram {
        val workflowIdentity = "WorkflowProducer"
        val operation = StateMachineOperationIdentity("WorkflowService", "beginReview")
        def program(actual: StateMachineProvidedApiRequest): ExecUowM[ActionExecution] = {
          actual shouldBe request
          ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(providerrequest)))
        }
      }
      val provider = new StateMachineProgramProvider {
        val identity = ProviderIdentity("external-review-provider")
        def program(actual: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          actual shouldBe providerrequest
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Suspended(selectedcontinuation)
          )
        }
      }
      val backing = new ContinuationRuntimePersistence.InMemory
      var failcreate = false
      val persistence = new ContinuationRuntimePersistence {
        def createC(record: ContinuationRuntimePersistence.Record): Consequence[Unit] =
          if (failcreate) Consequence.Failure(Conclusion.from(new IllegalStateException("planned post-commit write failure")))
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

      val directuow = new UnitOfWork(execution)
      val direct = new UnitOfWorkInterpreter(directuow).run(ConsequenceT.liftF(
        Free.liftF(UnitOfWorkOp.StateMachineProvidedApiExecute(request))
      ))
      direct shouldBe a[Consequence.Failure[_]]
      runtime.claimC(continuation.continuationId) shouldBe a[Consequence.Failure[_]]

      selectedcontinuation = continuation.copy(continuationId = ContinuationIdentity("aborted-service-continuation"))
      val abortedserviceuow = new UnitOfWork(execution)
      new UnitOfWorkInterpreter(abortedserviceuow).stageProvidedForExternalCommitC(request) shouldBe
        Consequence.success(ActionExecution.Suspended(selectedcontinuation))
      runtime.claimC(selectedcontinuation.continuationId) shouldBe a[Consequence.Failure[_]]
      abortedserviceuow.rollback().isSuccess shouldBe true
      runtime.claimC(selectedcontinuation.continuationId) shouldBe a[Consequence.Failure[_]]

      selectedcontinuation = continuation.copy(continuationId = ContinuationIdentity("committed-service-continuation"))
      val serviceowneduow = new UnitOfWork(execution)
      new UnitOfWorkInterpreter(serviceowneduow).stageProvidedForExternalCommitC(request) shouldBe
        Consequence.success(ActionExecution.Suspended(selectedcontinuation))
      runtime.claimC(selectedcontinuation.continuationId) shouldBe a[Consequence.Failure[_]]
      serviceowneduow.commit().isSuccess shouldBe true
      runtime.claimC(selectedcontinuation.continuationId).isSuccess shouldBe true

      selectedcontinuation = continuation
      val startuow = new UnitOfWork(execution)
      val suspended = new UnitOfWorkInterpreter(startuow).runProvidedCommittingC(request)
      suspended shouldBe Consequence.success(ActionExecution.Suspended(continuation))
      startuow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      val recoveredruntime = new PersistentContinuationRuntime(persistence)
      val claim = recoveredruntime.claimC(continuation.continuationId).toOption.getOrElse(
        fail("committed external continuation must survive runtime recreation")
      )
      runtime.claimC(continuation.continuationId) shouldBe a[Consequence.Failure[_]]
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("ReviewResult"), ContextReference("review-result", "1")
      )
      val resumedruntime = new PersistentContinuationRuntime(persistence)
      val recoveredclaim = resumedruntime.recoverClaimC(continuation.continuationId).toOption.getOrElse(
        fail("restart must recover the private claim from persistence")
      )
      recoveredclaim shouldBe claim
      resumedruntime.resumeC(recoveredclaim, result, () => new UnitOfWork(execution)).isSuccess shouldBe true
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

      selectedcontinuation = continuation.copy(continuationId = ContinuationIdentity("failed-continuation"))
      failcreate = true
      val faileduow = new UnitOfWork(execution)
      val failed = new UnitOfWorkInterpreter(faileduow).runProvidedCommittingC(request)
      failed shouldBe a[Consequence.Failure[_]]
      failed.display should include ("planned post-commit write failure")
      faileduow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      backing.loadC(selectedcontinuation.continuationId).toOption.flatten shouldBe None
      runtime.claimC(selectedcontinuation.continuationId) shouldBe a[Consequence.Failure[_]]
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
      val providerrequest = ProviderExecutionRequest(request.runId, required, request.input, request.context)
      var providedcalls = 0
      var providercalls = 0
      val providedprogram = new StateMachineProvidedApiProgram {
        val workflowIdentity = "WorkflowProducer"
        val operation = StateMachineOperationIdentity("WorkflowService", "beginReview")
        def program(actual: StateMachineProvidedApiRequest): ExecUowM[ActionExecution] = {
          actual shouldBe request
          providedcalls += 1
          ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(providerrequest)))
        }
      }
      val provider = new StateMachineProgramProvider {
        val identity = ProviderIdentity("review-provider")
        def program(actual: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          actual shouldBe providerrequest
          providercalls += 1
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](result)
        }
      }
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("generated_provided_api_dispatch"),
        new Component() with GeneratedWorkflowMetadataProvider with GeneratedProvidedApiMetadataProvider
          with StateMachineProvidedApiProgramSource with StateMachineProviderSource {
          override def generatedWorkflowDefinitions: Vector[Definition] = Vector(workflow)
          override def generatedProvidedApiDefinitions: Vector[GeneratedProvidedApiAbi.Definition] = Vector(provided)
          override def stateMachineProvidedApiPrograms: Vector[StateMachineProvidedApiProgram] = Vector(providedprogram)
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
      val wronginput = component.stateMachineProvidedApiDispatcher.resolveC(
        request.copy(input = Some(StateMachineOperationInput(
          StateMachineInputTypeReference("WrongContext"), ContextReference("input-1", "1")
        )))
      )
      val wrongresult = component.stateMachineProvidedApiDispatcher.admitResultC(
        request,
        ActionExecution.Completed(StateMachineOperationResult(
          StateMachineResultTypeReference("WrongResult"), ContextReference("result-1", "1")
        ))
      )

      Then("only the admitted operation reaches its program and bound Provider")
      dispatched shouldBe Consequence.success(result)
      unknown shouldBe a[Consequence.Failure[_]]
      wronginput shouldBe a[Consequence.Failure[_]]
      wrongresult shouldBe a[Consequence.Failure[_]]
      providedcalls shouldBe 1
      providercalls shouldBe 1
    }

    "invoke declared control-plane Operations without a child AI Provider" in {
      Given("advance, status, and submit are explicit Provided Operations with local programs")
      val workflow = _definition()
      val names = Vector("advance", "status", "submit")
      val provided = _provided_definition(workflow).copy(
        providedOperations = names.map { name =>
          GeneratedProvidedApiAbi.Operation(
            "WorkflowService", name, None, Some("ControlResult"),
            GeneratedProvidedApiAbi.SourceIdentity(Some(72))
          )
        }
      )
      val called = scala.collection.mutable.ArrayBuffer.empty[String]
      val programs = names.map { name =>
        new StateMachineProvidedApiProgram {
          val workflowIdentity = "WorkflowProducer"
          val operation = StateMachineOperationIdentity("WorkflowService", name)
          def program(request: StateMachineProvidedApiRequest): ExecUowM[ActionExecution] = {
            called += name
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(
                StateMachineResultTypeReference("ControlResult"), ContextReference(s"$name-result", "1")
              ))
            )
          }
        }
      }
      val component = _initialized_component(
        TestComponentFactory.emptySubsystem("workflow_control_operations"),
        new Component() with GeneratedWorkflowMetadataProvider with GeneratedProvidedApiMetadataProvider
          with StateMachineProvidedApiProgramSource {
          override def generatedWorkflowDefinitions: Vector[Definition] = Vector(workflow)
          override def generatedProvidedApiDefinitions: Vector[GeneratedProvidedApiAbi.Definition] = Vector(provided)
          override def stateMachineProvidedApiPrograms: Vector[StateMachineProvidedApiProgram] = programs
        }
      )

      When("each declared Operation is invoked through ComponentFactory and the active UnitOfWork")
      new ComponentFactory().bootstrapC(component).toOption shouldBe Some(component)
      val root = ExecutionContext.create()
      val context = root.withScope(Component.Context("workflow-control", root.scope, component, ComponentOrigin.Embed))
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(context))
      val outcomes = names.map { name =>
        val request = StateMachineProvidedApiRequest(
          "WorkflowProducer", "workflow-producer-v1", StateMachineRunIdentity("control-run-1"),
          StateMachineOperationIdentity("WorkflowService", name), None,
          ContextBundle("control", Vector.empty, Vector.empty, ContextSnapshot("workflow-producer-v1"))
        )
        interpreter.run(ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StateMachineProvidedApiExecute(request))))
      }

      Then("all three complete locally without registering or invoking a child AI Provider")
      outcomes.map(_.toOption) shouldBe names.map { name =>
        Some(ActionExecution.Completed(StateMachineOperationResult(
          StateMachineResultTypeReference("ControlResult"), ContextReference(s"$name-result", "1")
        )))
      }
      called.toVector shouldBe names
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
      27
    )
    val definition = root.copy(line = 29)
    val composite = root.copy(line = 33)
    val states = Vector(
      StateDescriptor(StateIdentity("Pending"), root.copy(line = 45)),
      StateDescriptor(StateIdentity("Approved"), root.copy(line = 47))
    )
    val actionnames = Vector(
      ("BuildProject", "buildProject", 67),
      ("RunTests", "runTests", 73),
      ("ReviewChange", "reviewChange", 79),
      ("CommitChanges", "commitChanges", 85)
    )
    val actions = actionnames.map { case (name, operation, line) =>
      val source = root.copy(line = line)
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
        root.copy(line = 93),
        reviewaction.sourceLocation
      )),
      schemaShapes = requiredSchemaShapes.toVector.sortBy(_._1).map {
        case (identity, members) => SchemaShapeDescriptor(identity, members, _abi_source)
      }
    )
  }

  private def _placed_definition(): Definition = {
    val original = _definition()
    val root = SourceLocation(
      "src/test/resources/modeler/skill-driven-workflow-executable.cml", 27
    )
    val actions = original.actions.zip(Vector(67, 76, 85, 95)).map { case (action, line) =>
      val location = root.copy(line = line)
      action.copy(
        operation = action.operation.copy(sourceLocation = location),
        sourceLocation = location
      )
    }
    original.copy(
      fixtureSha256 = acceptedPlacedFixtureSha256,
      workflow = original.workflow.copy(
        identity = placedWorkflowIdentity,
        revision = acceptedPlacedWorkflowRevision,
        source = WorkflowSourceCorrelation(root, root.copy(line = 29))
      ),
      compositeStateMachine = original.compositeStateMachine.copy(
        sourceLocation = root.copy(line = 33),
        states = original.compositeStateMachine.states.zip(Vector(45, 47)).map {
          case (state, line) => state.copy(sourceLocation = root.copy(line = line))
        }
      ),
      actions = actions,
      requiredSpis = Vector(original.requiredSpis.head.copy(
        operation = actions(2).operation,
        capabilitySource = root.copy(line = 145),
        actionSource = actions(2).sourceLocation
      ))
    )
  }

  private def _workflow_resource(name: String): String = {
    val stream = Option(getClass.getResourceAsStream(s"/workflow/$name"))
      .getOrElse(fail(s"missing pinned Workflow resource: $name"))
    try new String(stream.readAllBytes(), StandardCharsets.UTF_8).stripSuffix("\n")
    finally stream.close()
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
    79
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
