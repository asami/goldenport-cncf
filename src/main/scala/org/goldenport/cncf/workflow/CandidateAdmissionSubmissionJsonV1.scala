package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.CandidateAdmissionEvidencePersistence.{JsonV1, Record}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 */
/** Pure result codec composition. Stored evidence is not an admission judgment. */
object CandidateAdmissionSubmissionJsonV1 {
  val schemaVersion: String = "cncf.candidate-admission-submission.v1"

  def payloadCodecC[C, E, S](
    resultTypeIdentity: String,
    candidateCodec: PayloadCodec[C],
    evidenceCodec: PayloadCodec[E],
    scopeCodec: PayloadCodec[S]
  ): Consequence[PayloadCodec[AdmissionSubmission[C, E, S]]] = {
    _safely_c {
      if (!_name(resultTypeIdentity))
        Consequence.configurationInvalid("Candidate-Admission result type is missing")
      else _bindings_c(candidateCodec, evidenceCodec, scopeCodec).map { _ =>
        new SubmissionCodec(resultTypeIdentity, candidateCodec, evidenceCodec, scopeCodec)
      }
    }
  }

  def encodeC[C, E, S](
    submission: AdmissionSubmission[C, E, S],
    candidateCodec: PayloadCodec[C],
    evidenceCodec: PayloadCodec[E],
    scopeCodec: PayloadCodec[S]
  ): Consequence[String] = {
    _safely_c {
      for {
        encoded <- _encode_c(submission, candidateCodec, evidenceCodec, scopeCodec)
        decoded <- decodeC(encoded, candidateCodec, evidenceCodec, scopeCodec)
        repeated <- _encode_c(decoded, candidateCodec, evidenceCodec, scopeCodec)
        _ <- if (decoded == submission && repeated == encoded) Consequence.unit
          else Consequence.stateConflict("Candidate-Admission codecs are not an exact round-trip")
      } yield encoded
    }
  }

  def decodeC[C, E, S](
    raw: String,
    candidateCodec: PayloadCodec[C],
    evidenceCodec: PayloadCodec[E],
    scopeCodec: PayloadCodec[S]
  ): Consequence[AdmissionSubmission[C, E, S]] = {
    _safely_c {
      _bindings_c(candidateCodec, evidenceCodec, scopeCodec).flatMap { _ =>
        val decoded = for {
          json <- parse(Option(raw).getOrElse("")).left.map(_ => "Invalid Candidate-Admission JSON")
          root <- _fields(json, Set("schemaVersion", "submissionIdentity", "candidate", "evidence"))
          version <- _string(root, "schemaVersion")
          _ <- Either.cond(version == schemaVersion, (), "Unsupported Candidate-Admission schema")
          identity <- _string(root, "submissionIdentity")
          candidate <- _fields(root("candidate"), Set("reference", "payload"))
          reference <- _fields(candidate("reference"), Set("identity", "revision"))
          target <- _string(reference, "identity")
          revision <- _string(reference, "revision")
          payload <- _fields(candidate("payload"), Set("typeIdentity", "value"))
          typename <- _string(payload, "typeIdentity")
          _ <- Either.cond(typename == candidateCodec.typeIdentity, (), "Incompatible candidate type")
          value <- candidateCodec.decode(payload("value"))
          array <- root("evidence").asArray.toRight("Candidate-Admission evidence must be an array")
          records <- array.foldLeft[Either[String, Vector[AdmissionEvidence[E, S]]]](Right(Vector.empty)) {
            (acc, item) => for {
              collected <- acc
              record <- JsonV1.decodeC(item.noSpaces, evidenceCodec, scopeCodec).toOption
                .toRight("Invalid Candidate-Admission evidence record")
              _ <- Either.cond(record.submissionIdentity == identity, (), "Nested submission identity differs")
            } yield collected :+ record.evidence
          }
        } yield AdmissionSubmission(identity,
          CandidateSnapshot(CandidateRef(target, revision), TypedValue(typename, value)), records)
        decoded.fold(Consequence.stateConflict(_), _.validateC)
      }
    }
  }

  private def _encode_c[C, E, S](
    submission: AdmissionSubmission[C, E, S],
    candidatecodec: PayloadCodec[C],
    evidencecodec: PayloadCodec[E],
    scopecodec: PayloadCodec[S]
  ): Consequence[String] = {
    _bindings_c(candidatecodec, evidencecodec, scopecodec).flatMap { _ =>
      if (submission == null) Consequence.stateConflict("Candidate-Admission submission is missing")
      else submission.validateC.flatMap { valid =>
        if (valid.candidate.payload.typeIdentity != candidatecodec.typeIdentity)
          Consequence.stateConflict("Candidate-Admission candidate codec type differs")
        else valid.evidence.foldLeft[Consequence[Vector[Json]]](Consequence.success(Vector.empty)) {
          (acc, evidence) => for {
            collected <- acc
            encoded <- JsonV1.encodeC(Record(valid.identity, evidence), evidencecodec, scopecodec)
            json <- parse(encoded).fold(_ => Consequence.stateConflict("Invalid encoded admission evidence"),
              Consequence.success(_))
          } yield collected :+ json
        }.map { evidence =>
          val candidate = valid.candidate
          Json.obj(
            "schemaVersion" -> Json.fromString(schemaVersion),
            "submissionIdentity" -> Json.fromString(valid.identity),
            "candidate" -> Json.obj(
              "reference" -> Json.obj(
                "identity" -> Json.fromString(candidate.reference.identity),
                "revision" -> Json.fromString(candidate.reference.revision)),
              "payload" -> Json.obj(
                "typeIdentity" -> Json.fromString(candidate.payload.typeIdentity),
                "value" -> candidatecodec.encode(candidate.payload.value))),
            "evidence" -> Json.arr(evidence*)
          ).noSpaces
        }
      }
    }
  }

  private final class SubmissionCodec[C, E, S](
    val typeIdentity: String,
    candidatecodec: PayloadCodec[C],
    evidencecodec: PayloadCodec[E],
    scopecodec: PayloadCodec[S]
  ) extends PayloadCodec[AdmissionSubmission[C, E, S]] {
    /** Required PayloadCodec override: invalid encode is contained by the Workflow boundary. */
    def encode(value: AdmissionSubmission[C, E, S]): Json = {
      encodeC(value, candidatecodec, evidencecodec, scopecodec) match {
        case Consequence.Success(bytes) => parse(bytes).fold(
          _ => throw new IllegalArgumentException("Invalid encoded Candidate-Admission submission"), identity)
        case _ => throw new IllegalArgumentException("Candidate-Admission submission cannot be encoded exactly")
      }
    }
    def decode(value: Json): Either[String, AdmissionSubmission[C, E, S]] = {
      decodeC(Option(value).map(_.noSpaces).orNull, candidatecodec, evidencecodec, scopecodec).toOption
        .toRight("Candidate-Admission submission cannot be decoded")
    }
  }

  private def _bindings_c[C, E, S](
    candidate: PayloadCodec[C], evidence: PayloadCodec[E], scope: PayloadCodec[S]
  ): Consequence[Unit] = {
    if (candidate == null || evidence == null || scope == null ||
        !_name(candidate.typeIdentity) || !_name(evidence.typeIdentity) || !_name(scope.typeIdentity))
      Consequence.configurationInvalid("Candidate-Admission payload/scope codec binding is incomplete")
    else Consequence.unit
  }

  private def _fields(json: Json, expected: Set[String]): Either[String, Map[String, Json]] = {
    json.asObject.map(_.toMap).filter(_.keySet == expected).toRight("Candidate-Admission JSON fields differ")
  }

  private def _string(fields: Map[String, Json], key: String): Either[String, String] = {
    fields.get(key).flatMap(_.asString).filter(_name).toRight(s"Missing Candidate-Admission field: $key")
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _safely_c[A](operation: => Consequence[A]): Consequence[A] = {
    try operation catch {
      case NonFatal(_) => Consequence.stateConflict("Candidate-Admission application codec failed")
    }
  }
}
