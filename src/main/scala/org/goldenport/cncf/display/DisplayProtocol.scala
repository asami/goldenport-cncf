package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.observation.Taxonomy

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
/** Physical read-snapshot identity; metadata carries no execution authority. */
object DisplayProtocol {
  val schema: String = "cncf.display-model.v1"
  val version: Int = 1
  val mediaType: String = "application/json"

  final class Source private (val componentId: ComponentId, val resourceType: String)
  object Source {
    def createC(componentId: ComponentId, resourceType: String): Consequence[Source] = {
      if (componentId == null) _input_invalid("componentId")
      else if (resourceType == null || resourceType.isEmpty || resourceType != resourceType.trim)
        _input_invalid("resourceType")
      else Consequence.success(new Source(componentId, resourceType))
    }
    private def _input_invalid[A](path: String): Consequence[A] =
      Consequence.fail(Taxonomy.argumentInvalid,
        new CodecException(Diagnostic("DSP96_WIRE_INPUT_INVALID", path, "expected valid explicit source")))
  }

  final case class Diagnostic(code: String, path: String, reason: String)
  /** Typed redacted cause carrier, returned rather than thrown for validation. */
  final class CodecException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")
}
