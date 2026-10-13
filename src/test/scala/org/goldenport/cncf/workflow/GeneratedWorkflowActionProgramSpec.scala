package org.goldenport.cncf.workflow

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import cats.~>
import cats.syntax.functor.*
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.statemachine.{ExecutionPlan, ExecutionPlanExecutor, ResolvedAction}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkTermination}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Generated Workflow and Provided Operation executable metadata cases.
  * @since   Sep. 25, 2026
  * @version Oct. 12, 2026
  */
final class GeneratedWorkflowActionProgramSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedWorkflowActionProgram.*

  private def _resource(name: String): String = {
    val stream = Option(getClass.getResourceAsStream(s"/workflow/$name"))
      .getOrElse(fail(s"missing pinned Cozy resource: $name"))
    try new String(stream.readAllBytes(), StandardCharsets.UTF_8).stripSuffix("\n")
    finally stream.close()
  }

  private val _workflow = _resource("skill-driven-workflow-placed-abi.json")
  private val _program = _resource("skill-driven-workflow-placed-action-program.json")

  private def _action: ResolvedAction[Unit, Unit] =
    new ResolvedAction[Unit, Unit] {
      def program(state: Unit, event: Unit): ExecUowM[ActionExecution] =
        throw new UnsupportedOperationException("binding-only fixture")
    }

  "Generated Workflow ActionProgram receiver" should {
    "join the pinned Cozy Workflow and ActionProgram sidecars by identity and provenance" in {
      Given("the pinned Cozy Workflow and ActionProgram sidecars")
      val workflow = _workflow
      val program = _program
      When("the CNCF receiver admits both sidecars")
      val admitted = parseC(workflow, program).toOption.getOrElse(
        fail("placed Cozy sidecars must be admitted")
      )
      Then("their identities, hashes, and placed Action order agree")
      admitted.workflowIdentity shouldBe "WorkflowProducerPlaced"
      admitted.workflowVersion shouldBe "workflow-producer-placed-v1"
      admitted.workflowAbiSha256 shouldBe
        "4011da8dff663dc369101d1d54d892e71fb26b97970867f8adbed63e2c15d992"
      admitted.actionProgramSha256 shouldBe
        "baac46d5d355f03aca909ab6f10098c7da6708b4667dc67731f6f790191afa5d"
      admitted.requiredSpiActionIdentities shouldBe Vector("ReviewChange")
      admitted.orderedOccurrences.map(_.actionIdentity) shouldBe Vector(
        "BuildProject", "RunTests", "ReviewChange", "CommitChanges"
      )
      admitted.orderedOccurrences.map(_.placement) shouldBe Vector(
        Placement.Exit, Placement.Exit, Placement.Transition, Placement.Entry
      )
      admitted.orderedOccurrences.map(_.transition).distinct.size shouldBe 1
    }

    "bind explicitly generated occurrence order, independent of binding order" in {
      Given("admitted occurrences and deliberately reversed Action bindings")
      val admitted = parseC(_workflow, _program).toOption.getOrElse(fail("sidecars must be admitted"))
      val build = _action
      val tests = _action
      val review = _action
      val commit = _action
      val bindings = Vector(
        Binding("CommitChanges", commit), Binding("ReviewChange", review),
        Binding("RunTests", tests), Binding("BuildProject", build)
      )
      val reversedwire = admitted.copy(occurrences = admitted.occurrences.reverse)
      When("the receiver binds by generated occurrence ordinal")
      val plan = bindC(reversedwire, bindings).toOption.getOrElse(fail("occurrences must bind"))
      Then("the execution plan preserves generated placement order")
      plan.exitActions shouldBe Vector(build, tests)
      plan.transitionActions shouldBe Vector(review)
      plan.entryActions shouldBe Vector(commit)
    }

    "run the generated prefix to ReviewChange suspension and its entry Action after recovery" in {
      Given("a generated Action plan with a durable ReviewChange Continuation")
      val admitted = parseC(_workflow, _program).toOption.getOrElse(fail("sidecars must be admitted"))
      val trace = scala.collection.mutable.ArrayBuffer.empty[String]
      val required = StateMachineRequiredOperation(
        StateMachineRequiredOperationIdentity("review-change-capability"),
        "ReviewChange",
        StateMachineOperationIdentity("WorkflowService", "reviewChange"),
        Some(StateMachineInputTypeReference("ReviewContext")),
        Some(StateMachineResultTypeReference("ReviewResult")),
        StateMachineRequiredOperationMetadata(
          ContextContract("review-context", Vector.empty, Vector.empty),
          CompletionContract("review-completion", Vector.empty),
          EvidenceContract("review-evidence", Vector.empty), Vector.empty
        )
      )
      val continuation = Continuation(
        StateMachineRunIdentity("placed-run"), ContinuationIdentity("placed-review"),
        StateMachineRevision("1"), required,
        ContextBundle("review", Vector.empty, Vector.empty,
          ContextSnapshot(admitted.workflowVersion))
      )
      def _completed_(identity: String): ResolvedAction[Unit, Unit] =
        new ResolvedAction[Unit, Unit] {
          def program(state: Unit, event: Unit): ExecUowM[ActionExecution] =
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(
                StateMachineResultTypeReference("ReviewResult"), ContextReference(identity, "1")
              ))
            ).map { result =>
              trace += identity
              result
            }
        }
      val review = new ResolvedAction[Unit, Unit] {
        def program(state: Unit, event: Unit): ExecUowM[ActionExecution] =
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Suspended(continuation)
          ).map { result =>
            trace += "ReviewChange"
            result
          }
      }
      val plan = bindC(admitted, Vector(
        Binding("CommitChanges", _completed_("CommitChanges")),
        Binding("ReviewChange", review),
        Binding("RunTests", _completed_("RunTests")),
        Binding("BuildProject", _completed_("BuildProject"))
      )).toOption.getOrElse(fail("generated occurrences must bind"))
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      When("the generated prefix executes and commits")
      val startuow = new UnitOfWork(ExecutionContext.create())
      val started = ExecutionPlanExecutor.executeCommittingC(plan, (), (), startuow, runtime)
      Then("the internal Actions complete before the external suspension is claimable")
      started shouldBe org.goldenport.Consequence.success(Some(continuation))
      startuow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      trace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange")
      When("the committed suspension is claimed")
      val claimed = runtime.claimC(continuation.continuationId)
      Then("the actual Continuation is claimable")
      claimed.toOption should not be empty
      val closing = ExecutionPlan[Unit, Unit](Vector.empty, Vector.empty, plan.entryActions)
      When("the generated closing entry Action runs in a fresh UnitOfWork")
      val completionuow = new UnitOfWork(ExecutionContext.create())
      val completed = ExecutionPlanExecutor.executeCommittingC(closing, (), (), completionuow, runtime)
      Then("CommitChanges completes after the suspended prefix")
      completed shouldBe org.goldenport.Consequence.success(None)
      completionuow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      trace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange", "CommitChanges")
    }

    "bind the generated Action sequence as a provenance-checked Provided API program" in {
      Given("an admitted generated sequence and a declared Provided Operation")
      val admitted = parseC(_workflow, _program).toOption.getOrElse(fail("sidecars must be admitted"))
      val operation = StateMachineOperationIdentity("WorkflowService", "beginReview")
      val request = StateMachineProvidedApiRequest(
        admitted.workflowIdentity, admitted.workflowVersion, StateMachineRunIdentity("placed-service-run"),
        operation, None, ContextBundle("review", Vector.empty, Vector.empty,
          ContextSnapshot(admitted.workflowVersion))
      )
      val required = StateMachineRequiredOperation(
        StateMachineRequiredOperationIdentity("review-change-capability"), "ReviewChange",
        StateMachineOperationIdentity("WorkflowService", "reviewChange"),
        Some(StateMachineInputTypeReference("ReviewContext")),
        Some(StateMachineResultTypeReference("ReviewResult")),
        StateMachineRequiredOperationMetadata(
          ContextContract("review-context", Vector.empty, Vector.empty),
          CompletionContract("review-completion", Vector.empty),
          EvidenceContract("review-evidence", Vector.empty), Vector.empty
        )
      )
      val continuation = Continuation(request.runId, ContinuationIdentity("placed-service-review"),
        StateMachineRevision("1"), required, request.context)
      val trace = scala.collection.mutable.ArrayBuffer.empty[String]
      def _bound_(identity: String): Binding[StateMachineProvidedApiRequest, Unit] =
        Binding(identity, new ResolvedAction[StateMachineProvidedApiRequest, Unit] {
          def program(state: StateMachineProvidedApiRequest, event: Unit): ExecUowM[ActionExecution] = {
            state shouldBe request
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              if (identity == "ReviewChange") ActionExecution.Suspended(continuation)
              else ActionExecution.Completed(StateMachineOperationResult(
                StateMachineResultTypeReference("ReviewResult"), ContextReference(identity, "1")
              ))
            ).map { outcome =>
              trace += identity
              outcome
            }
          }
        })
      When("the generated sequence binds as a Provided API program")
      val provided = bindProvidedApiProgramC(admitted, operation, Vector(
        _bound_("CommitChanges"), _bound_("ReviewChange"), _bound_("RunTests"), _bound_("BuildProject")
      )).toOption.getOrElse(fail("generated Provided API program must bind"))
      Then("the public binding retains its Workflow and Operation provenance")
      provided.workflowIdentity shouldBe admitted.workflowIdentity
      provided.operation shouldBe operation
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(ExecutionContext.create()))
      val natural = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](op: UnitOfWorkOp[A]): Consequence[A] = interpreter.interpret(op)
      }
      When("the bound program executes the matching request")
      provided.program(request).value.foldMap(natural).flatMap(identity) shouldBe
        Consequence.success(ActionExecution.Suspended(continuation))
      Then("the generated prefix stops at the external ReviewChange boundary")
      trace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange")
      When("a request carries a different Workflow revision")
      provided.program(request.copy(workflowRevision = "other")).value.foldMap(natural).flatMap(identity)
        .toOption shouldBe None
      Then("it is rejected without executing another Action")
      trace.toVector shouldBe Vector("BuildProject", "RunTests", "ReviewChange")
    }

    "admit the placed Workflow as an independently persisted instance definition" in {
      Given("the placed Workflow and its generated Provided API sidecar")
      val placed = parseC(_workflow, _program).toOption.getOrElse(fail("sidecars must be admitted"))
      val abi = GeneratedWorkflowAbi
      val source = abi.SourceLocation(
        "src/test/resources/modeler/skill-driven-workflow-executable.cml", 27
      )
      val actions = placed.actions.map { action =>
        val location = source.copy(line = action.sourceLine)
        abi.ActionDescriptor(
          abi.ActionIdentity(action.identity), abi.ActionKind.Operation,
          abi.OperationDescriptor(
            abi.ServiceIdentity(action.operation.service),
            abi.OperationIdentity(action.operation.name),
            action.operation.inputType.map(abi.TypeIdentity.apply),
            Some(abi.TypeIdentity("ReviewResult")), location
          ),
          action.inputBinding, location
        )
      }
      val review = actions.find(_.identity.value == "ReviewChange")
        .getOrElse(fail("placed review Action must exist"))
      val definition = abi.Definition(
        abi.acceptedProducerAbiIdentity, abi.acceptedWorkflowAbiIdentity,
        abi.acceptedBootstrapSchemaIdentity, abi.acceptedProducerRevision,
        abi.acceptedPlacedFixtureSha256,
        abi.WorkflowDescriptor(
          abi.WorkflowIdentity(placed.workflowIdentity), abi.WorkflowRevision(placed.workflowVersion),
          abi.WorkflowSourceCorrelation(source, source.copy(line = 29))
        ),
        abi.CompositeStateMachineDescriptor(
          abi.CompositeStateMachineIdentity("review"), abi.StateMachineIdentity("ReviewLifecycle"),
          Vector(
            abi.StateDescriptor(abi.StateIdentity("Pending"), source.copy(line = 45)),
            abi.StateDescriptor(abi.StateIdentity("Approved"), source.copy(line = 47))
          ), source.copy(line = 33)
        ),
        actions,
        Vector(abi.RequiredSpiDescriptor(
          abi.RequiredSpiIdentity("review-change-capability"), review.identity, review.operation,
          source.copy(line = 145), review.sourceLocation
        )),
        abi.requiredSchemaShapes.toVector.sortBy(_._1).map { case (identity, members) =>
          abi.SchemaShapeDescriptor(identity, members,
            abi.SourceLocation("target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala", 1))
        }
      )
      When("CNCF admits the placed definition and binds WorkflowInstance persistence")
      abi.admitC(Vector(definition)).toOption shouldBe Some(Vector(definition))
      val binding = WorkflowInstancePersistence.bindDefinitionC(definition).toOption
        .getOrElse(fail("placed Workflow must bind to persistence"))
      Then("the persisted executable identity and revision resolve the admitted definition")
      binding.validateC.toOption shouldBe Some(binding)
      binding.workflowIdentity.value shouldBe placed.workflowIdentity
      binding.workflowRevision.value shouldBe placed.workflowVersion
      When("the matching generated Provided API sidecar is decoded")
      val providedwire = _resource("skill-driven-workflow-placed-provided-api.json")
      MessageDigest.getInstance("SHA-256").digest(providedwire.getBytes(StandardCharsets.UTF_8))
        .map(byte => f"${byte & 0xff}%02x").mkString shouldBe
          "ad70eee756a09c4b97fc9af6313f77efaa5137a3f9b52c13404b7e6ea707a8db"
      val providedroot = parse(providedwire).toOption.getOrElse(
        fail("placed Provided API sidecar must parse")
      ).hcursor
      val providedworkflow = providedroot.downField("workflows").downN(0)
      val providedoperations = providedworkflow.downField("providedOperations")
        .as[Vector[Json]].toOption.getOrElse(fail("placed Provided Operations must decode"))
      val provided = GeneratedProvidedApiAbi.Definition(
        providedroot.get[String]("schemaVersion").toOption.getOrElse(fail("Provided API schema")),
        providedroot.get[String]("generator").toOption.getOrElse(fail("Provided API generator")),
        providedworkflow.get[String]("identity").toOption.getOrElse(fail("Provided API identity")),
        providedworkflow.get[String]("version").toOption.getOrElse(fail("Provided API version")),
        GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
          GeneratedProvidedApiAbi.SourceIdentity(
            providedworkflow.downField("source").downField("root").get[Int]("line").toOption
          ),
          GeneratedProvidedApiAbi.SourceIdentity(
            providedworkflow.downField("source").downField("definition").get[Int]("line").toOption
          )
        ),
        providedoperations.map { operation =>
          val cursor = operation.hcursor
          GeneratedProvidedApiAbi.Operation(
            cursor.get[String]("service").toOption.getOrElse(fail("Provided service")),
            cursor.get[String]("operation").toOption.getOrElse(fail("Provided operation")),
            cursor.get[Option[String]]("inputType").toOption.flatten,
            cursor.get[Option[String]]("resultType").toOption.flatten,
            GeneratedProvidedApiAbi.SourceIdentity(cursor.downField("source").get[Int]("line").toOption)
          )
        }
      )
      Then("the generated public Operations agree with the Workflow and reject drift")
      provided.providedOperations.map(_.name) shouldBe Vector("beginReview", "submitReview")
      GeneratedProvidedApiAbi.admitC(Vector(provided), Vector(definition)).toOption shouldBe
        Some(Vector(provided))
      Given("Spec: docs/spec/workflow-completion-applicability.md; Rule: R3; Example: E5; actual parsed Provided API operations and an admitted Workflow")
      val diagnostic = provided.copy(generator = "different-diagnostic-producer",
        source = GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
          GeneratedProvidedApiAbi.SourceIdentity(None), GeneratedProvidedApiAbi.SourceIdentity(Some(999))),
        providedOperations = provided.providedOperations.map(_.copy(source = GeneratedProvidedApiAbi.SourceIdentity(None))))
      When("Provided admission and Operation resolution encounter different or absent provenance")
      val resolved = GeneratedProvidedApiAbi.admitC(Vector(diagnostic), Vector(definition))
      val compatible = diagnostic.providesOperation(provided.providedOperations.head)
      val conflicting = diagnostic.providesOperation(provided.providedOperations.head.copy(inputType = Some("foreign.input")))
      val rejected = Vector(
        GeneratedProvidedApiAbi.admitC(Vector(diagnostic.copy(version = "foreign-revision")), Vector(definition)),
        GeneratedProvidedApiAbi.admitC(Vector(diagnostic.copy(schemaVersion = "unsupported")), Vector(definition)),
        GeneratedProvidedApiAbi.admitC(Vector(diagnostic.copy(providedOperations = diagnostic.providedOperations :+ diagnostic.providedOperations.head)),
          Vector(definition)))
      Then("diagnostics are not an admission gate, while real schema/Workflow/Operation ambiguity and type conflicts reject")
      resolved.toOption shouldBe Some(Vector(diagnostic))
      compatible shouldBe true
      conflicting shouldBe false
      rejected.forall(!_.isSuccess) shouldBe true
      When("the placed Workflow is replaced by a different supported fixture identity or revision")
      val incompatible = Vector(
        abi.admitC(Vector(definition.copy(fixtureSha256 = abi.acceptedFixtureSha256))),
        abi.admitC(Vector(definition.copy(workflow = definition.workflow.copy(
          revision = abi.acceptedWorkflowRevision)))))
      Then("the actual placed executable contract remains closed")
      incompatible.map(_.toOption) shouldBe Vector(None, None)
    }

    "consume absent, moved and nonpositive diagnostic lines independently of executable Action identity" in {
      Given("the real Workflow and ActionProgram sidecars, their decoded Definition and baseline binding order")
      val workflow = parse(_workflow).toOption.get
      val program = parse(_program).toOption.get
      val baseline = parseC(_workflow, _program).toOption.getOrElse(fail("sidecars must be admitted"))
      val bindings = baseline.actions.reverse.map(action => Binding(action.identity, _action))
      val baselineplan = bindC(baseline, bindings).toOption.getOrElse(fail("baseline must bind"))
      def _annotate_(json: Json, container: String, collection: String, field: String,
        line: Option[Json]): Json = json.mapObject { root =>
        root.add(container, root(container).get.mapArray { definitions =>
          definitions.map(_.mapObject { definition =>
            definition.add(collection, definition(collection).get.mapArray { values =>
              values.map(_.mapObject { value =>
                line.fold(value.remove(field)) { annotation =>
                  value.add(field, value(field).get.mapObject(_.add("line", annotation)))
                }
              })
            })
          })
        })
      }
      def _action_semantics_(definition: Definition) = definition.actions.map(action =>
        (action.identity, action.operation, action.inputBinding, action.metadata))
      def _occurrence_semantics_(definition: Definition) = definition.occurrences.map(occurrence =>
        (occurrence.definitionIdentity, occurrence.identity, occurrence.ordinal,
          occurrence.actionIdentity, occurrence.operation, occurrence.inputBinding,
          occurrence.placement, occurrence.transition, occurrence.metadata))
      val annotations: Vector[(String, Option[Json], Int)] = Vector(
        ("missing", None, 0), ("null", Some(Json.Null), 0),
        ("string", Some(Json.fromString("unused")), 0),
        ("zero", Some(Json.fromInt(0)), 0), ("negative", Some(Json.fromInt(-7)), -7),
        ("moved", Some(Json.fromInt(999)), 999))
      val lanes = Vector(
        ("Workflow action source", "workflows", "actions", "source"),
        ("program action actionSource", "definitions", "actions", "actionSource"),
        ("occurrence actionSource", "definitions", "occurrences", "actionSource"),
        ("occurrence occurrenceSource", "definitions", "occurrences", "occurrenceSource"))
      lanes.foreach { case (lane, container, collection, field) =>
        annotations.foreach { case (variant, line, expected) =>
          withClue(s"$lane / $variant: ") {
            Given(s"only $lane has $variant diagnostic lines and every other lane retains the pinned values")
            val workflowvariant = if (container == "workflows")
              _annotate_(workflow, container, collection, field, line) else workflow
            val programvariant = if (container == "definitions")
              _annotate_(program, container, collection, field, line) else program
            When(s"the receiver parses and binds the $lane / $variant sidecars")
            val parsed = parseC(workflowvariant.noSpaces, programvariant.noSpaces)
            val bound = parsed.flatMap(definition => bindC(definition, bindings))
            Then("the diagnostic defaults or exact Int values are decoded without changing executable semantics or binding order")
            parsed.isSuccess shouldBe true
            bound.isSuccess shouldBe true
            val decoded = parsed.toOption.getOrElse(fail("variant must decode"))
            val plan = bound.toOption.getOrElse(fail("variant must bind"))
            decoded.actions.map(_.sourceLine) shouldBe
              (if (container == "definitions" && collection == "actions")
                Vector.fill(baseline.actions.size)(expected) else baseline.actions.map(_.sourceLine))
            decoded.occurrences.map(_.actionSourceLine) shouldBe
              (if (collection == "occurrences" && field == "actionSource")
                Vector.fill(baseline.occurrences.size)(expected) else baseline.occurrences.map(_.actionSourceLine))
            decoded.occurrences.map(_.occurrenceSourceLine) shouldBe
              (if (collection == "occurrences" && field == "occurrenceSource")
                Vector.fill(baseline.occurrences.size)(expected) else baseline.occurrences.map(_.occurrenceSourceLine))
            decoded.workflowIdentity shouldBe baseline.workflowIdentity
            decoded.workflowVersion shouldBe baseline.workflowVersion
            decoded.requiredSpiActionIdentities shouldBe baseline.requiredSpiActionIdentities
            _action_semantics_(decoded) shouldBe _action_semantics_(baseline)
            _occurrence_semantics_(decoded) shouldBe _occurrence_semantics_(baseline)
            plan.exitActions shouldBe baselineplan.exitActions
            plan.transitionActions shouldBe baselineplan.transitionActions
            plan.entryActions shouldBe baselineplan.entryActions
          }
        }
      }
    }

    "reject cross-artifact identity, Action, and metadata drift" in {
      Given("pinned Workflow and ActionProgram sidecars with single-field mutations")
      val admitted = parseC(_workflow, _program).toOption.getOrElse(fail("sidecars must be admitted"))
      val otherworkflow = _workflow.replace("\"identity\":\"WorkflowProducerPlaced\"",
        "\"identity\":\"OtherWorkflow\"")
      val unknownaction = _program.replace(
        "\"occurrenceId\":\"constituent:run-tests-before-review:2\",\"ordinal\":2,\"actionId\":\"RunTests\"",
        "\"occurrenceId\":\"constituent:run-tests-before-review:2\",\"ordinal\":2,\"actionId\":\"UnknownAction\""
      )
      val otheroccurrence = _program.replace(
        "\"definitionIdentity\":\"WorkflowProducerPlaced\",\"occurrenceId\":\"constituent:build-project-before-review:1\"",
        "\"definitionIdentity\":\"OtherWorkflow\",\"occurrenceId\":\"constituent:build-project-before-review:1\""
      )
      val invalid = admitted.copy(occurrences = admitted.occurrences.updated(2,
        admitted.occurrences(2).copy(metadata = admitted.occurrences(2).metadata.copy(
          effectClass = "LOCAL"
        ))))
      val declareddrift = admitted.copy(actions = admitted.actions.updated(2,
        admitted.actions(2).copy(metadata = admitted.actions(2).metadata.map(_.copy(
          idempotencyKeyRef = Some("different-key")
        )))))

      When("the receiver parses or binds each incompatible variant")
      val parsed = Vector(
        parseC(otherworkflow, _program), parseC(_workflow, unknownaction),
        parseC(_workflow, otheroccurrence)
      )
      val bound = Vector(
        bindC(invalid, admitted.actions.map(x => Binding(x.identity, _action))),
        bindC(declareddrift, admitted.actions.map(x => Binding(x.identity, _action))),
        bindC(admitted, Vector(Binding("BuildProject", _action)))
      )
      Then("none is admitted as a valid generated execution plan")
      parsed.map(_.toOption) shouldBe Vector.fill(3)(None)
      bound.map(_.toOption) shouldBe Vector.fill(3)(None)
    }
  }
}
