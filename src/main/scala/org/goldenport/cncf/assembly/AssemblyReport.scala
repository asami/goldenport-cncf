package org.goldenport.cncf.assembly

import java.nio.file.Paths
import scala.collection.mutable
import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOriginLabel
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Apr. 10, 2026
 *  version Apr. 11, 2026
 * @version May.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AssemblyWarning(
  kind: String,
  severity: String,
  componentName: String,
  reason: Option[String] = None,
  selectedOrigin: Option[String] = None,
  droppedOrigins: Vector[String] = Vector.empty,
  message: String
) {
  def toRecord: Record =
    Record.data(
      "kind" -> kind,
      "severity" -> severity,
      "component" -> componentName,
      "reason" -> reason,
      "selectedOrigin" -> selectedOrigin,
      "droppedOrigins" -> droppedOrigins,
      "message" -> message
    )
}

final class AssemblyReport {
  private val _warnings = mutable.ArrayBuffer.empty[AssemblyWarning]

  def warnings: Vector[AssemblyWarning] = _warnings.toVector

  def hasWarnings: Boolean = _warnings.nonEmpty

  def addWarning(warning: AssemblyWarning): Unit =
    if (!_warnings.contains(warning))
      _warnings += warning

  def toRecord: Record =
    Record.data(
      "status" -> (if (hasWarnings) "warning" else "ok"),
      "warningCount" -> warnings.size,
      "warnings" -> warnings.map(_.toRecord)
    )
}

object AssemblyReport {
  final case class Selection(
    selected: Component,
    dropped: Vector[Component],
    reason: String
  )

  def selectPreferred(
    existing: Component,
    candidate: Component
  ): Selection = {
    val existingPriority = _priority(existing)
    val candidatePriority = _priority(candidate)
    if (candidatePriority > existingPriority)
      Selection(
        selected = candidate,
        dropped = Vector(existing),
        reason = _reason(existing, candidate)
      )
    else
      Selection(
        selected = existing,
        dropped = Vector(candidate),
        reason = _reason(existing, candidate)
      )
  }

  def duplicateComponentWarning(
    componentName: String,
    selected: Component,
    dropped: Vector[Component],
    reason: String
  ): AssemblyWarning =
    AssemblyWarning(
      kind = "duplicate-component",
      severity = "warning",
      componentName = componentName,
      reason = Some(reason),
      selectedOrigin = Some(ComponentOriginLabel.userLabel(selected.origin.label)),
      droppedOrigins = dropped.map(x => ComponentOriginLabel.userLabel(x.origin.label)).distinct,
      message =
        s"duplicate component '${componentName}' was collapsed during assembly; " +
          s"selected=${ComponentOriginLabel.userLabel(selected.origin.label)}, dropped=${dropped.map(x => ComponentOriginLabel.userLabel(x.origin.label)).distinct.mkString(",")}, reason=${reason}"
    )

  def isSameAssemblySource(
    lhs: Component,
    rhs: Component
  ): Boolean =
    _same_component_name(lhs, rhs) &&
      lhs.origin.label == rhs.origin.label &&
      _same_factory_class(lhs, rhs) &&
      _same_artifact_source(lhs, rhs)

  private def _priority(component: Component): Int =
    _priority(component.origin.label)

  private def _priority(origin: String): Int =
    if (origin.contains(":sar:") || origin.contains(":sar-dir:")) 400
    else if (origin.contains(":car:") || origin.contains(":car-dir:")) 300
    else if (origin == "builtin") 200
    else if (origin == "main") 100
    else 0

  private def _reason(existing: Component, candidate: Component): String = {
    val existingOrigin = existing.origin.label
    val candidateOrigin = candidate.origin.label
    if ((candidateOrigin.contains(":sar:") || candidateOrigin.contains(":sar-dir:")) &&
        (existingOrigin.contains(":car:") || existingOrigin.contains(":car-dir:")))
      "bundled-subsystem-component-preferred-over-standalone-component"
    else if ((existingOrigin.contains(":sar:") || existingOrigin.contains(":sar-dir:")) &&
             (candidateOrigin.contains(":car:") || candidateOrigin.contains(":car-dir:")))
      "bundled-subsystem-component-preferred-over-standalone-component"
    else if (_priority(candidate) > _priority(existing))
      s"higher-origin-priority:${candidateOrigin}"
    else
      s"higher-origin-priority:${existingOrigin}"
  }

  private def _same_component_name(
    lhs: Component,
    rhs: Component
  ): Boolean =
    NamingConventions.toComparisonKey(lhs.core.name) ==
      NamingConventions.toComparisonKey(rhs.core.name)

  private def _same_factory_class(
    lhs: Component,
    rhs: Component
  ): Boolean =
    lhs.core.factory.map(_.getClass.getName) == rhs.core.factory.map(_.getClass.getName)

  private def _same_artifact_source(
    lhs: Component,
    rhs: Component
  ): Boolean =
    (lhs.artifactMetadata, rhs.artifactMetadata) match {
      case (Some(l), Some(r)) =>
        l.sourceType == r.sourceType &&
          l.name == r.name &&
          l.component == r.component &&
          l.archivePath.flatMap(_normalize_path) == r.archivePath.flatMap(_normalize_path)
      case _ =>
        false
    }

  private def _normalize_path(
    path: String
  ): Option[String] =
    Option(path).map(p => Paths.get(p).toAbsolutePath.normalize.toString)
}
