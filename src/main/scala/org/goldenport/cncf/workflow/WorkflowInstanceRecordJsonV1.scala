package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.{_expect, _fields, _optional, _optional_string, _string}

/** Versioned persistence bytes, not an executable definition or a public Operation.
  * Reopening requires a separately admitted, exact definition catalog.
  * @since   Sep. 29, 2026
  * @version Oct.  8, 2026
  */
object WorkflowInstanceRecordJsonV1 {
  val schemaVersion = "cncf.workflow-instance-record.v1"

  final class Catalog private[WorkflowInstanceRecordJsonV1] (
    private val definitions: Vector[DefinitionBinding]
  ) {
    private[WorkflowInstanceRecordJsonV1] def resolve(json: Json): Either[String, DefinitionBinding] =
      definitions.find(binding => definitionJson(binding) == json)
        .toRight("persisted Workflow definition is absent from the admitted catalog")
  }

  def catalogC(definitions: Vector[DefinitionBinding]): Consequence[Catalog] =
    if (definitions == null || definitions.isEmpty || definitions.exists(_ == null))
      Consequence.configurationInvalid("Workflow definition catalog is missing")
    else definitions.foldLeft(Consequence.unit)((prior, binding) =>
      prior.flatMap(_ => binding.validateC.map(_ => ()))).flatMap { _ =>
      val keys = definitions.map(binding =>
        (binding.workflowIdentity, binding.workflowRevision, binding.fixtureSha256))
      if (keys.distinct.size != keys.size)
        Consequence.configurationInvalid("Workflow definition catalog contains duplicate bindings")
      else Consequence.success(new Catalog(definitions))
    }

  def encodeC(record: InstanceRecord): Consequence[String] =
    if (record == null) Consequence.stateConflict("Workflow instance record is missing")
    else record.validateC.map { valid =>
      val current = HistoryEntry(HistorySequence(valid.history.size.toLong),
        valid.revision, valid.lifecycle, valid.currentProgression, valid.correlation,
        valid.causation, valid.derivedCompositeOccurrence, valid.committedPredecessor,
        valid.suspension)
      Json.obj(
        "schemaVersion" -> Json.fromString(schemaVersion),
        "identity" -> Json.fromString(valid.identity.value),
        "definition" -> definitionJson(valid.definition),
        "current" -> entryJson(current),
        "history" -> Json.arr(valid.history.map(entryJson)*)
      ).noSpaces
    }

  def decodeC(raw: String, catalog: Catalog): Consequence[InstanceRecord] =
    if (raw == null || catalog == null)
      Consequence.stateConflict("Workflow record bytes or admitted catalog are missing")
    else {
      val decoded = for {
        json <- parse(raw).left.map(_ => "invalid Workflow instance JSON")
        root <- _fields(json, Set("schemaVersion", "identity", "definition", "current", "history"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == schemaVersion, "unsupported Workflow instance schema")
        identity <- _string(root, "identity")
        definition <- catalog.resolve(root("definition"))
        current <- decodeEntry(root("current"))
        values <- root("history").asArray.toRight("Workflow history must be an array")
        history <- values.foldLeft[Either[String, Vector[HistoryEntry]]](Right(Vector.empty)) {
          (prior, value) => for { entries <- prior; entry <- decodeEntry(value) } yield entries :+ entry
        }
        _ <- _expect(current.sequence.value == history.size.toLong, "Workflow current history size differs")
      } yield InstanceRecord(InstanceIdentity(identity), definition, current.revision,
        current.lifecycle, current.currentProgression, current.correlation, current.causation,
        current.derivedCompositeOccurrence, current.committedPredecessor, current.suspension, history)
      decoded.fold(Consequence.stateConflict(_), _.validateC)
    }

  private def definitionJson(binding: DefinitionBinding): Json = Json.obj(
    "identity" -> Json.fromString(binding.workflowIdentity.value),
    "revision" -> Json.fromString(binding.workflowRevision.value),
    "source" -> Json.obj("root" -> sourceJson(binding.sourceCorrelation.root),
      "definition" -> sourceJson(binding.sourceCorrelation.definition)),
    "producerAbi" -> Json.fromString(binding.producerAbiIdentity.value),
    "workflowAbi" -> Json.fromString(binding.workflowAbiIdentity.value),
    "bootstrapAbi" -> Json.fromString(binding.bootstrapAbiIdentity.value),
    "producerRevision" -> Json.fromString(binding.producerRevision.value),
    "fixtureSha256" -> Json.fromString(binding.fixtureSha256.value)
  )

  private def sourceJson(source: SourceLocation): Json = Json.obj(
    "resource" -> Json.fromString(source.resource), "line" -> Json.fromInt(source.line),
    "column" -> source.column.fold(Json.Null)(Json.fromInt)
  )

  private def suspensionJson(value: SuspensionBoundary): Json = Json.obj(
    "identity" -> Json.fromString(value.continuationIdentity.value),
    "expectedRevision" -> Json.fromLong(value.expectedRevision.value),
    "context" -> Json.fromString(value.contextSnapshot.value),
    "completion" -> Json.fromString(value.completion.value),
    "evidence" -> Json.fromString(value.evidence.value),
    "resumeCorrelation" -> Json.fromString(value.resumeCorrelation.value)
  )

  private def entryJson(entry: HistoryEntry): Json = Json.obj(
    "sequence" -> Json.fromLong(entry.sequence.value),
    "revision" -> Json.fromLong(entry.revision.value),
    "lifecycle" -> Json.fromString(entry.lifecycle.value),
    "progression" -> _optional(entry.currentProgression.map(_.value)),
    "correlation" -> Json.fromString(entry.correlation.value),
    "causation" -> Json.fromString(entry.causation.value),
    "composite" -> _optional(entry.derivedCompositeOccurrence.map(_.value)),
    "predecessor" -> _optional(entry.committedPredecessor.map(_.value)),
    "suspension" -> entry.suspension.fold(Json.Null)(suspensionJson)
  )

  private def decodeEntry(json: Json): Either[String, HistoryEntry] = for {
    fields <- _fields(json, Set("sequence", "revision", "lifecycle", "progression",
      "correlation", "causation", "composite", "predecessor", "suspension"))
    sequence <- integral(fields, "sequence")
    revision <- integral(fields, "revision")
    state <- _string(fields, "lifecycle")
    lifecycle <- Lifecycle.values.find(_.value == state).toRight("unknown Workflow lifecycle")
    progression <- _optional_string(fields, "progression")
    correlation <- _string(fields, "correlation")
    causation <- _string(fields, "causation")
    composite <- _optional_string(fields, "composite")
    predecessor <- _optional_string(fields, "predecessor")
    suspension <- if (fields("suspension").isNull) Right(None)
      else decodeSuspension(fields("suspension")).map(Some(_))
  } yield HistoryEntry(HistorySequence(sequence), InstanceRevision(revision), lifecycle,
    progression.map(ProgressionReference.apply), CorrelationReference(correlation),
    CausationReference(causation), composite.map(DerivedCompositeOccurrenceReference.apply),
    predecessor.map(CommittedEntityTransitionReference.apply), suspension)

  private def decodeSuspension(json: Json): Either[String, SuspensionBoundary] = for {
    fields <- _fields(json, Set("identity", "expectedRevision", "context", "completion", "evidence", "resumeCorrelation"))
    identity <- _string(fields, "identity")
    revision <- integral(fields, "expectedRevision")
    context <- _string(fields, "context")
    completion <- _string(fields, "completion")
    evidence <- _string(fields, "evidence")
    resume <- _string(fields, "resumeCorrelation")
  } yield SuspensionBoundary(ContinuationIdentity(identity), InstanceRevision(revision),
    ContextSnapshotReference(context), CompletionReference(completion), EvidenceReference(evidence),
    CorrelationReference(resume))

  private def integral(fields: Map[String, Json], key: String): Either[String, Long] =
    fields(key).asNumber.flatMap(_.toLong).filter(_ >= 0L)
      .toRight(s"Workflow field $key must be a nonnegative Long")
}
