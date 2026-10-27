package org.goldenport.cncf.finding

import java.lang.reflect.AnnotatedElement
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.finding.annotation.FindingDisposition
import FindingDispositionV1.*

/** One portable property representation shared by annotations and model adapters.
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
object FindingDispositionRepresentation {
  final case class Properties(
    abiVersion: Int,
    rule: String,
    finding: String,
    scope: String,
    target: String,
    kind: String,
    reason: String,
    provenance: String
  )

  def decodeC(properties: Properties): Consequence[Declaration] =
    if (properties == null) Consequence.argumentInvalid("finding properties must be present")
    else Kind.values.find(_.name == properties.kind) match {
      case None => Consequence.argumentInvalid("unsupported finding disposition kind")
      case Some(kind) => validateC(Declaration(Key(properties.rule, properties.finding,
        Target(properties.scope, properties.target)), kind, properties.reason, properties.provenance, properties.abiVersion))
    }

  def encodeC(declaration: Declaration): Consequence[Properties] = validateC(declaration).map { value =>
    Properties(value.abiVersion, value.key.rule, value.key.finding, value.key.target.scope,
      value.key.target.identity, value.kind.name, value.reason, value.provenance)
  }

  def annotationC(annotation: FindingDisposition): Consequence[Declaration] =
    if (annotation == null) Consequence.argumentInvalid("finding annotation must be present")
    else try decodeC(Properties(annotation.abiVersion(), annotation.rule(), annotation.finding(), annotation.scope(),
      annotation.target(), annotation.kind(), annotation.reason(), annotation.provenance()))
    catch { case NonFatal(error) => Consequence.argumentInvalid(s"unreadable finding annotation: ${error.getClass.getName}") }

  /** The inspecting tool supplies the actual element's stable semantic target.
    * Annotation placement cannot silently redirect a decision to another target.
    * There is no inheritance, wildcard match, automatic insertion or workflow call.
    */
  def readC(element: AnnotatedElement, target: Target): Consequence[Vector[Declaration]] =
    if (element == null || target == null) Consequence.argumentInvalid("annotation element and target must be present")
    else try {
      val annotations = element.getDeclaredAnnotationsByType(classOf[FindingDisposition]).toVector
      annotations.foldLeft(Consequence.success(Vector.empty[Declaration])) { (prior, annotation) =>
        for {
          values <- prior
          value <- annotationC(annotation)
          _ <- if (value.key.target == target) Consequence.unit
            else Consequence.argumentInvalid("finding annotation does not bind to the inspected target")
          _ <- if (!values.exists(_.key == value.key)) Consequence.unit
            else Consequence.argumentInvalid("duplicate annotation finding binding")
        } yield values :+ value
      }
    } catch { case NonFatal(error) => Consequence.argumentInvalid(s"unreadable finding annotations: ${error.getClass.getName}") }
}
