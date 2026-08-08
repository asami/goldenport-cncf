package org.goldenport.cncf.subsystem.fixture

import cats.data.NonEmptyVector

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.*
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec

/*
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[subsystem] object Phase56NamespaceIsolatedRuntimeFixture {
  val serviceName: String = "notice"
  val operationName: String = "announce"

  def protocol(response: String): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = serviceName,
            operations = spec.OperationDefinitionGroup(
              NonEmptyVector.of(ScalarOperation(operationName, response))
            )
          )
        )
      )
    )

  private final case class ScalarOperation(
    override val name: String,
    responsebody: String
  ) extends spec.OperationDefinition {
    override val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = name,
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition.void
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(ScalarAction(req, responsebody))
  }

  private final case class ScalarAction(
    request: Request,
    responsebody: String
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      ScalarActionCall(core, responsebody)
  }

  private final case class ScalarActionCall(
    core: ActionCall.Core,
    responsebody: String
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar(responsebody))
  }
}

package phase56alpha {
  import org.goldenport.cncf.component.*
  import org.goldenport.cncf.subsystem.fixture.Phase56NamespaceIsolatedRuntimeFixture

  /*
   * @since   Aug.  8, 2026
   * @version Aug.  8, 2026
   * @author  ASAMI, Tomoharu
   */
  final class SharedComponent extends Component {
    override def displayName: String = "Shared"
  }

  final class ComponentFactory extends Component.Factory {
    protected def create_Component(params: ComponentCreate): Component =
      new SharedComponent

    protected def create_Core(
      params: ComponentCreate,
      component: Component
    ): Component.Core = {
      val componentid = ComponentId("org.alpha.textus.Shared")
      Component.Core.create(
        componentid.name,
        componentid,
        ComponentInstanceId.default(componentid),
        Phase56NamespaceIsolatedRuntimeFixture.protocol("alpha"),
        this
      )
    }
  }
}

package phase56beta {
  import org.goldenport.cncf.component.*
  import org.goldenport.cncf.subsystem.fixture.Phase56NamespaceIsolatedRuntimeFixture

  /*
   * @since   Aug.  8, 2026
   * @version Aug.  8, 2026
   * @author  ASAMI, Tomoharu
   */
  final class SharedComponent extends Component {
    override def displayName: String = "Shared"
  }

  final class ComponentFactory extends Component.Factory {
    protected def create_Component(params: ComponentCreate): Component =
      new SharedComponent

    protected def create_Core(
      params: ComponentCreate,
      component: Component
    ): Component.Core = {
      val componentid = ComponentId("org.beta.textus.Shared")
      Component.Core.create(
        componentid.name,
        componentid,
        ComponentInstanceId.default(componentid),
        Phase56NamespaceIsolatedRuntimeFixture.protocol("beta"),
        this
      )
    }
  }
}
