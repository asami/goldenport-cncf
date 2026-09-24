package org.goldenport.cncf.workflow

import java.nio.charset.StandardCharsets
import java.nio.file.{FileAlreadyExistsException, Files, LinkOption, Path}
import java.security.MessageDigest
import scala.collection.mutable
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{WorkflowContinuation, WorkflowInteraction}
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Separate, non-atomic issued-WorkOrder store. A durable adapter owns its encoding and storage. */
trait IssuedWorkOrderPersistence[W] {
  /** An identical retry is allowed; a different issue for the same Continuation is rejected. */
  def putIfAbsentC(issued: WorkflowInteraction[W, Nothing]): Consequence[WorkflowInteraction[W, Nothing]]
  def loadC(identity: ContinuationIdentity): Consequence[Option[WorkflowInteraction[W, Nothing]]]
}

object IssuedWorkOrderPersistence {
  /** A process-restart-capable JSON store in a caller-owned, pre-existing local directory.
    * Publication uses a hard link so the final name never exposes a partial JSON write.
    * It is not a shared transaction with Continuation or WorkflowInstance persistence.
    */
  final class LocalJson[W](root: Path, codec: PayloadCodec[W]) extends IssuedWorkOrderPersistence[W] {
    def putIfAbsentC(issued: WorkflowInteraction[W, Nothing]): Consequence[WorkflowInteraction[W, Nothing]] =
      for {
        identity <- _identity_c(issued)
        directory <- _directory_c
        encoded <- WorkflowWorkOrderJsonV1.encodeC(issued, codec)
        stored <- _publish_c(directory, identity, encoded, issued)
      } yield stored

    def loadC(identity: ContinuationIdentity): Consequence[Option[WorkflowInteraction[W, Nothing]]] =
      if (identity == null || Option(identity.value).forall(_.trim.isEmpty))
        Consequence.stateConflict("issued WorkOrder requires a Continuation identity")
      else _directory_c.flatMap(directory => _load_c(directory, identity))

    private def _directory_c: Consequence[Path] =
      if (root == null || codec == null ||
          !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
        Consequence.stateConflict("issued WorkOrder JSON directory or codec is unavailable")
      else Consequence.success(root)

    private def _publish_c(
      directory: Path,
      identity: ContinuationIdentity,
      encoded: String,
      issued: WorkflowInteraction[W, Nothing]
    ): Consequence[WorkflowInteraction[W, Nothing]] = {
      val destination = _path(directory, identity)
      var temporary: Path = null
      try {
        temporary = Files.createTempFile(directory, ".issued-workorder-", ".tmp")
        Files.writeString(temporary, encoded, StandardCharsets.UTF_8)
        try {
          Files.createLink(destination, temporary)
          Consequence.success(issued)
        } catch {
          case _: FileAlreadyExistsException =>
            _load_c(directory, identity).flatMap {
              case Some(existing) if existing == issued => Consequence.success(existing)
              case _ => Consequence.stateConflict(s"a different WorkOrder is already issued: ${identity.value}")
            }
        }
      } catch {
        case NonFatal(e) => Consequence.stateConflict(s"issued WorkOrder JSON write failed: ${e.getMessage}")
      } finally {
        if (temporary != null) try Files.deleteIfExists(temporary) catch {
          case NonFatal(_) => ()
        }
      }
    }

    private def _load_c(
      directory: Path,
      identity: ContinuationIdentity
    ): Consequence[Option[WorkflowInteraction[W, Nothing]]] = {
      val destination = _path(directory, identity)
      try {
        if (Files.notExists(destination, LinkOption.NOFOLLOW_LINKS))
          Consequence.success(None)
        else if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))
          Consequence.stateConflict("issued WorkOrder JSON entry is not a regular file")
        else WorkflowWorkOrderJsonV1.decodeC(Files.readString(destination, StandardCharsets.UTF_8), codec).flatMap {
          decoded => _identity_c(decoded).flatMap { actual =>
            if (actual == identity) Consequence.success(Some(decoded))
            else Consequence.stateConflict("issued WorkOrder JSON identity differs from its key")
          }
        }
      } catch {
        case NonFatal(e) => Consequence.stateConflict(s"issued WorkOrder JSON read failed: ${e.getMessage}")
      }
    }

    private def _path(directory: Path, identity: ContinuationIdentity): Path = {
      val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.value.getBytes(StandardCharsets.UTF_8))
        .map(byte => f"${byte & 0xff}%02x").mkString
      directory.resolve(s"$digest.workorder.json")
    }
  }

  /** Deterministic shared store for focused runtime fixtures, not process-durable storage. */
  final class InMemory[W] extends IssuedWorkOrderPersistence[W] {
    private val _issued = mutable.Map.empty[ContinuationIdentity, WorkflowInteraction[W, Nothing]]

    def putIfAbsentC(issued: WorkflowInteraction[W, Nothing]): Consequence[WorkflowInteraction[W, Nothing]] =
      _identity_c(issued).flatMap { identity => synchronized {
        _issued.get(identity) match {
          case Some(existing) if existing == issued => Consequence.success(existing)
          case Some(_) => Consequence.stateConflict(s"a different WorkOrder is already issued: ${identity.value}")
          case None =>
            _issued.update(identity, issued)
            Consequence.success(issued)
        }
      }}

    def loadC(identity: ContinuationIdentity): Consequence[Option[WorkflowInteraction[W, Nothing]]] =
      if (identity == null || Option(identity.value).forall(_.trim.isEmpty))
        Consequence.stateConflict("issued WorkOrder requires a Continuation identity")
      else synchronized { Consequence.success(_issued.get(identity)) }
  }

  private def _identity_c[W](issued: WorkflowInteraction[W, Nothing]): Consequence[ContinuationIdentity] =
    if (issued == null || issued.handle == null)
      Consequence.stateConflict("issued WorkOrder is incomplete")
    else issued.current match {
      case work: WorkflowContinuation.WorkOrder[?] if work.request != null &&
          work.request.continuationId != null &&
          Option(work.request.continuationId.value).exists(_.trim.nonEmpty) =>
        issued.handle.validateC.map(_ => work.request.continuationId)
      case _ => Consequence.stateConflict("issued WorkOrder has no Continuation identity")
    }
}
