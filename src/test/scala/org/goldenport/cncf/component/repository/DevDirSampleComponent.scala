package devdirsample

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.*
import org.goldenport.cncf.action.{ActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}
import org.goldenport.schema.XString

/*
 * @since   May. 18, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
class DevDirSampleComponent extends Component

final class DevDirSamplePrimaryComponent extends DevDirSampleComponent

final class DevDirSampleBundleFactory extends Component.BundleFactory {
  def primaryFactory: Component.PrimaryComponentFactory =
    DevDirSamplePrimaryFactory
}

object DevDirSamplePrimaryFactory extends Component.PrimaryComponentFactory {
  protected def create_Component(params: ComponentCreate): Component =
    new DevDirSamplePrimaryComponent

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    spec_create(
      DevDirSampleComponent.name,
      DevDirSampleComponent.componentId,
      MainService
    )
}

object DevDirSampleComponent extends Component.Factory {
  val name = "devdirsample"
  val componentId = ComponentId(name)

  protected def create_Component(params: ComponentCreate): Component =
    DevDirSampleComponent()

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    spec_create(
      name,
      componentId,
      MainService
    )
}

object MainService extends ServiceDefinition {
  val specification = ServiceDefinition.Specification.Builder("main")
    .operation(HelloOperation)
    .build()

  object HelloOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("hello").copy(
      response = ResponseDefinition(result = List(XString))
    ).build()

    override def createOperationRequest(
      req: Request
    ): Consequence[HelloQuery] =
      Consequence.success(HelloQuery(req))
  }
}

final case class HelloQuery(
  request: Request
) extends QueryAction() {
  override def createCall(core: ActionCall.Core): ActionCall =
    HelloActionCall(core, this)
}

final case class HelloActionCall(
  core: ActionCall.Core,
  query: HelloQuery
) extends ActionCall {
  override def execute(): Consequence[OperationResponse] =
    response_string("Hello from devdirsample")
}
