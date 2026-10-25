package org.goldenport.cncf.processexecution

import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ScopeContext

/*
 * @since   Oct. 23, 2026
 * @version Oct. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class HostSourceTarget private (val value: String)

object HostSourceTarget {
  private val _pattern = "[a-z][a-z0-9]*(?:-[a-z0-9]+)*".r

  def parseC(value: String): Consequence[HostSourceTarget] =
    if (value != null && value.length <= 47 && _pattern.matches(value))
      Consequence.success(new HostSourceTarget(value))
    else
      Consequence.argumentFormatError("target", "lower-hyphen logical identifier of 1 to 47 characters", "invalid")
}

final class HostSourcePayload private (val bytes: Vector[Byte])

object HostSourcePayload {
  val MAX_BYTES: Int = 65536

  def fromUtf8C(value: String): Consequence[HostSourcePayload] =
    if (value == null || value.length > MAX_BYTES)
      Consequence.argumentFormatError("payload", "strict UTF-8 within 65536 bytes", "invalid")
    else try {
      val encoded = StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(value))
      if (encoded.remaining() > MAX_BYTES)
        Consequence.argumentLimitExceeded("payload", MAX_BYTES.toLong, encoded.remaining().toLong, "host-source.payload-bytes")
      else {
        val bytes = new Array[Byte](encoded.remaining())
        encoded.get(bytes)
        Consequence.success(new HostSourcePayload(bytes.toVector))
      }
    } catch {
      case _: CharacterCodingException =>
        Consequence.argumentFormatError("payload", "strict UTF-8", "invalid")
    }
}

enum HostSourceReadResult {
  case Complete(bytes: Vector[Byte])
  case Failed(result: ProcessExecutionResult)
  case Unconfirmed(result: ProcessExecutionResult)
}

object HostSourceRead {
  private[cncf] def resolveC(
    scope: ScopeContext,
    target: HostSourceTarget,
    payload: HostSourcePayload
  ): Consequence[ResolvedProcessExecution] = {
    val identity = s"host-source-${target.value}-read"
    for {
      capability <- ProcessCapabilityId.parseC(identity)
      execution <- ProcessExecutionAdmission.resolveC(scope,
        ProcessExecutionRequest(capability, input = ProcessExecutionInput.Bytes(payload.bytes)))
      _ <- _validate_definition_c(execution.definition, identity)
    } yield execution
  }

  private def _validate_definition_c(definition: ProcessProgramDefinition, identity: String): Consequence[Unit] = {
    val policy = definition.argumentPolicy
    if (definition.safeProgramIdentity == identity &&
        policy.admission == ProcessArgumentAdmission.Template &&
        policy.permittedArgumentTemplates == Set(ProcessArgumentTemplate(Vector.empty)) &&
        definition.allowedArtifacts.isEmpty && definition.allowedInputFiles.isEmpty &&
        definition.allowedInputFilePaths.isEmpty && definition.allowedResourceTrees.isEmpty &&
        !definition.allowsWorkingDirectory)
      Consequence.unit
    else
      Consequence.argumentPolicyViolation("hostSource", "host-source.fixed-program",
        "exact identity and sole empty argument template without file or working-directory permissions", "unapproved")
  }

  private[cncf] def decodeC(result: ProcessExecutionResult): Consequence[HostSourceReadResult] =
    result.termination match {
      case ProcessExecutionTermination.TerminationUnconfirmed =>
        Consequence.success(HostSourceReadResult.Unconfirmed(result))
      case ProcessExecutionTermination.Exited(0) if _complete_capture(result.stdout) && _complete_capture(result.stderr) =>
        try {
          StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(result.stdout.content.toArray))
          Consequence.success(HostSourceReadResult.Complete(result.stdout.content))
        } catch {
          case _: CharacterCodingException =>
            Consequence.argumentFormatError("stdout", "complete strict UTF-8 host-source response", "invalid")
        }
      case _ => Consequence.success(HostSourceReadResult.Failed(result))
    }

  private def _complete_capture(capture: ProcessExecutionCapture): Boolean =
    !capture.truncated && capture.byteCount == capture.content.length.toLong
}
