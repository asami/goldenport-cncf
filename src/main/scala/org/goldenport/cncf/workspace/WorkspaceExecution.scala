package org.goldenport.cncf.workspace

import java.nio.file.{Files, Path}
import java.nio.file.attribute.BasicFileAttributes
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId}
import org.goldenport.cncf.context.ScopeContext

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
opaque type WorkspaceId = String

object WorkspaceId {
  private val _pattern = "[a-z][a-z0-9-]{0,63}".r

  def parseC(value: String): Consequence[WorkspaceId] =
    Option(value) match {
      case Some(_pattern()) => Consequence.success(value)
      case _ => Consequence.argumentFormatError("workspaceId", "safe logical workspace ID", "invalid")
    }

  extension (workspaceId: WorkspaceId) {
    def value: String = workspaceId
    def print: String = workspaceId
  }
}

opaque type WorkspaceRelativePath = String

object WorkspaceRelativePath {
  def parseC(value: String): Consequence[WorkspaceRelativePath] = {
    val text = Option(value).getOrElse("")
    val segments = text.split("/", -1).toVector
    if (text.isEmpty || text.startsWith("/") || text.matches("[A-Za-z]:.*") ||
      text.contains("\\") || text.exists(_.isControl) ||
      segments.exists(x => x.isEmpty || x == "." || x == ".."))
      Consequence.argumentFormatError("path", "normalized workspace-relative path", "invalid")
    else
      Consequence.success(text)
  }

  extension (path: WorkspaceRelativePath) {
    def value: String = path
    def print: String = path
  }
}

private[cncf] enum WorkspacePermission {
  case ReadOnly, Manage
}

private[cncf] final case class WorkspaceGrant(
  componentId: ComponentId,
  workspaceId: WorkspaceId,
  permission: WorkspacePermission
)

/** One trusted binding, shared by semantic tools. Host paths never reach CAR data. */
private[cncf] final class WorkspaceBinding private (
  val workspaceId: WorkspaceId,
  private val _configured_root: Path,
  private val _canonical_root: Path,
  private val _file_key: Option[AnyRef]
) {
  /** Recheck configured symlinks and the canonical directory identity before use. */
  def workingDirectoryC: Consequence[Path] =
    try {
      val current = _configured_root.toRealPath()
      val attributes = Files.readAttributes(current, classOf[BasicFileAttributes])
      if (current == _canonical_root && attributes.isDirectory &&
        Option(attributes.fileKey()) == _file_key)
        Consequence.success(_canonical_root)
      else
        WorkspaceBinding.unavailableC
    } catch {
      case NonFatal(_) => WorkspaceBinding.unavailableC
    }

  /** Resolution seam only; no file content is read, written or enumerated here. */
  def resolveRelativeC(path: WorkspaceRelativePath): Consequence[Path] =
    _resolve_c(path.value)

  private[cncf] def resolveFileC(path: WorkspaceFilePath): Consequence[Path] =
    WorkspaceFilePath.parseC(path.value).flatMap(valid => _resolve_c(valid.value))

  private def _resolve_c(path: String): Consequence[Path] =
    workingDirectoryC.flatMap { root =>
      try {
        val candidate = root.resolve(path).normalize()
        val elements = root.relativize(candidate).iterator()
        var current = root
        var linked = false
        while (elements.hasNext && !linked) {
          current = current.resolve(elements.next())
          linked = Files.isSymbolicLink(current)
        }
        if (candidate.startsWith(root) && !linked)
          Consequence.success(candidate)
        else
          WorkspaceBinding.unavailableC
      } catch {
        case NonFatal(_) => WorkspaceBinding.unavailableC
      }
    }

  override def toString: String = s"WorkspaceBinding(${workspaceId.print})"
}

private[cncf] object WorkspaceBinding {
  def createC(workspaceId: WorkspaceId, root: Path): Consequence[WorkspaceBinding] =
    try {
      if (root == null || !root.isAbsolute)
        unavailableC
      else {
        val configured = root.normalize()
        val canonical = configured.toRealPath()
        val attributes = Files.readAttributes(canonical, classOf[BasicFileAttributes])
        if (attributes.isDirectory)
          Consequence.success(new WorkspaceBinding(
            workspaceId, configured, canonical, Option(attributes.fileKey())
          ))
        else
          unavailableC
      }
    } catch {
      case NonFatal(_) => unavailableC
    }

  def unavailableC[A]: Consequence[A] =
    Consequence.argumentPolicyViolation(
      "workspace", "workspace.execution.root", "unchanged admitted directory", "unavailable"
    )
}

private[cncf] final class WorkspaceExecutionAdmission private (
  private val _bindings: Map[WorkspaceId, WorkspaceBinding],
  private val _grants: Map[(ComponentId, WorkspaceId), WorkspacePermission]
) {
  def bindingC(workspaceId: WorkspaceId): Consequence[WorkspaceBinding] =
    _bindings.get(workspaceId).map(Consequence.success).getOrElse(
      Consequence.argumentPolicyViolation(
        "workspaceId", "workspace.execution.binding", "registered logical workspace", "unknown"
      )
    )

  def admitC(
    scope: ScopeContext,
    workspaceId: WorkspaceId,
    permission: WorkspacePermission
  ): Consequence[WorkspaceBinding] =
    for {
      binding <- bindingC(workspaceId)
      owner <- WorkspaceExecutionAdmission.componentIdC(scope)
      _ <- _grants.get(owner -> workspaceId) match {
        case Some(WorkspacePermission.Manage) => Consequence.unit
        case Some(WorkspacePermission.ReadOnly) if permission == WorkspacePermission.ReadOnly => Consequence.unit
        case _ => Consequence.argumentPolicyViolation(
          "workspaceId", "workspace.execution.component-grant", "component workspace permission", "unapproved"
        )
      }
      _ <- binding.workingDirectoryC
    } yield binding
}

private[cncf] object WorkspaceExecutionAdmission {
  def createC(
    roots: Vector[(WorkspaceId, Path)],
    grants: Vector[WorkspaceGrant]
  ): Consequence[WorkspaceExecutionAdmission] = {
    val ids = roots.map(_._1)
    val owners = grants.map(x => x.componentId -> x.workspaceId)
    if (ids.distinct.size != ids.size || owners.distinct.size != owners.size)
      Consequence.argumentPolicyViolation(
        "workspace", "workspace.execution.configuration", "unique bindings and component grants", "duplicate"
      )
    else if (grants.exists(x => x.componentId == null || x.permission == null || !ids.contains(x.workspaceId)))
      Consequence.argumentPolicyViolation(
        "workspace", "workspace.execution.configuration", "known workspace and actual component grant", "invalid"
      )
    else
      roots.foldLeft(Consequence.success(Map.empty[WorkspaceId, WorkspaceBinding])) {
        case (acc, (id, root)) => for {
          bindings <- acc
          binding <- WorkspaceBinding.createC(id, root)
        } yield bindings.updated(id, binding)
      }.map(bindings => new WorkspaceExecutionAdmission(
        bindings, grants.map(x => (x.componentId -> x.workspaceId) -> x.permission).toMap
      ))
  }

  /** Labels and ScopeKind.Component alone are never component identity. */
  def componentIdC(scope: ScopeContext): Consequence[ComponentId] = {
    def _owner_(current: ScopeContext): Option[ComponentId] = current match {
      case context: Component.Context => Some(context.component.componentId)
      case _ => current.parent.flatMap(_owner_)
    }
    Option(scope).flatMap(_owner_).map(Consequence.success).getOrElse(
      Consequence.argumentPolicyViolation(
        "component", "workspace.execution.component-owner", "actual Component.Context ancestry", "absent"
      )
    )
  }
}
