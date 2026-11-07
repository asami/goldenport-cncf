package org.goldenport.cncf.processexecution

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.Path
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workspace.WorkspaceId

/** Closed SBT 1.x TestCompile export projection. This view grants no execution authority.
 * @since   Nov.  6, 2026
 * @version Nov.  7, 2026
 */
object SbtValidationDiscovery {
  final case class Binding(componentIdentity: String, artifactIdentity: String,
    artifactRevision: String, packagePrefix: String) {
    def validateC: Consequence[Binding] =
      if (Vector(componentIdentity, artifactIdentity, artifactRevision).forall(value => value != null &&
          value.nonEmpty && value.length <= 512 && !value.exists(_.isControl)) && packagePrefix != null &&
          packagePrefix.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) Consequence.success(this)
      else Consequence.configurationInvalid("Ordinary test discovery requires existing exact artifact identities and package scope")
  }
  final case class Build(workspaceId: WorkspaceId, binding: Binding, products: Vector[Path], fullClasspath: Vector[Path])
  enum Result {
    case Available(build: Build)
    case Unavailable(reason: String)
  }
  val tasks: Vector[String] = Vector("Test / compile", "export Test / fullClasspath", "export Test / exportedProducts")

  def decode(workspaceId: WorkspaceId, binding: Binding, result: ProcessExecutionResult): Result = {
    def _unavailable_(reason: String): Result = Result.Unavailable(reason)
    if (binding == null || !binding.validateC.isSuccess || result == null ||
        result.termination != ProcessExecutionTermination.Exited(0)) _unavailable_("ordinary test build did not complete successfully")
    else try {
      val captures = Vector(result.stdout, result.stderr)
      if (captures.exists(capture => capture == null || capture.content == null || capture.truncated ||
          capture.byteCount != capture.content.size.toLong)) _unavailable_("ordinary test build capture is incomplete")
      else {
        def _text_(capture: ProcessExecutionCapture): String = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(capture.content.toArray)).toString
        val stdout = _text_(result.stdout)
        val stderr = _text_(result.stderr)
        def _records_(text: String): Vector[String] = text.linesIterator.filter(_.nonEmpty)
          .filterNot(line => Vector("[info]", "[warn]", "[success]", "[debug]").exists(prefix => line.startsWith(prefix + " "))).toVector
        val records = _records_(stdout)
        def _paths_(record: String): Option[Vector[Path]] = {
          val entries = record.split(java.util.regex.Pattern.quote(File.pathSeparator), -1).toVector
          val paths = entries.map(Path.of(_))
          Option.when(paths.nonEmpty && paths.distinct == paths && paths.forall(path =>
            path.isAbsolute && path.normalize == path && !path.toString.exists(_.isControl)))(paths)
        }
        val decoded = for {
          _ <- Option.when(records.size == 2 && _records_(stderr).isEmpty)(())
          classpath <- _paths_(records(0))
          products <- _paths_(records(1))
          _ <- Option.when(products.forall(classpath.contains)) (())
        } yield Build(workspaceId, binding, products, classpath)
        decoded.map(Result.Available.apply).getOrElse(_unavailable_(
          "SBT 1.x discovery requires exactly ordered fullClasspath/exportedProducts export records with nonempty absolute products within classpath"))
      }
    } catch { case NonFatal(_) => _unavailable_("ordinary test build exports are malformed or not complete UTF-8") }
  }

  private[cncf] def admitC(target: SbtTarget, operation: SbtOperation,
    execution: ResolvedProcessExecution): Consequence[Unit] =
    if (!target.validationDiscovery) Consequence.unit
    else if (operation == SbtOperation.TestCompile && execution.definition._sbt_metadata.exists(_.discovery.nonEmpty)) Consequence.unit
    else Consequence.serviceUnavailable("ValidationSetupRequired: ordinary TestCompile discovery projection is not declared")
}
