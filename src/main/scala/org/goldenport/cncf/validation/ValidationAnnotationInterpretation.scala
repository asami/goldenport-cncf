package org.goldenport.cncf.validation

import java.lang.reflect.Modifier
import scala.util.control.NonFatal
import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}
import ValidationDiscoveryV1.*
import ValidationMetadataV1.Declaration
import ValidationArtifactEnumeration.{diagnostic, textValid}

/** Runtime anchors bind actual registered scenarios to the unchanged S1 resolver.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
private[validation] object ValidationAnnotationInterpretation {
  final case class Interpretation(operations: Vector[Operation], diagnostics: Vector[Diagnostic])
  private final case class Anchor(name: String, identity: String, revision: String,
    declaration: Option[Declaration], diagnostics: Vector[Diagnostic])

  def interpret(suiteClass: Class[?], testNames: Vector[String], artifact: ArtifactScope): Interpretation = {
    val classidentity = suiteClass.getName
    try {
      val annotation = Option(suiteClass.getAnnotation(classOf[ValidationSpecification]))
      val identity = annotation.map(_.identity()).getOrElse("")
      val revision = annotation.map(_.revision()).getOrElse("")
      val specification = _fallback(identity, classidentity)
      val defaultrevision = _fallback(revision, artifact.artifactRevision)
      val provider = _provider(annotation.map(_.declaration()).getOrElse(classOf[java.lang.Void]),
        artifact, classidentity)
      val classdiagnostics = _text_diagnostics(identity, revision, classidentity) ++ provider.left.toOption.toVector
      val anchors = suiteClass.getMethods.toVector.sortBy(_.toGenericString).flatMap { method =>
        Option(method.getAnnotation(classOf[ValidationScenario])).map { value =>
          val source = s"$classidentity#${method.getName}"
          val shape = if (Modifier.isStatic(method.getModifiers) || method.getParameterCount != 0 ||
              method.getReturnType != java.lang.Void.TYPE) {
            Vector(diagnostic("anchor", source, "Scenario anchor must be a public instance no-arg void method"))
          } else Vector.empty
          val declaration = _provider(value.declaration(), artifact, source)
          Anchor(value.name(), value.identity(), value.revision(), declaration.toOption.flatten,
            shape ++ _text_diagnostics(value.identity(), value.revision(), source) ++
              declaration.left.toOption.toVector)
        }
      }
      val invalidanchors = anchors.filter(x => !textValid(x.name) || !testNames.contains(x.name))
      val duplicates = anchors.groupBy(_.name).toVector.filter(_._2.size > 1).sortBy(x => String.valueOf(x._1))
      val suitediagnostics = invalidanchors.map(x => diagnostic("anchor", classidentity,
        "Scenario anchor must name one exact registered test")) ++ duplicates.map { _ =>
        diagnostic("anchor", classidentity, "More than one anchor names a registered scenario")
      } ++ anchors.filter(_.diagnostics.exists(_.code == "anchor")).flatMap(_.diagnostics)
      val index = anchors.groupBy(_.name)
      val operations = testNames.map { name =>
        val anchor = index.get(name).flatMap(_.headOption)
        val diagnostics = classdiagnostics ++ suitediagnostics ++ anchor.toVector.flatMap(_.diagnostics)
        val resolved = ValidationMetadataV1.resolveC(provider.toOption.flatten, anchor.flatMap(_.declaration))
        val resolutiondiagnostics = if (resolved.toOption.isEmpty) {
          Vector(diagnostic("metadata", s"$classidentity#$name", "Declaration interpretation failed"))
        } else Vector.empty
        val all = diagnostics ++ resolutiondiagnostics
        Operation(
          OperationReference(artifact.componentIdentity, specification,
            anchor.map(x => _fallback(x.identity, name)).getOrElse(name)),
          Provenance(artifact.artifactIdentity, artifact.artifactRevision, artifact.sourceRevision,
            anchor.map(x => _fallback(x.revision, defaultrevision)).getOrElse(defaultrevision)),
          ScalaTestSelector(classidentity, name),
          if (all.isEmpty) resolved.toOption else None,
          all
        )
      }
      // Empty suites still report malformed source declarations at scope level.
      Interpretation(operations, if (operations.isEmpty) classdiagnostics ++ suitediagnostics else Vector.empty)
    } catch {
      case NonFatal(_) => _failed(classidentity, testNames, artifact)
      case _: LinkageError => _failed(classidentity, testNames, artifact)
    }
  }

  private def _provider(
    provider: Class[?], artifact: ArtifactScope, source: String
  ): Either[Diagnostic, Option[Declaration]] = {
    if (provider == classOf[java.lang.Void]) Right(None)
    else {
      val invalid = diagnostic("provider", source, "Invalid local typed declaration provider")
      try {
        if (provider == null || !classOf[ValidationDeclarationProvider].isAssignableFrom(provider) ||
            !ValidationArtifactEnumeration.sameArtifact(provider, artifact.root) ||
            !ValidationArtifactEnumeration.constructible(provider)) {
          Left(invalid)
        } else {
          val instance = provider.getConstructor().newInstance().asInstanceOf[ValidationDeclarationProvider]
          val declaration = instance.declaration
          if (declaration == null || declaration.validateC.toOption.isEmpty) Left(invalid)
          else Right(Some(declaration))
        }
      } catch {
        case NonFatal(_) => Left(invalid)
        case _: LinkageError => Left(invalid)
      }
    }
  }

  private def _fallback(value: String, fallback: String): String =
    if (textValid(value)) value else fallback

  private def _text_diagnostics(identity: String, revision: String, source: String): Vector[Diagnostic] =
    if ((identity == "" || textValid(identity)) && (revision == "" || textValid(revision))) Vector.empty
    else Vector(diagnostic("identity", source, "Annotation identity/revision must be empty or exact bounded text"))

  private def _failed(classidentity: String, names: Vector[String], artifact: ArtifactScope): Interpretation = {
    val issue = diagnostic("annotation", classidentity, "Runtime annotation interpretation failed")
    val operations = names.map { name =>
      Operation(OperationReference(artifact.componentIdentity, classidentity, name),
        Provenance(artifact.artifactIdentity, artifact.artifactRevision, artifact.sourceRevision, artifact.artifactRevision),
        ScalaTestSelector(classidentity, name), None, Vector(issue))
    }
    Interpretation(operations, if (operations.isEmpty) Vector(issue) else Vector.empty)
  }
}
