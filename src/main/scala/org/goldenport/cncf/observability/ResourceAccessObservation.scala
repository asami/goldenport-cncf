package org.goldenport.cncf.observability

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.resource.{ResourceAccess, ResourceContent, ResourceReference}

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
object ResourceAccessObservation {
  def observed(
    access: ResourceAccess
  )(using context: ExecutionContext): ResourceAccess =
    new ResourceAccess {
      def read(reference: ResourceReference): Consequence[ResourceContent] = {
        val provider = access.providerMetadata(reference)
        val chokepoint = DslChokepointContext(
          domain = "resource",
          operation = "read",
          attributes = Record.dataAuto(
            "resource.scheme" -> reference.scheme
          ) ++ Record.dataAuto(provider.safeAttributes*)
        )
        DslChokepointRunner.run(chokepoint) {
          DslChokepointRunner.phase(chokepoint, DslChokepointPhase.Resolve) {
            access.read(reference)
          }
        }
      }

      override def providerMetadata(reference: ResourceReference) =
        access.providerMetadata(reference)
    }
}
