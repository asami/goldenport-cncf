package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.{_expect, _required_fields, _optional, _optional_string, _string}

/** Versioned persistence bytes, not an executable definition or a public Operation.
  * Reopening selects a separately admitted declared definition reference.
  * Legacy fixture digests carry no reopening authority.
  * @since   Sep. 29, 2026
  * @version Oct. 12, 2026
  */
object WorkflowInstanceRecordJsonV1 {
  val schemaVersion = "cncf.workflow-instance-record.v1"

  final class Catalog private[WorkflowInstanceRecordJsonV1] (
    private val _definitions: Vector[DefinitionBinding]
  ) {
    private[WorkflowInstanceRecordJsonV1] def _resolve(json: Json): Either[String, DefinitionBinding] = {
       for {
      fields <- Option(json).flatMap(_.asObject).toRight("persisted Workflow definition must be an object")
      identity <- fields("identity").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("persisted Workflow definition identity is missing")
      revision <- fields("revision").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("persisted Workflow definition revision is missing")
      // Legacy source, ABI, producer and digest fields are ignored. They are
      // not execution authority and do not prove unchanged content.
      binding <- _resolve_reference(identity, revision)
    } yield binding
    }

    private[workflow] def resolveC(binding: DefinitionBinding): Consequence[DefinitionBinding] =
      _resolve_reference(binding.workflowIdentity.value, binding.workflowRevision.value)
        .fold(Consequence.stateConflict(_), Consequence.success)

    private def _resolve_reference(identity: String, revision: String): Either[String, DefinitionBinding] = {
      val matches = _definitions.filter(binding => binding.workflowIdentity.value == identity &&
        binding.workflowRevision.value == revision)
      if (matches.size == 1) Right(matches.head)
      else Left("persisted Workflow definition reference is absent or ambiguous")
    }
  }

  def catalogC(definitions: Vector[DefinitionBinding]): Consequence[Catalog] = {
    if (definitions == null || definitions.isEmpty || definitions.exists(_ == null))
      Consequence.configurationInvalid("Workflow definition catalog is missing")
    else Consequence.success(new Catalog(definitions.distinct))
  }

  def encodeC(record: InstanceRecord): Consequence[String] = {
    if (record == null) Consequence.stateConflict("Workflow instance record is missing")
    else record.validateC.map(_encode_unchecked)

  }
  /** Precondition: record was consumed or constructed by an admitted append in
    * this call chain. Formatting does not certify prior history again.
    */
  private[workflow] def _encode_unchecked(valid: InstanceRecord): String = {
      val current = HistoryEntry(HistorySequence(valid.history.size.toLong),
        valid.revision, valid.lifecycle, valid.currentProgression, valid.correlation,
        valid.causation, valid.derivedCompositeOccurrence, valid.committedPredecessor,
        valid.suspension)
      Json.obj(
        "schemaVersion" -> Json.fromString(schemaVersion),
        "identity" -> Json.fromString(valid.identity.value),
        "definition" -> _definition_json(valid.definition),
        "current" -> _entry_json(current),
        "history" -> Json.arr(valid.history.map(_entry_json)*)
      ).noSpaces
    }

  def decodeC(raw: String, catalog: Catalog): Consequence[InstanceRecord] = {
    if (raw == null || catalog == null)
      Consequence.stateConflict("Workflow record bytes or admitted catalog are missing")
    else {
      val decoded = for {
        json <- parse(raw).left.map(_ => "invalid Workflow instance JSON")
        root <- _required_fields(json, Set("schemaVersion", "identity", "definition", "current", "history"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == schemaVersion, "unsupported Workflow instance schema")
        identity <- _string(root, "identity")
        definition <- catalog._resolve(root("definition"))
        current <- _decode_entry(root("current"))
        values <- root("history").asArray.toRight("Workflow history must be an array")
        history <- values.foldLeft[Either[String, Vector[HistoryEntry]]](Right(Vector.empty)) {
          (prior, value) => for { entries <- prior; entry <- _decode_entry(value) } yield entries :+ entry
        }
        _ <- _expect(current.sequence.value == history.size.toLong, "Workflow current history size differs")
      } yield InstanceRecord(InstanceIdentity(identity), definition, current.revision,
        current.lifecycle, current.currentProgression, current.correlation, current.causation,
        current.derivedCompositeOccurrence, current.committedPredecessor, current.suspension, history)
      decoded.fold(Consequence.stateConflict(_), _.validateC)
    }
  }

  private def _definition_json(binding: DefinitionBinding): Json = Json.obj(
    "identity" -> Json.fromString(binding.workflowIdentity.value),
    "revision" -> Json.fromString(binding.workflowRevision.value)
  )

  private def _suspension_json(value: SuspensionBoundary): Json = Json.obj(
    "identity" -> Json.fromString(value.continuationIdentity.value),
    "expectedRevision" -> Json.fromLong(value.expectedRevision.value),
    "context" -> Json.fromString(value.contextSnapshot.value),
    "completion" -> Json.fromString(value.completion.value),
    "evidence" -> Json.fromString(value.evidence.value),
    "resumeCorrelation" -> Json.fromString(value.resumeCorrelation.value)
  )

  private def _entry_json(entry: HistoryEntry): Json = Json.obj(
    "sequence" -> Json.fromLong(entry.sequence.value),
    "revision" -> Json.fromLong(entry.revision.value),
    "lifecycle" -> Json.fromString(entry.lifecycle.value),
    "progression" -> _optional(entry.currentProgression.map(_.value)),
    "correlation" -> Json.fromString(entry.correlation.value),
    "causation" -> Json.fromString(entry.causation.value),
    "composite" -> _optional(entry.derivedCompositeOccurrence.map(_.value)),
    "predecessor" -> _optional(entry.committedPredecessor.map(_.value)),
    "suspension" -> entry.suspension.fold(Json.Null)(_suspension_json)
  )

  private def _decode_entry(json: Json): Either[String, HistoryEntry] = {
     for {
    fields <- _required_fields(json, Set("sequence", "revision", "lifecycle", "progression",
      "correlation", "causation", "composite", "predecessor", "suspension"))
    sequence <- _integral(fields, "sequence")
    revision <- _integral(fields, "revision")
    state <- _string(fields, "lifecycle")
    lifecycle <- Lifecycle.values.find(_.value == state).toRight("unknown Workflow lifecycle")
    progression <- _optional_string(fields, "progression")
    correlation <- _string(fields, "correlation")
    causation <- _string(fields, "causation")
    composite <- _optional_string(fields, "composite")
    predecessor <- _optional_string(fields, "predecessor")
    suspension <- if (fields("suspension").isNull) Right(None)
      else _decode_suspension(fields("suspension")).map(Some(_))
  } yield HistoryEntry(HistorySequence(sequence), InstanceRevision(revision), lifecycle,
    progression.map(ProgressionReference.apply), CorrelationReference(correlation),
    CausationReference(causation), composite.map(DerivedCompositeOccurrenceReference.apply),
    predecessor.map(CommittedEntityTransitionReference.apply), suspension)
  }

  private def _decode_suspension(json: Json): Either[String, SuspensionBoundary] = {
     for {
    fields <- _required_fields(json, Set("identity", "expectedRevision", "context", "completion", "evidence", "resumeCorrelation"))
    identity <- _string(fields, "identity")
    revision <- _integral(fields, "expectedRevision")
    context <- _string(fields, "context")
    completion <- _string(fields, "completion")
    evidence <- _string(fields, "evidence")
    resume <- _string(fields, "resumeCorrelation")
  } yield SuspensionBoundary(ContinuationIdentity(identity), InstanceRevision(revision),
    ContextSnapshotReference(context), CompletionReference(completion), EvidenceReference(evidence),
    CorrelationReference(resume))
  }

  private def _integral(fields: Map[String, Json], key: String): Either[String, Long] =
    fields(key).asNumber.flatMap(_.toLong).filter(_ >= 0L)
      .toRight(s"Workflow field $key must be a nonnegative Long")
}
