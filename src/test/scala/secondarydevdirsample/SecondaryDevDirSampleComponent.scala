package secondarydevdirsample

import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.protocol.Protocol

/*
 * @since   Jul. 29, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecondaryDevDirSampleComponent extends Component {
  override def displayName: String = SecondaryDevDirSampleComponent.name
}

object SecondaryDevDirSampleComponent extends Component.Factory {
  val name = "secondarydevdirsample"
  val componentId = ComponentId("org.goldenport.fixture.SecondaryDevDirSample")

  protected def create_Component(params: ComponentCreate): Component =
    new SecondaryDevDirSampleComponent

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    Component.Core.create(
      componentId.name,
      componentId,
      ComponentInstanceId.default(componentId),
      Protocol.empty,
      this
    )
}
