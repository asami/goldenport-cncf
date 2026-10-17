package org.goldenport.cncf.validation

import java.lang.reflect.Modifier
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.scalatest.Suite
import ValidationDiscoveryV1.*
import ValidationArtifactEnumeration.*

/** Scoped registration discovery. The caller retains all ClassLoader ownership.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
private[validation] object ValidationDiscovery {
  private final case class ArtifactResult(operations: Vector[Operation], diagnostics: Vector[Diagnostic],
    usable: Boolean, complete: Boolean)

  def discoverC(scope: DiscoveryScope, loader: ClassLoader): Consequence[Inventory] =
    validateScopeC(scope).flatMap { _ =>
      if (loader == null) failure("loader", "null", "caller-owned ClassLoader")
      else {
        val artifacts = scope.artifacts.sortBy(x => (x.componentIdentity, x.artifactIdentity))
        val results = artifacts.map { artifact =>
          val result = _artifact(artifact, loader)
          val limited = _limit(result.operations, result.diagnostics, 32, artifact.artifactIdentity)
          result.copy(operations = limited._1, diagnostics = limited._2)
        }
        val completeness = if (results.forall(_.complete)) Completeness.COMPLETE
          else if (results.exists(_.usable)) Completeness.PARTIAL
          else Completeness.UNKNOWN
        val rows = results.flatMap(_.operations)
        val collisions = rows.groupBy(_.reference).collect { case (reference, values) if values.size > 1 => reference }.toSet
        val operations = rows.map { operation =>
          if (collisions.contains(operation.reference)) operation.copy(metadata = None,
            diagnostics = operation.diagnostics :+ diagnostic("ambiguity", operation.selector.suiteClassName,
              "Logical operation reference occurs more than once in this scope"))
          else operation
        }
        // Include ambiguity diagnostics in the same per-artifact budget.
        val admitted = artifacts.zip(results).map { case (artifact, result) =>
          _limit(operations.filter(x => x.reference.component == artifact.componentIdentity &&
            x.provenance.artifactIdentity == artifact.artifactIdentity), result.diagnostics, 32, artifact.artifactIdentity)
        }
        val limited = _limit(ordered(admitted.flatMap(_._1)), admitted.flatMap(_._2), 256, scope.identity)
        Consequence.success(Inventory(scope.copy(artifacts = artifacts), completeness, limited._1, limited._2))
      }
    }

  def validateScopeC(scope: DiscoveryScope): Consequence[Unit] = {
    if (scope == null || scope.artifacts == null || scope.artifacts.contains(null)) {
      failure("scope", "null", "scope with non-null artifact vector")
    } else if (!textValid(scope.identity) || !textValid(scope.revision)) {
      failure("scope", "identity", "exact nonempty bounded scope identity/revision")
    } else if (scope.artifacts.map(x => (x.componentIdentity, x.artifactIdentity)).distinct.size != scope.artifacts.size) {
      failure("artifacts", "duplicate-artifact", "distinct component/artifact keys")
    } else {
      scope.artifacts.foldLeft(Consequence.unit) { (result, artifact) =>
        result.flatMap { _ =>
          if (!Vector(artifact.componentIdentity, artifact.artifactIdentity, artifact.artifactRevision,
              artifact.sourceRevision).forall(textValid) || !packageValid(artifact.packagePrefix)) {
            failure("artifact", "artifact", "exact artifact identities/revisions and package namespace")
          } else {
            try {
              if (rootValid(artifact.root)) Consequence.unit
              else failure("root", "root", "canonical absolute local directory or regular JAR")
            } catch {
              case NonFatal(_) => failure("root", "root", "accessible canonical local artifact")
              case _: LinkageError => failure("root", "root", "accessible canonical local artifact")
            }
          }
        }
      }
    }
  }

  def ordered(operations: Vector[Operation]): Vector[Operation] =
    operations.sortBy(x => (x.reference.component, x.reference.specification, x.reference.operation,
      x.provenance.artifactIdentity, x.selector.suiteClassName, x.selector.testName))

  def failure[A](field: String, policy: String, expected: String): Consequence[A] =
    Consequence.argumentPolicyViolation(field, s"validation.discovery.$policy", expected, "invalid")

  private def _artifact(artifact: ArtifactScope, loader: ClassLoader): ArtifactResult = {
    val enumeration = enumerate(artifact)
    val results = enumeration.names.map { name =>
      try {
        val clazz = Class.forName(name, false, loader)
        if (!classOf[Suite].isAssignableFrom(clazz) || Modifier.isAbstract(clazz.getModifiers)) {
          ArtifactResult(Vector.empty, Vector.empty, true, true)
        } else if (!sameArtifact(clazz, artifact.root)) {
          _unsupported("provenance", name, "Suite code source differs from the selected artifact")
        } else {
          _suite(clazz, artifact)
        }
      } catch {
        case NonFatal(_) => _unsupported("loading", name, "Class loading or suite admission failed")
        case _: LinkageError => _unsupported("loading", name, "Class linkage or suite admission failed")
      }
    }
    ArtifactResult(results.flatMap(_.operations), enumeration.diagnostics ++ results.flatMap(_.diagnostics),
      enumeration.usable, enumeration.diagnostics.isEmpty && results.forall(_.complete))
  }

  private def _suite(clazz: Class[?], artifact: ArtifactScope): ArtifactResult = {
    try {
      if (!constructible(clazz)) _unsupported("unsupported-suite", clazz.getName,
        "Only public concrete no-arg flat suites are supported")
      else {
        val suite = clazz.getConstructor().newInstance().asInstanceOf[Suite]
        val names = suite.testNames.toVector.sorted
        val nested = suite.nestedSuites.nonEmpty
        val invalidnames = names.exists(x => !textValid(x))
        val interpreted = ValidationAnnotationInterpretation.interpret(clazz, names, artifact)
        val diagnostics = interpreted.diagnostics ++ (if (nested || invalidnames) {
          Vector(diagnostic("unsupported-suite", clazz.getName,
            "Nested suites or malformed registered test names are unsupported"))
        } else Vector.empty)
        val operations = if (invalidnames) interpreted.operations.map(_.copy(metadata = None)) else interpreted.operations
        ArtifactResult(operations, diagnostics, true, !nested && !invalidnames)
      }
    } catch {
      case _: NoSuchMethodException => _unsupported("unsupported-suite", clazz.getName,
        "Suite requires a public no-arg constructor")
      case NonFatal(_) => _unsupported("suite-construction", clazz.getName, "Suite registration could not be inspected")
      case _: LinkageError => _unsupported("suite-construction", clazz.getName, "Suite registration linkage failed")
    }
  }

  private def _unsupported(code: String, source: String, message: String): ArtifactResult =
    ArtifactResult(Vector.empty, Vector(diagnostic(code, source, message)), true, false)

  private def _limit(
    operations: Vector[Operation], diagnostics: Vector[Diagnostic], limit: Int, source: String
  ): (Vector[Operation], Vector[Diagnostic]) = {
    val total = diagnostics.size + operations.map(_.diagnostics.size).sum
    if (total <= limit) (operations, diagnostics)
    else {
      var remaining = limit - 1
      val scope = diagnostics.take(remaining)
      remaining -= scope.size
      val rows = operations.map { operation =>
        val kept = operation.diagnostics.take(remaining)
        remaining -= kept.size
        operation.copy(diagnostics = kept)
      }
      (rows, scope :+ diagnostic("truncated", source, "Additional discovery diagnostics omitted"))
    }
  }
}
