package org.goldenport.cncf.component.repository.fixture.plain

import org.goldenport.cncf.component.*
import org.goldenport.protocol.Protocol

/*
 * @since   Apr. 23, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class _PlainFactoryBackedComponent extends Component

final class ComponentFactory extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component =
    new _PlainFactoryBackedComponent

  protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    Component.Core.create(
      "plain-factory-primary",
      ComponentId("plain_factory_primary"),
      ComponentInstanceId.default(ComponentId("plain_factory_primary")),
      Protocol.empty,
      this
    )
}
