package org.goldenport.cncf.workflow

import org.goldenport.{Conclusion, Consequence}

/*
 * @since   Sep. 21, 2026
 * @version Sep. 21, 2026
 * @author  ASAMI, Tomoharu
 */
/** Provider-neutral persistence boundary for an independently durable workflow
  * instance. This is a contract only: it supplies no storage, transaction,
  * migration, lease, dispatch, or continuation-resume implementation.
  */
trait WorkflowInstancePersistence {
  def create(
    configuration: WorkflowInstancePersistence.Configuration,
    record: WorkflowInstancePersistence.InstanceRecord
  ): Consequence[WorkflowInstancePersistence.InstanceRecord]

  def load(
    configuration: WorkflowInstancePersistence.Configuration,
    identity: WorkflowInstancePersistence.InstanceIdentity
  ): Consequence[Option[WorkflowInstancePersistence.InstanceRecord]]

  def append(
    configuration: WorkflowInstancePersistence.Configuration,
    identity: WorkflowInstancePersistence.InstanceIdentity,
    expectedRevision: WorkflowInstancePersistence.InstanceRevision,
    entry: WorkflowInstancePersistence.HistoryEntry
  ): Consequence[WorkflowInstancePersistence.InstanceRecord]
}

final class WorkflowInstancePersistenceException(
  val diagnostic: WorkflowInstancePersistence.Diagnostic
) extends IllegalArgumentException(diagnostic.render)

object WorkflowInstancePersistence {
  final case class WorkflowDefinitionIdentity(value: String)
  final case class WorkflowDefinitionRevision(value: String)
  final case class SourceLocation(
    resource: String,
    line: Int,
    column: Option[Int] = None
  )
  final case class SourceCorrelation(
    root: SourceLocation,
    definition: SourceLocation
  )

  final case class ProducerAbiIdentity(value: String)
  final case class WorkflowAbiIdentity(value: String)
  final case class BootstrapAbiIdentity(value: String)
  final case class ProducerRevision(value: String)
  final case class FixtureSha256(value: String)

  /** A version-pinned, source-correlated persistence view of an admitted ABI. */
  final class DefinitionBinding private[workflow] (
    val workflowIdentity: WorkflowDefinitionIdentity,
    val workflowRevision: WorkflowDefinitionRevision,
    val sourceCorrelation: SourceCorrelation,
    val producerAbiIdentity: ProducerAbiIdentity,
    val workflowAbiIdentity: WorkflowAbiIdentity,
    val bootstrapAbiIdentity: BootstrapAbiIdentity,
    val producerRevision: ProducerRevision,
    val fixtureSha256: FixtureSha256,
    private[workflow] val admittedDefinition: GeneratedWorkflowAbi.Definition
  ) {
    def validateC: Consequence[DefinitionBinding] =
      WorkflowInstancePersistence.validateC(this)
  }

  final case class InstanceIdentity(value: String)
  final case class InstanceRevision(value: Long) {
    def next: InstanceRevision = InstanceRevision(value + 1L)
  }
  final case class HistorySequence(value: Long)
  final case class ProgressionReference(value: String)
  final case class CorrelationReference(value: String)
  final case class CausationReference(value: String)
  final case class DerivedCompositeOccurrenceReference(value: String)
  final case class CommittedEntityTransitionReference(value: String)

  /** Closed Phase 64 workflow lifecycle vocabulary. */
  enum Lifecycle(val value: String) {
    case NotStarted extends Lifecycle("not-started")
    case Active extends Lifecycle("active")
    case Completed extends Lifecycle("completed")
  }

  final case class ContinuationIdentity(value: String)
  final case class ContextSnapshotReference(value: String)
  final case class CompletionReference(value: String)
  final case class EvidenceReference(value: String)

  /** Persisted suspension facts only; it neither claims nor resumes work. */
  final case class SuspensionBoundary(
    continuationIdentity: ContinuationIdentity,
    expectedRevision: InstanceRevision,
    contextSnapshot: ContextSnapshotReference,
    completion: CompletionReference,
    evidence: EvidenceReference,
    resumeCorrelation: CorrelationReference
  ) {
    def validateC: Consequence[SuspensionBoundary] =
      WorkflowInstancePersistence.validateC(this)
  }

  final case class HistoryEntry(
    sequence: HistorySequence,
    revision: InstanceRevision,
    lifecycle: Lifecycle,
    currentProgression: Option[ProgressionReference],
    correlation: CorrelationReference,
    causation: CausationReference,
    derivedCompositeOccurrence: Option[DerivedCompositeOccurrenceReference],
    committedPredecessor: Option[CommittedEntityTransitionReference],
    suspension: Option[SuspensionBoundary]
  )

  /** Immutable record whose present facts are the final accepted history entry. */
  final case class InstanceRecord(
    identity: InstanceIdentity,
    definition: DefinitionBinding,
    revision: InstanceRevision,
    lifecycle: Lifecycle,
    currentProgression: Option[ProgressionReference],
    correlation: CorrelationReference,
    causation: CausationReference,
    derivedCompositeOccurrence: Option[DerivedCompositeOccurrenceReference],
    committedPredecessor: Option[CommittedEntityTransitionReference],
    suspension: Option[SuspensionBoundary],
    history: Vector[HistoryEntry]
  ) {
    def validateC: Consequence[InstanceRecord] =
      WorkflowInstancePersistence.validateC(this)

    /** Derives a subsequent immutable record only from one contiguous history entry. */
    def appendC(
      expectedRevision: InstanceRevision,
      entry: HistoryEntry
    ): Consequence[InstanceRecord] =
      validateC.flatMap { current =>
        if (expectedRevision != current.revision)
          _failure_c(Diagnostic(
            DiagnosticCode.IncorrectExpectedRevision,
            Map(
              "actual" -> current.revision.value.toString,
              "expected" -> _revision_value(expectedRevision),
              "instance" -> _identity_value(current.identity)
            )
          ))
        else if (current.revision.value == Long.MaxValue)
          _failure_c(Diagnostic(
            DiagnosticCode.InvalidNextRevision,
            Map("instance" -> _identity_value(current.identity), "reason" -> "revision-overflow")
          ))
        else if (entry == null)
          _failure_c(Diagnostic(
            DiagnosticCode.NonAppendOnlyHistory,
            Map("instance" -> _identity_value(current.identity), "reason" -> "missing-entry")
          ))
        else {
          val expectedsequence = HistorySequence(current.history.size.toLong + 1L)
          val expectednext = current.revision.next
          if (entry.sequence != expectedsequence)
            _failure_c(Diagnostic(
              DiagnosticCode.InvalidNextSequence,
              Map(
                "actual" -> entry.sequence.value.toString,
                "expected" -> expectedsequence.value.toString,
                "instance" -> _identity_value(current.identity)
              )
            ))
          else if (entry.revision != expectednext)
            _failure_c(Diagnostic(
              DiagnosticCode.InvalidNextRevision,
              Map(
                "actual" -> entry.revision.value.toString,
                "expected" -> expectednext.value.toString,
                "instance" -> _identity_value(current.identity)
              )
            ))
          else {
            val next = current.copy(
              revision = entry.revision,
              lifecycle = entry.lifecycle,
              currentProgression = entry.currentProgression,
              correlation = entry.correlation,
              causation = entry.causation,
              derivedCompositeOccurrence = entry.derivedCompositeOccurrence,
              committedPredecessor = entry.committedPredecessor,
              suspension = entry.suspension,
              history = current.history :+ entry
            )
            WorkflowInstancePersistence.validateC(next)
          }
        }
      }
  }

  final case class WorkflowStoreIdentity(value: String)
  final case class NamedWorkflowStore(
    name: String,
    identity: WorkflowStoreIdentity
  )
  final case class MigrationPolicy(value: String)
  final case class RetentionPolicy(value: String)
  final case class LeasePolicy(value: String)
  final case class IdempotencyKey(value: String)
  final case class RecoverabilityReference(value: String)

  /** The only delivery alternatives admitted by the provider-neutral boundary. */
  enum DeliveryConfiguration {
    case SameStore(entityStoreIdentity: WorkflowStoreIdentity)
    case CrossStoreCommittedTransition(
      entityStoreIdentity: WorkflowStoreIdentity,
      committedTransition: CommittedEntityTransitionReference,
      idempotencyKey: IdempotencyKey,
      recoverability: RecoverabilityReference
    )
  }

  /** Every consumer must supply all storage and delivery policy facts explicitly. */
  final case class Configuration(
    workflowStore: NamedWorkflowStore,
    migrationPolicy: MigrationPolicy,
    retentionPolicy: RetentionPolicy,
    leasePolicy: LeasePolicy,
    delivery: DeliveryConfiguration
  ) {
    def validateC: Consequence[Configuration] =
      WorkflowInstancePersistence.validateC(this)
  }

  enum DiagnosticCode(val value: String) {
    case GeneratedAbiAdmissionRejected extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-001")
    case MissingAbiProvenance extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-002")
    case UnsupportedAbiProvenance extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-003")
    case InvalidDefinitionIdentity extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-004")
    case InvalidInstanceIdentity extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-005")
    case InvalidInitialRevision extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-006")
    case IncorrectExpectedRevision extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-007")
    case InvalidNextRevision extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-008")
    case InvalidNextSequence extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-009")
    case IllegalLifecycleProgression extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-010")
    case NonAppendOnlyHistory extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-011")
    case IncompleteSuspensionBoundary extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-012")
    case MissingCorrelationOrCausation extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-013")
    case MissingPersistencePolicy extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-014")
    case MismatchedSameStoreIdentity extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-015")
    case IncompleteCrossStoreDeliveryProof extends DiagnosticCode("CWF77-WORKFLOW-PERSISTENCE-016")
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

  val initialRevision: InstanceRevision = InstanceRevision(0L)

  /** Binds generated metadata only after its closed ABI admission has succeeded. */
  def bindDefinitionC(
    definition: GeneratedWorkflowAbi.Definition
  ): Consequence[DefinitionBinding] =
    if (definition == null)
      _failure_c(Diagnostic(
        DiagnosticCode.MissingAbiProvenance,
        Map("kind" -> "generated-definition")
      ))
    else
      _admit_definition_c(definition).map { accepted =>
      new DefinitionBinding(
        workflowIdentity = WorkflowDefinitionIdentity(accepted.workflow.identity.value),
        workflowRevision = WorkflowDefinitionRevision(accepted.workflow.revision.value),
        sourceCorrelation = SourceCorrelation(
          SourceLocation(
            accepted.workflow.source.root.resource,
            accepted.workflow.source.root.line,
            accepted.workflow.source.root.column
          ),
          SourceLocation(
            accepted.workflow.source.definition.resource,
            accepted.workflow.source.definition.line,
            accepted.workflow.source.definition.column
          )
        ),
        producerAbiIdentity = ProducerAbiIdentity(accepted.producerAbiIdentity.value),
        workflowAbiIdentity = WorkflowAbiIdentity(accepted.workflowAbiIdentity.value),
        bootstrapAbiIdentity = BootstrapAbiIdentity(accepted.bootstrapSchemaIdentity.value),
        producerRevision = ProducerRevision(accepted.producerRevision.value),
        fixtureSha256 = FixtureSha256(accepted.fixtureSha256.value),
        admittedDefinition = accepted
      )
      }

  def validateC(binding: DefinitionBinding): Consequence[DefinitionBinding] =
    if (binding == null)
      _failure_c(Diagnostic(DiagnosticCode.MissingAbiProvenance, Map("kind" -> "definition-binding")))
    else
      _admit_definition_c(binding.admittedDefinition).flatMap { admitted =>
        _binding_diagnostic(binding).orElse(_binding_consistency_diagnostic(binding, admitted)) match {
          case Some(diagnostic) => _failure_c(diagnostic)
          case None => Consequence.success(binding)
        }
      }

  def validateC(boundary: SuspensionBoundary): Consequence[SuspensionBoundary] =
    _suspension_diagnostic(boundary, None) match {
      case Some(diagnostic) => _failure_c(diagnostic)
      case None => Consequence.success(boundary)
    }

  def validateC(configuration: Configuration): Consequence[Configuration] =
    _configuration_diagnostic(configuration) match {
      case Some(diagnostic) => _failure_c(diagnostic)
      case None => Consequence.success(configuration)
    }

  def validateC(record: InstanceRecord): Consequence[InstanceRecord] =
    if (record == null)
      _record_diagnostic(record) match {
        case Some(diagnostic) => _failure_c(diagnostic)
        case None => Consequence.success(record)
      }
    else
      validateC(record.definition).flatMap { _ =>
        _record_diagnostic(record) match {
          case Some(diagnostic) => _failure_c(diagnostic)
          case None => Consequence.success(record)
        }
      }

  private def _admit_definition_c(definition: GeneratedWorkflowAbi.Definition): Consequence[GeneratedWorkflowAbi.Definition] =
    if (definition == null)
      _failure_c(Diagnostic(DiagnosticCode.MissingAbiProvenance, Map("kind" -> "generated-definition")))
    else
      GeneratedWorkflowAbi.admitC(Vector(definition)) match {
        case Consequence.Success(admitted) => Consequence.success(admitted.head)
        case Consequence.Failure(conclusion) =>
          conclusion.getException match {
            case Some(exception: GeneratedWorkflowAbiAdmissionException) =>
              _failure_c(Diagnostic(
                DiagnosticCode.GeneratedAbiAdmissionRejected,
                exception.diagnostic.data + ("generated-code" -> exception.diagnostic.code.value)
              ))
            case _ =>
              _failure_c(Diagnostic(
                DiagnosticCode.GeneratedAbiAdmissionRejected,
                Map("generated-code" -> "unknown")
              ))
          }
      }

  private def _binding_consistency_diagnostic(
    binding: DefinitionBinding,
    admitted: GeneratedWorkflowAbi.Definition
  ): Option[Diagnostic] = {
    val sourceCorrelation = SourceCorrelation(
      SourceLocation(admitted.workflow.source.root.resource, admitted.workflow.source.root.line, admitted.workflow.source.root.column),
      SourceLocation(admitted.workflow.source.definition.resource, admitted.workflow.source.definition.line, admitted.workflow.source.definition.column)
    )
    if (binding.workflowIdentity != WorkflowDefinitionIdentity(admitted.workflow.identity.value))
      Some(Diagnostic(DiagnosticCode.InvalidDefinitionIdentity, Map("kind" -> "workflow-identity")))
    else if (binding.workflowRevision != WorkflowDefinitionRevision(admitted.workflow.revision.value))
      Some(Diagnostic(DiagnosticCode.InvalidDefinitionIdentity, Map("kind" -> "workflow-revision")))
    else if (binding.sourceCorrelation != sourceCorrelation)
      Some(Diagnostic(DiagnosticCode.InvalidDefinitionIdentity, Map("kind" -> "source-correlation")))
    else {
      val provenance = Vector(
        "producer-abi" -> _value(binding.producerAbiIdentity),
        "workflow-abi" -> _value(binding.workflowAbiIdentity),
        "bootstrap-abi" -> _value(binding.bootstrapAbiIdentity),
        "producer-revision" -> _value(binding.producerRevision),
        "fixture-sha256" -> _value(binding.fixtureSha256)
      )
      val expected = Vector(
        "producer-abi" -> admitted.producerAbiIdentity.value,
        "workflow-abi" -> admitted.workflowAbiIdentity.value,
        "bootstrap-abi" -> admitted.bootstrapSchemaIdentity.value,
        "producer-revision" -> admitted.producerRevision.value,
        "fixture-sha256" -> admitted.fixtureSha256.value
      )
      provenance.zip(expected).collectFirst { case ((kind, actual), (_, required)) if actual != required =>
        Diagnostic(
          DiagnosticCode.UnsupportedAbiProvenance,
          Map("actual" -> actual, "expected" -> required, "kind" -> kind, "workflow" -> _definition_identity_value(binding.workflowIdentity))
        )
      }
    }
  }

  private def _binding_diagnostic(binding: DefinitionBinding): Option[Diagnostic] =
    if (binding == null)
      Some(Diagnostic(DiagnosticCode.MissingAbiProvenance, Map("kind" -> "definition-binding")))
    else if (_blank(binding.workflowIdentity) || _blank(binding.workflowRevision))
      Some(Diagnostic(DiagnosticCode.InvalidDefinitionIdentity, Map(
        "workflow" -> _definition_identity_value(binding.workflowIdentity)
      )))
    else if (_source_diagnostic(binding.sourceCorrelation).nonEmpty)
      Some(Diagnostic(DiagnosticCode.MissingAbiProvenance, Map(
        "kind" -> "source-correlation",
        "workflow" -> _definition_identity_value(binding.workflowIdentity)
      )))
    else {
      val provenance = Vector(
        "producer-abi" -> _value(binding.producerAbiIdentity),
        "workflow-abi" -> _value(binding.workflowAbiIdentity),
        "bootstrap-abi" -> _value(binding.bootstrapAbiIdentity),
        "producer-revision" -> _value(binding.producerRevision),
        "fixture-sha256" -> _value(binding.fixtureSha256)
      )
      provenance.collectFirst {
        case (kind, value) if _blank(value) =>
          Diagnostic(DiagnosticCode.MissingAbiProvenance, Map(
            "kind" -> kind,
            "workflow" -> _definition_identity_value(binding.workflowIdentity)
          ))
      }.orElse {
        val expected = Vector(
          "producer-abi" -> GeneratedWorkflowAbi.acceptedProducerAbiIdentity.value,
          "workflow-abi" -> GeneratedWorkflowAbi.acceptedWorkflowAbiIdentity.value,
          "bootstrap-abi" -> GeneratedWorkflowAbi.acceptedBootstrapSchemaIdentity.value,
          "producer-revision" -> GeneratedWorkflowAbi.acceptedProducerRevision.value,
          "fixture-sha256" -> GeneratedWorkflowAbi.acceptedFixtureSha256.value
        )
        provenance.zip(expected).collectFirst {
          case ((kind, actual), (_, required)) if actual != required =>
            Diagnostic(DiagnosticCode.UnsupportedAbiProvenance, Map(
              "actual" -> actual,
              "expected" -> required,
              "kind" -> kind,
              "workflow" -> _definition_identity_value(binding.workflowIdentity)
            ))
        }
      }
    }

  private def _source_diagnostic(source: SourceCorrelation): Option[Unit] =
    if (source == null || _location_diagnostic(source.root) || _location_diagnostic(source.definition))
      Some(())
    else
      None

  private def _location_diagnostic(location: SourceLocation): Boolean =
    location == null || _blank(location.resource) || location.line < 1 ||
      location.column.exists(_ < 1)

  private def _suspension_diagnostic(
    boundary: SuspensionBoundary,
    recordrevision: Option[InstanceRevision]
  ): Option[Diagnostic] =
    if (boundary == null)
      Some(Diagnostic(DiagnosticCode.IncompleteSuspensionBoundary, Map("kind" -> "boundary")))
    else if (_blank(boundary.continuationIdentity) ||
      _blank(boundary.contextSnapshot) ||
      _blank(boundary.completion) ||
      _blank(boundary.evidence) ||
      _blank(boundary.resumeCorrelation))
      Some(Diagnostic(DiagnosticCode.IncompleteSuspensionBoundary, Map(
        "kind" -> "required-reference"
      )))
    else if (boundary.expectedRevision == null || boundary.expectedRevision.value < 0L)
      Some(Diagnostic(DiagnosticCode.IncompleteSuspensionBoundary, Map(
        "kind" -> "expected-revision"
      )))
    else if (recordrevision.exists(_ != boundary.expectedRevision))
      Some(Diagnostic(DiagnosticCode.IncompleteSuspensionBoundary, Map(
        "actual" -> boundary.expectedRevision.value.toString,
        "expected" -> recordrevision.map(_.value.toString).getOrElse(""),
        "kind" -> "expected-revision"
      )))
    else
      None

  private def _configuration_diagnostic(configuration: Configuration): Option[Diagnostic] =
    if (configuration == null || configuration.workflowStore == null ||
      _blank(configuration.workflowStore.name) || _blank(configuration.workflowStore.identity))
      Some(Diagnostic(DiagnosticCode.MissingPersistencePolicy, Map("kind" -> "workflow-store")))
    else if (_blank(configuration.migrationPolicy))
      Some(Diagnostic(DiagnosticCode.MissingPersistencePolicy, Map("kind" -> "migration-policy")))
    else if (_blank(configuration.retentionPolicy))
      Some(Diagnostic(DiagnosticCode.MissingPersistencePolicy, Map("kind" -> "retention-policy")))
    else if (_blank(configuration.leasePolicy))
      Some(Diagnostic(DiagnosticCode.MissingPersistencePolicy, Map("kind" -> "lease-policy")))
    else
      configuration.delivery match {
        case DeliveryConfiguration.SameStore(entitystore) =>
          if (_blank(entitystore))
            Some(Diagnostic(DiagnosticCode.MismatchedSameStoreIdentity, Map(
              "kind" -> "entity-store"
            )))
          else if (entitystore != configuration.workflowStore.identity)
            Some(Diagnostic(DiagnosticCode.MismatchedSameStoreIdentity, Map(
              "entity-store" -> _value(entitystore),
              "workflow-store" -> _value(configuration.workflowStore.identity)
            )))
          else
            None
        case DeliveryConfiguration.CrossStoreCommittedTransition(
              entitystore,
              transition,
              key,
              recoverability
            ) =>
          if (_blank(entitystore) || _blank(transition) || _blank(key) || _blank(recoverability))
            Some(Diagnostic(DiagnosticCode.IncompleteCrossStoreDeliveryProof, Map(
              "kind" -> "committed-transition/idempotency/recoverability"
            )))
          else if (entitystore == configuration.workflowStore.identity)
            Some(Diagnostic(DiagnosticCode.IncompleteCrossStoreDeliveryProof, Map(
              "kind" -> "distinct-store-identity"
            )))
          else
            None
        case null =>
          Some(Diagnostic(DiagnosticCode.MissingPersistencePolicy, Map("kind" -> "delivery")))
      }

  private def _record_diagnostic(record: InstanceRecord): Option[Diagnostic] =
    if (record == null)
      Some(Diagnostic(DiagnosticCode.InvalidInstanceIdentity, Map("kind" -> "record")))
    else if (record.identity == null || _blank(record.identity.value))
      Some(Diagnostic(DiagnosticCode.InvalidInstanceIdentity, Map("kind" -> "instance")))
    else
      _binding_diagnostic(record.definition)
        .orElse(_references_diagnostic(
          record.correlation,
          record.causation,
          record.derivedCompositeOccurrence,
          record.committedPredecessor
        ))
        .orElse(_lifecycle_diagnostic(
          record.lifecycle,
          record.currentProgression,
          record.suspension,
          record.revision
        ))
        .orElse {
          if (record.history.isEmpty)
            _initial_record_diagnostic(record)
          else
            _history_diagnostic(record)
        }

  private def _initial_record_diagnostic(record: InstanceRecord): Option[Diagnostic] =
    if (record.revision != initialRevision)
      Some(Diagnostic(DiagnosticCode.InvalidInitialRevision, Map(
        "actual" -> _revision_value(record.revision),
        "expected" -> initialRevision.value.toString,
        "instance" -> _identity_value(record.identity)
      )))
    else if (record.lifecycle != Lifecycle.NotStarted || record.currentProgression.nonEmpty ||
      record.suspension.nonEmpty)
      Some(Diagnostic(DiagnosticCode.IllegalLifecycleProgression, Map(
        "instance" -> _identity_value(record.identity),
        "reason" -> "initial-record-must-be-not-started"
      )))
    else
      None

  private def _history_diagnostic(record: InstanceRecord): Option[Diagnostic] = {
    val entries = record.history
    val initial = entries.headOption
    val entrydiagnostic = entries.zipWithIndex.iterator.map { case (entry, index) =>
      _entry_diagnostic(record.identity, entry, index, entries.lift(index - 1))
    }.collectFirst { case Some(diagnostic) => diagnostic }
    entrydiagnostic
      .orElse {
        initial match {
          case Some(entry) if entry.sequence != HistorySequence(1L) || entry.revision != InstanceRevision(1L) =>
            Some(Diagnostic(DiagnosticCode.NonAppendOnlyHistory, Map(
              "instance" -> _identity_value(record.identity),
              "reason" -> "history-must-begin-at-one"
            )))
          case _ => None
        }
      }
      .orElse {
        entries.lastOption.flatMap { entry =>
          if (record.revision != entry.revision ||
            record.lifecycle != entry.lifecycle ||
            record.currentProgression != entry.currentProgression ||
            record.correlation != entry.correlation ||
            record.causation != entry.causation ||
            record.derivedCompositeOccurrence != entry.derivedCompositeOccurrence ||
            record.committedPredecessor != entry.committedPredecessor ||
            record.suspension != entry.suspension)
            Some(Diagnostic(DiagnosticCode.NonAppendOnlyHistory, Map(
              "instance" -> _identity_value(record.identity),
              "reason" -> "current-facts-must-match-final-history-entry"
            )))
          else
            None
        }
      }
  }

  private def _entry_diagnostic(
    identity: InstanceIdentity,
    entry: HistoryEntry,
    index: Int,
    previous: Option[HistoryEntry]
  ): Option[Diagnostic] =
    if (entry == null)
      Some(Diagnostic(DiagnosticCode.NonAppendOnlyHistory, Map(
        "instance" -> _identity_value(identity),
        "reason" -> "missing-history-entry"
      )))
    else {
      val expectedsequence = HistorySequence(index.toLong + 1L)
      val expectedrevision = InstanceRevision(index.toLong + 1L)
      if (entry.sequence != expectedsequence)
        Some(Diagnostic(DiagnosticCode.InvalidNextSequence, Map(
          "actual" -> entry.sequence.value.toString,
          "expected" -> expectedsequence.value.toString,
          "instance" -> _identity_value(identity)
        )))
      else if (entry.revision != expectedrevision)
        Some(Diagnostic(DiagnosticCode.InvalidNextRevision, Map(
          "actual" -> entry.revision.value.toString,
          "expected" -> expectedrevision.value.toString,
          "instance" -> _identity_value(identity)
        )))
      else
        _references_diagnostic(
          entry.correlation,
          entry.causation,
          entry.derivedCompositeOccurrence,
          entry.committedPredecessor
        ).orElse(_lifecycle_diagnostic(
          entry.lifecycle,
          entry.currentProgression,
          entry.suspension,
          entry.revision
        )).orElse(_transition_diagnostic(
          previous.map(_.lifecycle).getOrElse(Lifecycle.NotStarted),
          entry.lifecycle,
          identity
        ))
    }

  private def _references_diagnostic(
    correlation: CorrelationReference,
    causation: CausationReference,
    derived: Option[DerivedCompositeOccurrenceReference],
    predecessor: Option[CommittedEntityTransitionReference]
  ): Option[Diagnostic] =
    if (_blank(correlation) || _blank(causation) || derived.exists(_blank) || predecessor.exists(_blank))
      Some(Diagnostic(DiagnosticCode.MissingCorrelationOrCausation, Map(
        "kind" -> "correlation/causation/provenance"
      )))
    else
      None

  private def _lifecycle_diagnostic(
    lifecycle: Lifecycle,
    progression: Option[ProgressionReference],
    suspension: Option[SuspensionBoundary],
    revision: InstanceRevision
  ): Option[Diagnostic] =
    if (revision == null || revision.value < 0L)
      Some(Diagnostic(DiagnosticCode.InvalidInitialRevision, Map("kind" -> "revision")))
    else if (lifecycle == null)
      Some(Diagnostic(DiagnosticCode.IllegalLifecycleProgression, Map("reason" -> "missing-lifecycle")))
    else if (progression.exists(reference => reference == null || _blank(reference.value)))
      Some(Diagnostic(DiagnosticCode.IllegalLifecycleProgression, Map("reason" -> "empty-progression")))
    else
      lifecycle match {
        case Lifecycle.NotStarted if progression.nonEmpty || suspension.nonEmpty =>
          Some(Diagnostic(DiagnosticCode.IllegalLifecycleProgression, Map(
            "reason" -> "not-started-cannot-progress-or-suspend"
          )))
        case Lifecycle.Active if progression.isEmpty =>
          Some(Diagnostic(DiagnosticCode.IllegalLifecycleProgression, Map(
            "reason" -> "active-requires-progression"
          )))
        case Lifecycle.Completed if progression.nonEmpty || suspension.nonEmpty =>
          Some(Diagnostic(DiagnosticCode.IllegalLifecycleProgression, Map(
            "reason" -> "completed-cannot-progress-or-suspend"
          )))
        case Lifecycle.Active =>
          suspension.flatMap(_suspension_diagnostic(_, Some(revision)))
        case _ =>
          None
      }

  private def _transition_diagnostic(
    previous: Lifecycle,
    next: Lifecycle,
    identity: InstanceIdentity
  ): Option[Diagnostic] =
    (previous, next) match {
      case (Lifecycle.NotStarted, Lifecycle.Active) |
          (Lifecycle.Active, Lifecycle.Active) |
          (Lifecycle.Active, Lifecycle.Completed) =>
        None
      case _ =>
        Some(Diagnostic(DiagnosticCode.IllegalLifecycleProgression, Map(
          "from" -> Option(previous).map(_.value).getOrElse(""),
          "instance" -> _identity_value(identity),
          "to" -> Option(next).map(_.value).getOrElse("")
        )))
    }

  private def _failure_c[A](diagnostic: Diagnostic): Consequence[A] =
    Consequence.Failure(Conclusion.from(new WorkflowInstancePersistenceException(diagnostic)))

  private def _blank(value: String): Boolean =
    Option(value).forall(_.trim.isEmpty)

  private def _blank(value: WorkflowDefinitionIdentity): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: WorkflowDefinitionRevision): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: ContinuationIdentity): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: ContextSnapshotReference): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: CompletionReference): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: EvidenceReference): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: CorrelationReference): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: CausationReference): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: DerivedCompositeOccurrenceReference): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: CommittedEntityTransitionReference): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: WorkflowStoreIdentity): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: MigrationPolicy): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: RetentionPolicy): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: LeasePolicy): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: IdempotencyKey): Boolean =
    value == null || _blank(value.value)

  private def _blank(value: RecoverabilityReference): Boolean =
    value == null || _blank(value.value)

  private def _value(value: ProducerAbiIdentity): String =
    Option(value).map(_.value).getOrElse("")

  private def _value(value: WorkflowAbiIdentity): String =
    Option(value).map(_.value).getOrElse("")

  private def _value(value: BootstrapAbiIdentity): String =
    Option(value).map(_.value).getOrElse("")

  private def _value(value: ProducerRevision): String =
    Option(value).map(_.value).getOrElse("")

  private def _value(value: FixtureSha256): String =
    Option(value).map(_.value).getOrElse("")

  private def _value(value: WorkflowStoreIdentity): String =
    Option(value).map(_.value).getOrElse("")

  private def _definition_identity_value(identity: WorkflowDefinitionIdentity): String =
    Option(identity).map(_.value).getOrElse("")

  private def _identity_value(identity: InstanceIdentity): String =
    Option(identity).map(_.value).getOrElse("")

  private def _revision_value(revision: InstanceRevision): String =
    Option(revision).map(_.value.toString).getOrElse("")
}
