package secondarydevdirsample

import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.protocol.Protocol

/*
 * @since   Jul. 29, 2026
 * @version Jul. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecondaryDevDirSampleComponent extends Component

object SecondaryDevDirSampleComponent extends Component.Factory {
  val name = "secondarydevdirsample"
  val componentId = ComponentId(name)

  protected def create_Component(params: ComponentCreate): Component =
    new SecondaryDevDirSampleComponent

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    Component.Core.create(
      name,
      componentId,
      ComponentInstanceId.default(componentId),
      Protocol.empty,
      this
    )
}
