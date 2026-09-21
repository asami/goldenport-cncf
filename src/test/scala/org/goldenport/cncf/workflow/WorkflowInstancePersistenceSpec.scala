package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 21, 2026
 * @version Sep. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowInstancePersistenceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  import GeneratedWorkflowAbi.{
    Diagnostic as _,
    DiagnosticCode as _,
    ProducerAbiIdentity as _,
    SourceLocation as GeneratedSourceLocation,
    WorkflowAbiIdentity as _,
    *
  }
  import WorkflowInstancePersistence.*

  private val _e1 = afterWord(
    "in spec:workflow-instance-persistence, example:E1, rules:CWF-77-03, phase:77, slice:77-S1B"
  )
  private val _e2 = afterWord(
    "in spec:workflow-instance-persistence, example:E2, rules:CWF-77-03, phase:77, slice:77-S1B"
  )
  private val _e3 = afterWord(
    "in spec:workflow-instance-persistence, example:E3, rules:CWF-77-03, phase:77, slice:77-S1B"
  )
  private val _e4 = afterWord(
    "in spec:workflow-instance-persistence, example:E4, rules:CWF-77-03, phase:77, slice:77-S1B"
  )
  private val _e5 = afterWord(
    "in spec:workflow-instance-persistence, example:E5, rules:CWF-77-03, phase:77, slice:77-S1B"
  )

  private val _e6 = afterWord("in spec:workflow-instance-persistence, example:E6, rules:CWF-77-03, phase:77, slice:77-S1B")

  "Workflow instance persistence SPI" should {
    "E1 bind only admitted generated ABI provenance into the independent SPI family" must _e1 {
      "retain the definition binding without adopting legacy workflow runtime behavior" in {
        Given("a complete generated Workflow ABI definition and an explicit same-store configuration")
        val binding = _take(WorkflowInstancePersistence.bindDefinitionC(_definition()))
        val record = _initial_record(binding)
        val provider: WorkflowInstancePersistence = new ProbePersistence

        When("the independent record is presented through the SPI create operation")
        val persisted = _take(provider.create(_same_store_configuration, record))

        Then("the accepted ABI identities and source correlation remain in the persistence binding")
        persisted.definition shouldBe binding
        binding.workflowIdentity shouldBe WorkflowDefinitionIdentity("WorkflowProducer")
        binding.workflowRevision shouldBe WorkflowDefinitionRevision("workflow-producer-v1")
        binding.producerAbiIdentity shouldBe ProducerAbiIdentity("workflow-producer-v1")
        binding.workflowAbiIdentity shouldBe WorkflowAbiIdentity("cozy.cml.statemachine-workflow-abi.v1")
        binding.bootstrapAbiIdentity shouldBe BootstrapAbiIdentity("cozy.cml.statemachine-workflow-bootstrap.v1")
        binding.sourceCorrelation.root.resource should include("skill-driven-workflow-producer.cml")
      }
    }

    "E2 preserve revisioned append-only history for independent instance records" must _e2 {
      "derive only the contiguous next record from its expected revision and sequence" in {
        Given("admitted bindings and a range of explicit active progression references")
        val binding = _take(WorkflowInstancePersistence.bindDefinitionC(_definition()))
        val property = Prop.forAll(Gen.alphaNumStr.suchThat(_.nonEmpty)) { suffix =>
          val initial = _initial_record(binding, suffix)
          val appended = initial.appendC(initialRevision, _active_entry(suffix))
          appended.toOption.exists { record =>
            record.revision == InstanceRevision(1L) &&
              record.history.map(_.sequence) == Vector(HistorySequence(1L)) &&
              record.lifecycle == Lifecycle.Active &&
              record.currentProgression == Some(ProgressionReference(s"progress-$suffix"))
          }
        }

        When("the pure append admission evaluates the generated records")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)
        val appended = _take(_initial_record(binding).appendC(initialRevision, _active_entry("one")))

        Then("the expected revision is consumed once and the immutable history remains the source of current facts")
        checked.passed shouldBe true
        appended.history should have size 1
        appended.history.head.revision shouldBe appended.revision
        appended.history.head.currentProgression shouldBe appended.currentProgression
      }
    }

    "E3 reject stale and incomplete persistence facts with stable diagnostics" must _e3 {
      "fail closed for incorrect expected revision, illegal lifecycle/progression, and incomplete suspension" in {
        Given("a valid initial record plus stale, illegal, and incomplete persistence inputs")
        val binding = _take(WorkflowInstancePersistence.bindDefinitionC(_definition()))
        val initial = _initial_record(binding)
        val stale = initial.appendC(InstanceRevision(1L), _active_entry("stale"))
        val illegal = initial.copy(
          lifecycle = Lifecycle.Active,
          currentProgression = None
        ).validateC
        val incomplete = initial.copy(
          lifecycle = Lifecycle.Active,
          currentProgression = Some(ProgressionReference("review")),
          suspension = Some(SuspensionBoundary(
            ContinuationIdentity("continuation-1"),
            initialRevision,
            ContextSnapshotReference("context-1"),
            CompletionReference("completion-1"),
            EvidenceReference(""),
            CorrelationReference("resume-1")
          ))
        ).validateC

        When("the pure validation helpers admit each boundary")
        val diagnostics = Vector(stale, illegal, incomplete).map(_diagnostic)

        Then("each rejected fact has a deterministic closed diagnostic code")
        diagnostics.map(_.code) shouldBe Vector(
          DiagnosticCode.IncorrectExpectedRevision,
          DiagnosticCode.IllegalLifecycleProgression,
          DiagnosticCode.IncompleteSuspensionBoundary
        )
      }
    }

    "E4 require an explicit valid delivery configuration for every consumer" must _e4 {
      "reject mismatched same-store identity and incomplete cross-store committed-transition proof" in {
        Given("a named workflow store with all non-delivery policies supplied")
        val mismatch = _same_store_configuration.copy(
          delivery = DeliveryConfiguration.SameStore(WorkflowStoreIdentity("entity-store"))
        )
        val incomplete = _same_store_configuration.copy(
          delivery = DeliveryConfiguration.CrossStoreCommittedTransition(
            WorkflowStoreIdentity("entity-store"),
            CommittedEntityTransitionReference("committed-transition-1"),
            IdempotencyKey(""),
            RecoverabilityReference("recovery-1")
          )
        )

        When("the explicit delivery alternatives are validated")
        val diagnostics = Vector(mismatch.validateC, incomplete.validateC).map(_diagnostic)

        Then("identity equality and cross-store recovery, idempotency, and committed-transition proof remain mandatory")
        diagnostics.map(_.code) shouldBe Vector(
          DiagnosticCode.MismatchedSameStoreIdentity,
          DiagnosticCode.IncompleteCrossStoreDeliveryProof
        )
      }
    }

    "E5 remain a provider-neutral three-operation SPI" must _e5 {
      "expose no default store or workflow runtime behavior" in {
        Given("the declared persistence service boundary")
        val methods = classOf[WorkflowInstancePersistence].getDeclaredMethods
          .filterNot(method => java.lang.reflect.Modifier.isStatic(method.getModifiers))
          .map(_.getName)
          .toSet

        When("its public SPI shape is inspected")
        val isinterface = classOf[WorkflowInstancePersistence].isInterface

        Then("only create, load, and append are available to a provider implementation")
        isinterface shouldBe true
        methods shouldBe Set("create", "load", "append")
      }
    }

    "E6 retain an admitted definition behind immutable binding facts" must _e6 {
      "translate generated admission failures and reject reflected fact divergence" in {
        Given("an admitted generated definition and a generated definition with a forged revision")
        val definition = _definition()
        val tamperedDefinition = definition.copy(
          workflow = definition.workflow.copy(revision = WorkflowRevision("forged-revision"))
        )
        val binding = _take(WorkflowInstancePersistence.bindDefinitionC(definition))

        When("generated admission and a reflected private binding construction are attempted")
        val admission = _diagnostic(WorkflowInstancePersistence.bindDefinitionC(tamperedDefinition))
        val constructor = classOf[DefinitionBinding].getDeclaredConstructors match {
          case Array(value) => value
          case values => fail(s"expected one DefinitionBinding constructor but got ${values.length}")
        }
        constructor.setAccessible(true)
        val forged = constructor.newInstance(
          WorkflowDefinitionIdentity("forged-workflow"),
          binding.workflowRevision,
          binding.sourceCorrelation,
          binding.producerAbiIdentity,
          binding.workflowAbiIdentity,
          binding.bootstrapAbiIdentity,
          binding.producerRevision,
          binding.fixtureSha256,
          definition
        ).asInstanceOf[DefinitionBinding]
        val forgedDiagnostic = _diagnostic(forged.validateC)
        val forgedRecordDiagnostic = _diagnostic(_initial_record(forged).validateC)

        Then("the persistence boundary preserves the generated diagnostic and rejects divergent public facts")
        admission.code shouldBe DiagnosticCode.GeneratedAbiAdmissionRejected
        admission.data("generated-code") shouldBe GeneratedWorkflowAbi.DiagnosticCode.UnsupportedWorkflowRevision.value
        forgedDiagnostic.code shouldBe DiagnosticCode.InvalidDefinitionIdentity
        forgedRecordDiagnostic.code shouldBe DiagnosticCode.InvalidDefinitionIdentity
      }
    }
  }

  private final class ProbePersistence extends WorkflowInstancePersistence {
    def create(
      configuration: Configuration,
      record: InstanceRecord
    ): Consequence[InstanceRecord] =
      Consequence.success(record)

    def load(
      configuration: Configuration,
      identity: InstanceIdentity
    ): Consequence[Option[InstanceRecord]] =
      Consequence.success(None)

    def append(
      configuration: Configuration,
      identity: InstanceIdentity,
      expectedRevision: InstanceRevision,
      entry: HistoryEntry
    ): Consequence[InstanceRecord] =
      Consequence.notImplemented("probe only verifies the SPI declaration")
  }

  private def _initial_record(
    binding: DefinitionBinding,
    suffix: String = "one"
  ): InstanceRecord =
    InstanceRecord(
      identity = InstanceIdentity(s"instance-$suffix"),
      definition = binding,
      revision = initialRevision,
      lifecycle = Lifecycle.NotStarted,
      currentProgression = None,
      correlation = CorrelationReference(s"correlation-$suffix"),
      causation = CausationReference(s"causation-$suffix"),
      derivedCompositeOccurrence = Some(DerivedCompositeOccurrenceReference(s"composite-$suffix")),
      committedPredecessor = Some(CommittedEntityTransitionReference(s"committed-$suffix")),
      suspension = None,
      history = Vector.empty
    )

  private def _active_entry(suffix: String): HistoryEntry =
    HistoryEntry(
      sequence = HistorySequence(1L),
      revision = InstanceRevision(1L),
      lifecycle = Lifecycle.Active,
      currentProgression = Some(ProgressionReference(s"progress-$suffix")),
      correlation = CorrelationReference(s"correlation-$suffix"),
      causation = CausationReference(s"causation-$suffix"),
      derivedCompositeOccurrence = Some(DerivedCompositeOccurrenceReference(s"composite-$suffix")),
      committedPredecessor = Some(CommittedEntityTransitionReference(s"committed-$suffix")),
      suspension = None
    )

  private val _same_store_configuration = Configuration(
    workflowStore = NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
    migrationPolicy = MigrationPolicy("consumer-managed-v1"),
    retentionPolicy = RetentionPolicy("consumer-managed-v1"),
    leasePolicy = LeasePolicy("consumer-managed-v1"),
    delivery = DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store"))
  )

  private def _definition(): Definition = {
    val root = GeneratedSourceLocation(
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
        WorkflowIdentity("WorkflowProducer"),
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
    source: GeneratedSourceLocation
  ): OperationDescriptor =
    OperationDescriptor(
      ServiceIdentity("WorkflowService"),
      OperationIdentity(operation),
      Some(TypeIdentity("ReviewContext")),
      Some(TypeIdentity("ReviewResult")),
      source
    )

  private val _abi_source = GeneratedSourceLocation(
    "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala",
    1
  )

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))

  private def _diagnostic[A](result: Consequence[A]): Diagnostic =
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.getException match {
          case Some(exception: WorkflowInstancePersistenceException) => exception.diagnostic
          case other => fail(s"expected workflow instance persistence diagnostic but got $other")
        }
      case other =>
        fail(s"expected workflow instance persistence failure but got $other")
    }
}
