package org.goldenport.cncf.workflow

import org.goldenport.Consequence

/*
 * @since   Sep. 21, 2026
 * @version Sep. 21, 2026
 * @author  ASAMI, Tomoharu
 */
/** Typed, version-bound admission contract for Cozy Phase 62.3 workflow metadata.
  * It is deliberately metadata-only: it neither parses CML nor adopts workflow
  * runtime behavior.
  */
trait GeneratedWorkflowMetadataProvider {
  def generatedWorkflowDefinitions: Vector[GeneratedWorkflowAbi.Definition] =
    Vector.empty
}

final class GeneratedWorkflowAbiAdmissionException(
  val diagnostic: GeneratedWorkflowAbi.Diagnostic
) extends IllegalArgumentException(diagnostic.render)

object GeneratedWorkflowAbi {
  final case class ProducerAbiIdentity(value: String)
  final case class WorkflowAbiIdentity(value: String)
  final case class BootstrapSchemaIdentity(value: String)
  final case class ProducerRevision(value: String)
  final case class FixtureSha256(value: String)

  final case class SourceLocation(
    resource: String,
    line: Int,
    column: Option[Int] = None
  )

  final case class WorkflowIdentity(value: String)
  final case class WorkflowRevision(value: String)
  final case class CompositeStateMachineIdentity(value: String)
  final case class StateMachineIdentity(value: String)
  final case class StateIdentity(value: String)
  final case class ActionIdentity(value: String)
  final case class ServiceIdentity(value: String)
  final case class OperationIdentity(value: String)
  final case class TypeIdentity(value: String)
  final case class RequiredSpiIdentity(value: String)

  final case class WorkflowSourceCorrelation(
    root: SourceLocation,
    definition: SourceLocation
  )

  final case class WorkflowDescriptor(
    identity: WorkflowIdentity,
    revision: WorkflowRevision,
    source: WorkflowSourceCorrelation
  )

  final case class StateDescriptor(
    identity: StateIdentity,
    sourceLocation: SourceLocation
  )

  final case class CompositeStateMachineDescriptor(
    identity: CompositeStateMachineIdentity,
    stateMachineIdentity: StateMachineIdentity,
    states: Vector[StateDescriptor],
    sourceLocation: SourceLocation
  )

  final case class OperationDescriptor(
    serviceIdentity: ServiceIdentity,
    operationIdentity: OperationIdentity,
    inputType: Option[TypeIdentity],
    resultType: Option[TypeIdentity],
    sourceLocation: SourceLocation
  ) {
    def key: String = s"${serviceIdentity.value}.${operationIdentity.value}"
  }

  enum ActionKind(val value: String) {
    case Operation extends ActionKind("OPERATION")
  }

  final case class ActionDescriptor(
    identity: ActionIdentity,
    kind: ActionKind,
    operation: OperationDescriptor,
    inputBinding: Option[String],
    sourceLocation: SourceLocation
  )

  final case class RequiredSpiDescriptor(
    identity: RequiredSpiIdentity,
    actionIdentity: ActionIdentity,
    operation: OperationDescriptor,
    capabilitySource: SourceLocation,
    actionSource: SourceLocation
  )

  /** A closed schema-shape descriptor from the supported workflow ABI source. */
  final case class SchemaShapeDescriptor(
    identity: String,
    members: Set[String],
    sourceLocation: SourceLocation
  )

  final case class Definition(
    producerAbiIdentity: ProducerAbiIdentity,
    workflowAbiIdentity: WorkflowAbiIdentity,
    bootstrapSchemaIdentity: BootstrapSchemaIdentity,
    producerRevision: ProducerRevision,
    fixtureSha256: FixtureSha256,
    workflow: WorkflowDescriptor,
    compositeStateMachine: CompositeStateMachineDescriptor,
    actions: Vector[ActionDescriptor],
    requiredSpis: Vector[RequiredSpiDescriptor],
    schemaShapes: Vector[SchemaShapeDescriptor]
  )

  enum DiagnosticCode(val value: String) {
    case MissingDefinitions extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-001")
    case UnsupportedProducerAbi extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-002")
    case UnsupportedWorkflowAbi extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-003")
    case UnsupportedBootstrapSchema extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-004")
    case UnsupportedProducerRevision extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-005")
    case UnsupportedFixtureSha256 extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-006")
    case UnsupportedWorkflowRevision extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-007")
    case MissingRequiredProvenance extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-008")
    case MissingRequiredIdentity extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-009")
    case DuplicateDefinitionIdentity extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-010")
    case DuplicateStateIdentity extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-011")
    case DuplicateRequiredSpiIdentity extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-012")
    case DuplicateActionIdentity extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-013")
    case MissingRequiredStructure extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-014")
    case MissingRequiredSchemaShape extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-015")
    case DuplicateSchemaShapeIdentity extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-016")
    case UnknownSchemaShape extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-017")
    case IncompatibleSchemaShape extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-018")
    case RequiredSpiActionMismatch extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-019")
    case RequiredSpiOperationMismatch extends DiagnosticCode("CWF77-GENERATED-WORKFLOW-ABI-020")
  }

  final case class Diagnostic(
    code: DiagnosticCode,
    data: Map[String, String]
  ) {
    def render: String = {
      val details = data.toVector.sortBy(_._1).map { case (key, value) =>
        s"$key=$value"
      }.mkString(", ")
      s"${code.value}: $details"
    }
  }

  val acceptedProducerAbiIdentity: ProducerAbiIdentity =
    ProducerAbiIdentity("workflow-producer-v1")
  val acceptedWorkflowAbiIdentity: WorkflowAbiIdentity =
    WorkflowAbiIdentity("cozy.cml.statemachine-workflow-abi.v1")
  val acceptedBootstrapSchemaIdentity: BootstrapSchemaIdentity =
    BootstrapSchemaIdentity("cozy.cml.statemachine-workflow-bootstrap.v1")
  val acceptedProducerRevision: ProducerRevision =
    ProducerRevision("workflow-producer-v1")
  val acceptedFixtureSha256: FixtureSha256 =
    FixtureSha256("8d52e2116657fdc37bb1a422851e4047790efeda96831b4154ca9e46ca799827")
  val acceptedWorkflowRevision: WorkflowRevision =
    WorkflowRevision("workflow-producer-v1")

  /** Closed vocabulary from `cozy.cml.statemachine-workflow-abi.v1`. */
  val requiredSchemaShapes: Map[String, Set[String]] = Map(
    "ActionExecution" -> Set("Completed", "Suspended", "Failed"),
    "Continuation" -> Set(
      "runId",
      "continuationId",
      "expectedRevision",
      "requiredOperation",
      "context"
    ),
    "Context" -> Set("ContextReference", "ContextSnapshot", "ContextBundle"),
    "Completion" -> Set(
      "StateMachineOperationResult",
      "StateMachineResultTypeReference",
      "ContextReference"
    ),
    "Evidence" -> Set("EvidenceContract", "ContextReference")
  )

  def admitC(definitions: Vector[Definition]): Consequence[Vector[Definition]] =
    _admission_diagnostic(definitions) match {
      case Some(diagnostic) =>
        Consequence.Failure(
          org.goldenport.Conclusion.from(
            new GeneratedWorkflowAbiAdmissionException(diagnostic)
          )
        )
      case None =>
        Consequence.success(definitions)
    }

  private def _admission_diagnostic(
    definitions: Vector[Definition]
  ): Option[Diagnostic] =
    if (definitions.isEmpty)
      Some(Diagnostic(
        DiagnosticCode.MissingDefinitions,
        Map("required" -> "generated-workflow-definition")
      ))
    else
      definitions.iterator
        .map(_definition_diagnostic)
        .collectFirst { case Some(diagnostic) => diagnostic }
        .orElse(_duplicate_diagnostic(definitions))

  private def _definition_diagnostic(
    definition: Definition
  ): Option[Diagnostic] =
    if (definition.producerAbiIdentity != acceptedProducerAbiIdentity)
      Some(_identity_diagnostic(
        DiagnosticCode.UnsupportedProducerAbi,
        "producer-abi",
        acceptedProducerAbiIdentity.value,
        definition.producerAbiIdentity.value,
        definition.workflow.identity.value
      ))
    else if (definition.workflowAbiIdentity != acceptedWorkflowAbiIdentity)
      Some(_identity_diagnostic(
        DiagnosticCode.UnsupportedWorkflowAbi,
        "workflow-abi",
        acceptedWorkflowAbiIdentity.value,
        definition.workflowAbiIdentity.value,
        definition.workflow.identity.value
      ))
    else if (definition.bootstrapSchemaIdentity != acceptedBootstrapSchemaIdentity)
      Some(_identity_diagnostic(
        DiagnosticCode.UnsupportedBootstrapSchema,
        "bootstrap-schema",
        acceptedBootstrapSchemaIdentity.value,
        definition.bootstrapSchemaIdentity.value,
        definition.workflow.identity.value
      ))
    else if (definition.producerRevision != acceptedProducerRevision)
      Some(_identity_diagnostic(
        DiagnosticCode.UnsupportedProducerRevision,
        "producer-revision",
        acceptedProducerRevision.value,
        definition.producerRevision.value,
        definition.workflow.identity.value
      ))
    else if (definition.fixtureSha256 != acceptedFixtureSha256)
      Some(_identity_diagnostic(
        DiagnosticCode.UnsupportedFixtureSha256,
        "fixture-sha256",
        acceptedFixtureSha256.value,
        definition.fixtureSha256.value,
        definition.workflow.identity.value
      ))
    else if (definition.workflow.revision != acceptedWorkflowRevision)
      Some(_identity_diagnostic(
        DiagnosticCode.UnsupportedWorkflowRevision,
        "workflow-revision",
        acceptedWorkflowRevision.value,
        definition.workflow.revision.value,
        definition.workflow.identity.value
      ))
    else
      _structure_diagnostic(definition)
        .orElse(_identity_diagnostic(definition))
        .orElse(_provenance_diagnostic(definition))
        .orElse(_required_spi_diagnostic(definition))
        .orElse(_schema_shape_diagnostic(definition))

  private def _identity_diagnostic(
    code: DiagnosticCode,
    kind: String,
    expected: String,
    actual: String,
    workflow: String
  ): Diagnostic =
    Diagnostic(code, Map(
      "actual" -> actual,
      "expected" -> expected,
      "kind" -> kind,
      "workflow" -> workflow
    ))

  private def _structure_diagnostic(
    definition: Definition
  ): Option[Diagnostic] =
    Vector(
      "states" -> definition.compositeStateMachine.states.size,
      "actions" -> definition.actions.size,
      "required-spis" -> definition.requiredSpis.size,
      "schema-shapes" -> definition.schemaShapes.size
    ).collectFirst {
      case (kind, size) if size == 0 =>
        Diagnostic(DiagnosticCode.MissingRequiredStructure, Map(
          "kind" -> kind,
          "workflow" -> definition.workflow.identity.value
        ))
    }

  private def _identity_diagnostic(definition: Definition): Option[Diagnostic] = {
    val required = Vector(
      "workflow" -> definition.workflow.identity.value,
      "workflow-revision" -> definition.workflow.revision.value,
      "composite-state-machine" -> definition.compositeStateMachine.identity.value,
      "state-machine" -> definition.compositeStateMachine.stateMachineIdentity.value
    ) ++ definition.compositeStateMachine.states.map(x => "state" -> x.identity.value) ++
      definition.actions.flatMap(x => Vector(
        "action" -> x.identity.value,
        "operation-service" -> x.operation.serviceIdentity.value,
        "operation" -> x.operation.operationIdentity.value
      ) ++ x.operation.inputType.map(y => "operation-input-type" -> y.value) ++
        x.operation.resultType.map(y => "operation-result-type" -> y.value) ++
        x.inputBinding.map(y => "action-input-binding" -> y)) ++
      definition.requiredSpis.flatMap(x => Vector(
        "required-spi" -> x.identity.value,
        "required-spi-action" -> x.actionIdentity.value,
        "required-spi-operation-service" -> x.operation.serviceIdentity.value,
        "required-spi-operation" -> x.operation.operationIdentity.value
      ) ++ x.operation.inputType.map(y => "required-spi-input-type" -> y.value) ++
        x.operation.resultType.map(y => "required-spi-result-type" -> y.value)) ++
      definition.schemaShapes.flatMap(x =>
        Vector("schema-shape" -> x.identity) ++ x.members.map(y => "schema-shape-member" -> y)
      )
    required.collectFirst {
      case (kind, value) if _is_empty(value) =>
        Diagnostic(DiagnosticCode.MissingRequiredIdentity, Map(
          "kind" -> kind,
          "workflow" -> definition.workflow.identity.value
        ))
    }.orElse(
      _first_duplicate(definition.compositeStateMachine.states.map(_.identity.value)).map {
        identity =>
          Diagnostic(DiagnosticCode.DuplicateStateIdentity, Map(
            "identity" -> identity,
            "kind" -> "state",
            "workflow" -> definition.workflow.identity.value
          ))
      }
    )
  }

  private def _provenance_diagnostic(
    definition: Definition
  ): Option[Diagnostic] = {
    val locations = Vector(
      "workflow-root" -> Vector(definition.workflow.source.root),
      "workflow-definition" -> Vector(definition.workflow.source.definition),
      "composite-state-machine" -> Vector(definition.compositeStateMachine.sourceLocation)
    ) ++ definition.compositeStateMachine.states.map(x => "state" -> Vector(x.sourceLocation)) ++
      definition.actions.flatMap(x => Vector(
        "action" -> Vector(x.sourceLocation),
        "operation" -> Vector(x.operation.sourceLocation)
      )) ++ definition.requiredSpis.flatMap(x => Vector(
        "required-spi-capability" -> Vector(x.capabilitySource),
        "required-spi-action" -> Vector(x.actionSource),
        "required-spi-operation" -> Vector(x.operation.sourceLocation)
      )) ++ definition.schemaShapes.map(x => "schema-shape" -> Vector(x.sourceLocation))
    locations.collectFirst {
      case (kind, values) if values.exists(_is_empty_location) =>
        Diagnostic(DiagnosticCode.MissingRequiredProvenance, Map(
          "kind" -> kind,
          "workflow" -> definition.workflow.identity.value
        ))
    }
  }

  private def _required_spi_diagnostic(
    definition: Definition
  ): Option[Diagnostic] =
    definition.requiredSpis.iterator.map { requiredspi =>
      definition.actions.find(_.identity == requiredspi.actionIdentity) match {
        case None => Some(Diagnostic(
          DiagnosticCode.RequiredSpiActionMismatch,
          Map(
            "action" -> requiredspi.actionIdentity.value,
            "required-spi" -> requiredspi.identity.value,
            "workflow" -> definition.workflow.identity.value
          )
        ))
        case Some(action) if action.operation != requiredspi.operation => Some(Diagnostic(
          DiagnosticCode.RequiredSpiOperationMismatch,
          Map(
            "action" -> action.identity.value,
            "required-spi" -> requiredspi.identity.value,
            "workflow" -> definition.workflow.identity.value
          )
        ))
        case Some(_) => None
      }
    }.collectFirst { case Some(diagnostic) => diagnostic }

  private def _schema_shape_diagnostic(
    definition: Definition
  ): Option[Diagnostic] = {
    val shapesbyidentity = definition.schemaShapes.groupBy(_.identity)
    _first_duplicate(definition.schemaShapes.map(_.identity)).map { identity =>
      Diagnostic(DiagnosticCode.DuplicateSchemaShapeIdentity, Map(
        "identity" -> identity,
        "workflow" -> definition.workflow.identity.value
      ))
    }.orElse {
      val unknown = shapesbyidentity.keySet.diff(requiredSchemaShapes.keySet).toVector.sorted
      unknown.headOption.map { identity =>
        Diagnostic(DiagnosticCode.UnknownSchemaShape, Map(
          "identity" -> identity,
          "workflow" -> definition.workflow.identity.value
        ))
      }
    }.orElse {
      val missing = requiredSchemaShapes.keySet.diff(shapesbyidentity.keySet).toVector.sorted
      if (missing.isEmpty) None
      else Some(Diagnostic(DiagnosticCode.MissingRequiredSchemaShape, Map(
        "missing" -> missing.mkString(","),
        "workflow" -> definition.workflow.identity.value
      )))
    }.orElse {
      definition.schemaShapes.collectFirst {
        case shape if requiredSchemaShapes.get(shape.identity).exists(_ != shape.members) =>
          Diagnostic(DiagnosticCode.IncompatibleSchemaShape, Map(
            "actual-members" -> shape.members.toVector.sorted.mkString(","),
            "expected-members" -> requiredSchemaShapes(shape.identity).toVector.sorted.mkString(","),
            "identity" -> shape.identity,
            "workflow" -> definition.workflow.identity.value
          ))
      }
    }
  }

  private def _duplicate_diagnostic(
    definitions: Vector[Definition]
  ): Option[Diagnostic] =
    _first_duplicate(definitions.map(_.workflow.identity.value)).map { identity =>
      _duplicate_identity_diagnostic(
        DiagnosticCode.DuplicateDefinitionIdentity,
        "definition",
        identity
      )
    }.orElse(
      _first_duplicate(definitions.flatMap(_.requiredSpis.map(_.identity.value))).map { identity =>
        _duplicate_identity_diagnostic(
          DiagnosticCode.DuplicateRequiredSpiIdentity,
          "required-spi",
          identity
        )
      }
    ).orElse(
      _first_duplicate(definitions.flatMap(_.actions.map(_.identity.value))).map { identity =>
        _duplicate_identity_diagnostic(
          DiagnosticCode.DuplicateActionIdentity,
          "action",
          identity
        )
      }
    )

  private def _duplicate_identity_diagnostic(
    code: DiagnosticCode,
    kind: String,
    identity: String
  ): Diagnostic =
    Diagnostic(code, Map("identity" -> identity, "kind" -> kind))

  private def _first_duplicate(values: Vector[String]): Option[String] =
    values.indices.iterator.collectFirst {
      case index if values.take(index).contains(values(index)) => values(index)
    }

  private def _is_empty(value: String): Boolean =
    Option(value).forall(_.trim.isEmpty)

  private def _is_empty_location(location: SourceLocation): Boolean =
    _is_empty(location.resource) || location.line < 1 || location.column.exists(_ < 1)
}
