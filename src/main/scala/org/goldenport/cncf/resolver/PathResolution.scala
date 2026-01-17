package org.goldenport.cncf.resolver

final case class CanonicalPath(
  component: String,
  service: String,
  operation: String
)

sealed trait PathResolutionResult
object PathResolutionResult {
  final case class Success(path: CanonicalPath) extends PathResolutionResult
  final case class Failure(reason: String) extends PathResolutionResult
}

object PathResolution {
  def resolve(
    selector: String,
    registry: Seq[CanonicalPath],
    builtinComponents: Set[String] = Set.empty
  ): PathResolutionResult = {
    val normalized = normalize(selector)
    if (normalized.isEmpty) PathResolutionResult.Failure("selector is required")
    else {
      val rawSegments = splitSegments(normalized)
      if (rawSegments.isEmpty) {
        PathResolutionResult.Failure("selector is required")
      } else {
        val (segments, hadSuffix) = stripSuffixFromLastSegment(rawSegments)
        resolveSegments(segments, registry, builtinComponents) match {
          case success: PathResolutionResult.Success => success
          case failure: PathResolutionResult.Failure
              if hadSuffix && segments.length == 3 =>
            resolveSegments(segments.take(2), registry, builtinComponents)
          case failure => failure
        }
      }
    }
  }

  private def resolveSegments(
    segments: Array[String],
    registry: Seq[CanonicalPath],
    builtinComponents: Set[String]
  ): PathResolutionResult = segments.length match {
    case 0 => PathResolutionResult.Failure("selector is required")
    case 1 => resolveComponentOnly(segments(0), registry, builtinComponents)
    case 2 => resolveComponentService(segments, registry)
    case 3 => resolveFull(segments, registry)
    case _ => PathResolutionResult.Failure("selector contains too many segments")
  }

  private def splitSegments(value: String): Array[String] = {
    val raw =
      if (value.contains("/")) value.split("/")
      else if (value.contains("\\")) value.split("\\\\")
      else value.split("\\.")
    raw.map(_.trim).filter(_.nonEmpty)
  }

  private def stripSuffixFromLastSegment(
    segments: Array[String]
  ): (Array[String], Boolean) = {
    if (segments.isEmpty) (segments, false)
    else {
      val last = segments.last
      val idx = last.lastIndexOf('.')
      if (idx <= 0 || idx == last.length - 1) (segments, false)
      else {
        val trimmed = last.substring(0, idx).trim
        val updated = segments.updated(segments.length - 1, trimmed)
        (updated.filter(_.nonEmpty), true)
      }
    }
  }

  private def normalize(value: String): String = value.trim

  private def resolveFull(
    segments: Array[String],
    registry: Seq[CanonicalPath]
  ): PathResolutionResult = {
    val Array(component, service, operation) = segments
    registry.find { path =>
      matches(component, path.component) &&
        matches(service, path.service) &&
        matches(operation, path.operation)
    } match {
      case Some(path) => PathResolutionResult.Success(path)
      case None => PathResolutionResult.Failure("canonical path not found")
    }
  }

  private def resolveComponentService(
    segments: Array[String],
    registry: Seq[CanonicalPath]
  ): PathResolutionResult = {
    val Array(componentExpr, serviceExpr) = segments
    val matchesService = registry.filter { path =>
      matches(componentExpr, path.component) &&
        matches(serviceExpr, path.service)
    }
    matchesService match {
      case Seq(path) => PathResolutionResult.Success(path)
      case Seq() => PathResolutionResult.Failure("component/service not found")
      case _ => PathResolutionResult.Failure("ambiguous component/service selector")
    }
  }

  private def resolveComponentOnly(
    component: String,
    registry: Seq[CanonicalPath],
    builtinComponents: Set[String]
  ): PathResolutionResult = {
    val matchesComponent = registry.filter(path => matches(component, path.component))
    if (matchesComponent.isEmpty) {
      PathResolutionResult.Failure("component not found")
    } else if (isBuiltin(component, builtinComponents)) {
      PathResolutionResult.Failure("builtin components do not allow omission")
    } else {
      val services = matchesComponent.map(_.service).distinct
      val operations = matchesComponent.map(_.operation).distinct
      if (services.size == 1 && operations.size == 1) {
        PathResolutionResult.Success(matchesComponent.head.copy(
          service = matchesComponent.head.service,
          operation = matchesComponent.head.operation
        ))
      } else {
        PathResolutionResult.Failure("component has multiple services or operations")
      }
    }
  }

  private def matches(input: String, target: String): Boolean =
    input.equalsIgnoreCase(target)

  private def isBuiltin(
    component: String,
    builtinComponents: Set[String]
  ): Boolean =
    builtinComponents.exists(_.equalsIgnoreCase(component))
}
