package org.goldenport.cncf.workflow

import java.nio.charset.StandardCharsets
import java.nio.file.{FileAlreadyExistsException, Files, LinkOption, Path}
import java.security.MessageDigest
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.{AdmissionEvidence, CandidateRef}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Application-owned evidence is stored separately from Phase 77 Continuation.
  * A completed semantic result does not become admission authority on write.
  * @since Sep. 25, 2026
  * @version Sep. 25, 2026
  */
trait CandidateAdmissionEvidencePersistence[E, S] {
  def putIfAbsentC(submissionIdentity: String, evidence: AdmissionEvidence[E, S]): Consequence[Unit]
  def loadC(submissionIdentity: String): Consequence[Vector[AdmissionEvidence[E, S]]]
}

object CandidateAdmissionEvidencePersistence {
  final case class Record[E, S](submissionIdentity: String, evidence: AdmissionEvidence[E, S]) {
    def validateC: Consequence[Record[E, S]] =
      if (!_name(submissionIdentity) || evidence == null)
        Consequence.stateConflict("Admission evidence record is incomplete")
      else evidence.validateC.map(_ => this)
  }

  /** Exact-schema JSON, with application-owned codecs for both payload and scope. */
  object JsonV1 {
    val schemaVersion = "cncf.candidate-admission-evidence.v1"

    def encodeC[E, S](record: Record[E, S], evidenceCodec: PayloadCodec[E],
      scopeCodec: PayloadCodec[S]): Consequence[String] =
      _codecs_c(evidenceCodec, scopeCodec).flatMap { _ =>
        if (record == null) Consequence.stateConflict("Admission evidence record is missing")
        else record.validateC.flatMap { valid =>
          val evidence = valid.evidence
          if (evidence.payload.typeIdentity != evidenceCodec.typeIdentity ||
              evidence.scope.typeIdentity != scopeCodec.typeIdentity)
            Consequence.stateConflict("Admission evidence codecs do not match declared types")
          else try Consequence.success(Json.obj(
            "schemaVersion" -> Json.fromString(schemaVersion),
            "submissionIdentity" -> Json.fromString(valid.submissionIdentity),
            "identity" -> Json.fromString(evidence.identity),
            "requirementIdentity" -> Json.fromString(evidence.requirementIdentity),
            "target" -> Json.obj(
              "identity" -> Json.fromString(evidence.target.identity),
              "revision" -> Json.fromString(evidence.target.revision)
            ),
            "payload" -> Json.obj(
              "typeIdentity" -> Json.fromString(evidence.payload.typeIdentity),
              "value" -> evidenceCodec.encode(evidence.payload.value)
            ),
            "scope" -> Json.obj(
              "typeIdentity" -> Json.fromString(evidence.scope.typeIdentity),
              "value" -> scopeCodec.encode(evidence.scope.value)
            ),
            "provenance" -> Json.fromString(evidence.provenance)
          ).noSpaces)
          catch { case NonFatal(e) => Consequence.stateConflict(s"Admission evidence encode failed: ${e.getMessage}") }
        }
      }

    def decodeC[E, S](raw: String, evidenceCodec: PayloadCodec[E],
      scopeCodec: PayloadCodec[S]): Consequence[Record[E, S]] =
      _codecs_c(evidenceCodec, scopeCodec).flatMap { _ => try {
        val decoded = for {
          json <- parse(Option(raw).getOrElse("")).left.map(_ => "invalid JSON")
          root <- _fields(json, Set("schemaVersion", "submissionIdentity", "identity",
            "requirementIdentity", "target", "payload", "scope", "provenance"))
          version <- _string(root, "schemaVersion")
          _ <- _expect(version == schemaVersion, "unsupported admission evidence schema")
          submission <- _string(root, "submissionIdentity")
          identity <- _string(root, "identity")
          requirement <- _string(root, "requirementIdentity")
          target <- _fields(root("target"), Set("identity", "revision"))
          targetIdentity <- _string(target, "identity")
          revision <- _string(target, "revision")
          payload <- _fields(root("payload"), Set("typeIdentity", "value"))
          payloadType <- _string(payload, "typeIdentity")
          _ <- _expect(payloadType == evidenceCodec.typeIdentity, "incompatible admission evidence type")
          payloadValue <- evidenceCodec.decode(payload("value"))
          scope <- _fields(root("scope"), Set("typeIdentity", "value"))
          scopeType <- _string(scope, "typeIdentity")
          _ <- _expect(scopeType == scopeCodec.typeIdentity, "incompatible admission scope type")
          scopeValue <- scopeCodec.decode(scope("value"))
          provenance <- _string(root, "provenance")
        } yield Record(submission, AdmissionEvidence(identity, requirement,
          CandidateRef(targetIdentity, revision), TypedValue(payloadType, payloadValue),
          TypedValue(scopeType, scopeValue), provenance))
        decoded.fold(Consequence.stateConflict(_), _.validateC)
      } catch {
        case NonFatal(e) => Consequence.stateConflict(s"Admission evidence decode failed: ${e.getMessage}")
      }
      }

    private def _fields(json: Json, expected: Set[String]): Either[String, Map[String, Json]] =
      json.asObject.map(_.toMap).filter(_.keySet == expected)
        .toRight("admission evidence JSON fields differ from schema")

    private def _string(fields: Map[String, Json], key: String): Either[String, String] =
      fields.get(key).flatMap(_.asString).filter(_name)
        .toRight(s"admission evidence field is missing: $key")

    private def _expect(condition: Boolean, message: String): Either[String, Unit] =
      Either.cond(condition, (), message)
  }

  /** Immutable per-evidence files, published atomically in a caller-owned directory.
    * A load may overlap a new write; this is not a shared Continuation transaction.
    */
  final class LocalJson[E, S](root: Path, evidenceCodec: PayloadCodec[E],
    scopeCodec: PayloadCodec[S]) extends CandidateAdmissionEvidencePersistence[E, S] {
    def putIfAbsentC(submissionIdentity: String, evidence: AdmissionEvidence[E, S]): Consequence[Unit] =
      JsonV1.encodeC(Record(submissionIdentity, evidence), evidenceCodec, scopeCodec).flatMap { encoded =>
        _directory_c.flatMap { directory =>
          val destination = _path(directory, submissionIdentity, evidence.identity)
          var temporary: Path = null
          try {
            temporary = Files.createTempFile(directory, ".cam-evidence-", ".tmp")
            Files.writeString(temporary, encoded, StandardCharsets.UTF_8)
            try {
              Files.createLink(destination, temporary)
              Consequence.unit
            } catch {
              case _: FileAlreadyExistsException =>
                _read_c(destination).flatMap { existing =>
                  if (existing == Record(submissionIdentity, evidence)) Consequence.unit
                  else Consequence.stateConflict("A different admission evidence record already exists")
                }
            }
          } catch {
            case NonFatal(e) => Consequence.stateConflict(s"Admission evidence write failed: ${e.getMessage}")
          } finally {
            if (temporary != null) try Files.deleteIfExists(temporary) catch {
              case NonFatal(_) => ()
            }
          }
        }
      }

    def loadC(submissionIdentity: String): Consequence[Vector[AdmissionEvidence[E, S]]] =
      if (!_name(submissionIdentity)) Consequence.stateConflict("Admission submission identity is missing")
      else _directory_c.flatMap { directory =>
        try {
          val stream = Files.list(directory)
          val paths = try stream.iterator().asScala.toVector.filter(_.getFileName.toString.endsWith(".cam-evidence.json"))
            finally stream.close()
          paths.foldLeft[Consequence[Vector[AdmissionEvidence[E, S]]]](Consequence.success(Vector.empty)) {
            (acc, path) => acc.flatMap { collected => _read_c(path).flatMap { record =>
              if (path != _path(directory, record.submissionIdentity, record.evidence.identity))
                Consequence.stateConflict("Admission evidence filename differs from its identity")
              else if (record.submissionIdentity == submissionIdentity)
                Consequence.success(collected :+ record.evidence)
              else Consequence.success(collected)
            }}
          }.map(_.sortBy(_.identity))
        } catch {
          case NonFatal(e) => Consequence.stateConflict(s"Admission evidence load failed: ${e.getMessage}")
        }
      }

    private def _directory_c: Consequence[Path] =
      if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
        Consequence.stateConflict("Admission evidence directory is unavailable")
      else Consequence.success(root)

    private def _read_c(path: Path): Consequence[Record[E, S]] =
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        Consequence.stateConflict("Admission evidence entry is not a regular file")
      else try JsonV1.decodeC(Files.readString(path, StandardCharsets.UTF_8), evidenceCodec, scopeCodec)
      catch { case NonFatal(e) => Consequence.stateConflict(s"Admission evidence read failed: ${e.getMessage}") }
  }

  final class InMemory[E, S] extends CandidateAdmissionEvidencePersistence[E, S] {
    private val _records = mutable.Map.empty[(String, String), AdmissionEvidence[E, S]]

    def putIfAbsentC(submissionIdentity: String, evidence: AdmissionEvidence[E, S]): Consequence[Unit] =
      Record(submissionIdentity, evidence).validateC.flatMap { _ => synchronized {
        val key = submissionIdentity -> evidence.identity
        _records.get(key) match {
          case Some(existing) if existing == evidence => Consequence.unit
          case Some(_) => Consequence.stateConflict("A different admission evidence record already exists")
          case None => _records.update(key, evidence); Consequence.unit
        }
      }}

    def loadC(submissionIdentity: String): Consequence[Vector[AdmissionEvidence[E, S]]] =
      if (!_name(submissionIdentity)) Consequence.stateConflict("Admission submission identity is missing")
      else synchronized { Consequence.success(_records.toVector.collect {
        case ((identity, _), evidence) if identity == submissionIdentity => evidence
      }.sortBy(_.identity)) }
  }

  private def _codecs_c[E, S](evidence: PayloadCodec[E], scope: PayloadCodec[S]): Consequence[Unit] =
    if (evidence == null || scope == null || !_name(evidence.typeIdentity) || !_name(scope.typeIdentity))
      Consequence.stateConflict("Admission evidence codecs are incomplete")
    else Consequence.unit

  private def _path(directory: Path, submission: String, evidence: String): Path = {
    val bytes = (submission + "\u0000" + evidence).getBytes(StandardCharsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
    directory.resolve(s"$digest.cam-evidence.json")
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
