package org.goldenport.cncf.processexecution

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, LinkOption, Path, SimpleFileVisitor}
import org.goldenport.Consequence
import org.goldenport.cncf.workarea.WorkAreaSpace

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Runtime-owned filesystem boundary for one Process Execution invocation.
 * Components receive only logical WorkAreaRelativePath values and never this
 * host path wrapper.
 */
final class ProcessExecutionWorkArea private[processexecution] (
  private val _root: Path
) extends AutoCloseable {
  def root: Path = _root

  def workingDirectoryC(path: Option[WorkAreaRelativePath]): Consequence[Path] =
    path match {
      case Some(value) =>
        _ensure_directory_c(value)
      case None => Consequence.success(_root)
    }

  def inputPathC(path: WorkAreaRelativePath): Consequence[Path] =
    _resolve_c(path, createParents = false).flatMap { resolved =>
      if (Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS))
        Consequence.success(resolved)
      else
        Consequence.operationIllegal("process_exec", "WorkArea input file is not available")
    }

  /**
   * Creates only the parents of declared output paths. The child process
   * remains responsible for creating the declared artifact itself.
   */
  def prepareOutputsC(execution: ResolvedProcessExecution): Consequence[Unit] =
    execution.request.outputs.foldLeft(Consequence.unit) { (z, declaration) =>
      z.flatMap { _ =>
        _output_path_c(execution, declaration.path, createParents = true).map(_ => ())
      }
    }

  def collectArtifactsC(
    execution: ResolvedProcessExecution
  ): Consequence[ProcessExecutionArtifactCollection] =
    _work_area_limit_exceeded_c(execution.effectiveLimits.workAreaBytes.get).flatMap {
      case true => Consequence.success(ProcessExecutionArtifactCollection(Vector.empty, limitExceeded = true))
      case false => _collect_declared_artifacts_c(execution)
    }

  private def _collect_declared_artifacts_c(
    execution: ResolvedProcessExecution
  ): Consequence[ProcessExecutionArtifactCollection] = {
    val countLimit = execution.effectiveLimits.artifactCount.get
    val totalLimit = execution.effectiveLimits.artifactBytes.get
    if (execution.request.outputs.size > countLimit)
      Consequence.success(ProcessExecutionArtifactCollection(Vector.empty, limitExceeded = true))
    else {
      execution.request.outputs.foldLeft(Consequence.success((Vector.empty[ProcessExecutionArtifact], 0L, false))) {
        case (z, declaration) =>
          z.flatMap { case (artifacts, total, exceeded) =>
            if (exceeded)
              Consequence.success((artifacts, total, exceeded))
            else
            _output_path_c(execution, declaration.path, createParents = false).flatMap { resolved =>
              if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS))
                Consequence.success((artifacts, total, false))
              else if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS))
                Consequence.operationIllegal("process_exec", "declared artifact is not a regular file")
              else {
                val bytes = Files.size(resolved)
                if (bytes > declaration.maximumBytes || bytes > totalLimit - total)
                  Consequence.success((artifacts, total, true))
                else
                  Consequence.success(
                    (artifacts :+ ProcessExecutionArtifact(declaration.name, declaration.kind, bytes), total + bytes, false)
                  )
              }
            }
          }
      }.map { case (artifacts, _, exceeded) =>
        ProcessExecutionArtifactCollection(artifacts, exceeded)
      }
    }
  }

  private def _work_area_limit_exceeded_c(limit: Long): Consequence[Boolean] =
    try {
      var bytes = 0L
      var invalid = false
      Files.walkFileTree(_root, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult = {
          if (attributes.isSymbolicLink)
            invalid = true
          else if (attributes.isRegularFile)
            bytes = math.addExact(bytes, attributes.size())
          FileVisitResult.CONTINUE
        }
      })
      if (invalid)
        Consequence.operationIllegal("process_exec", "WorkArea symbolic links are not allowed")
      else
        Consequence.success(bytes > limit)
    } catch {
      case _: java.lang.ArithmeticException => Consequence.success(true)
      case _: java.io.IOException =>
        Consequence.operationIllegal("process_exec", "WorkArea is unavailable")
    }

  def close(): Unit =
    if (Files.exists(_root, LinkOption.NOFOLLOW_LINKS))
      Files.walkFileTree(_root, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult = {
          Files.deleteIfExists(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(directory: Path, error: java.io.IOException): FileVisitResult = {
          if (error != null) throw error
          Files.deleteIfExists(directory)
          FileVisitResult.CONTINUE
        }
      })

  private def _resolve_c(
    path: WorkAreaRelativePath,
    createParents: Boolean
  ): Consequence[Path] =
    try {
      val candidate = _root.resolve(path.value).normalize
      if (!candidate.startsWith(_root))
        Consequence.operationIllegal("process_exec", "WorkArea path escapes its execution root")
      else if (createParents)
        _create_parent_directories_c(candidate).flatMap(_ => _verify_no_symlink_c(candidate).map(_ => candidate))
      else
        _verify_no_symlink_c(candidate).map(_ => candidate)
    } catch {
      case _: java.io.IOException =>
        Consequence.operationIllegal("process_exec", "WorkArea path is unavailable")
    }

  private def _output_path_c(
    execution: ResolvedProcessExecution,
    path: WorkAreaRelativePath,
    createParents: Boolean
  ): Consequence[Path] = {
    val relative = execution.request.workingDirectory match {
      case Some(workingdirectory) => s"${workingdirectory.value}/${path.value}"
      case None => path.value
    }
    WorkAreaRelativePath.parseC(relative).flatMap(_resolve_c(_, createParents))
  }

  private def _verify_no_symlink_c(candidate: Path): Consequence[Unit] = {
    val relative = _root.relativize(candidate)
    val elements = relative.iterator()
    var current = _root
    var escaped = false
    while (elements.hasNext && !escaped) {
      current = current.resolve(elements.next())
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
        escaped = true
    }
    if (escaped)
      Consequence.operationIllegal("process_exec", "WorkArea symlink paths are not allowed")
    else
      Consequence.unit
  }

  private def _ensure_directory_c(path: WorkAreaRelativePath): Consequence[Path] =
    _resolve_c(path, createParents = true).flatMap { candidate =>
      try {
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
          if (Files.isSymbolicLink(candidate) || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS))
            Consequence.operationIllegal("process_exec", "WorkArea working directory is not available")
          else
            Consequence.success(candidate)
        } else {
          Files.createDirectory(candidate)
          Consequence.success(candidate)
        }
      } catch {
        case _: java.io.IOException =>
          Consequence.operationIllegal("process_exec", "WorkArea working directory is not available")
      }
    }

  private def _create_parent_directories_c(candidate: Path): Consequence[Unit] =
    try {
      val parent = Option(candidate.getParent).getOrElse(_root)
      val elements = _root.relativize(parent).iterator()
      var current = _root
      var available = true
      while (elements.hasNext && available) {
        current = current.resolve(elements.next())
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
          if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
            available = false
        } else {
          Files.createDirectory(current)
        }
      }
      if (available)
        Consequence.unit
      else
        Consequence.operationIllegal("process_exec", "WorkArea path is not available")
    } catch {
      case _: java.io.IOException =>
        Consequence.operationIllegal("process_exec", "WorkArea path is not available")
    }
}

object ProcessExecutionWorkArea {
  def allocateC(space: WorkAreaSpace): Consequence[ProcessExecutionWorkArea] =
    space.createTempDir("process-exec").flatMap { root =>
      try {
        Consequence.success(new ProcessExecutionWorkArea(root.toRealPath()))
      } catch {
        case _: java.io.IOException =>
          Consequence.operationIllegal("process_exec", "execution WorkArea is unavailable")
      }
    }
}

final case class ProcessExecutionArtifactCollection(
  artifacts: Vector[ProcessExecutionArtifact],
  limitExceeded: Boolean
)
