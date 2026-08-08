package org.simplemodeling.textus.corpus

import cats.data.NonEmptyVector

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.*
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec

/*
 * A frozen-release style generated Core. The bare ComponentId call is
 * intentional executable evidence for CID-06D and must not be canonicalized.
 *
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class CorpusComponent extends Component {
  override def displayName: String = "Corpus"
}

final class CorpusComponentFactory extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component =
    new CorpusComponent

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core = {
    val componentid = ComponentId("Corpus")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(
            spec.ServiceDefinition(
              name = CorpusComponentFactory.serviceName,
              operations = spec.OperationDefinitionGroup(
                NonEmptyVector.of(new CorpusScalarOperation)
              )
            )
          )
        )
      ),
      this
    )
  }
}

object CorpusComponentFactory {
  val serviceName: String = "compatibility"
  val operationName: String = "echo"
  val responseBody: String = "corpus-0.1.0-compatible"
}

final class CorpusScalarOperation extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = CorpusComponentFactory.operationName,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(new CorpusScalarAction(req))
}

final class CorpusScalarAction(
  val request: Request
) extends QueryAction {
  override def createCall(core: ActionCall.Core): ActionCall =
    new CorpusScalarActionCall(core)
}

final class CorpusScalarActionCall(
  val core: ActionCall.Core
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar(CorpusComponentFactory.responseBody))
}
