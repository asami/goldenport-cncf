package org.goldenport.cncf.component.repository.fixture.impl

import org.goldenport.cncf.component.*
import org.goldenport.protocol.Protocol

/*
 * @since   Apr. 23, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class _ImplBackedComponent extends Component

object ComponentFactory extends Component.BundleFactory {
  object PrimaryFactory extends Component.PrimaryComponentFactory {
    protected def create_Component(params: ComponentCreate): Component =
      new _ImplBackedComponent

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      Component.Core.create(
        "impl-backed-primary",
        ComponentId("impl_backed_primary"),
        ComponentInstanceId.default(ComponentId("impl_backed_primary")),
        Protocol.empty,
        this
      )
  }

  def primaryFactory: Component.PrimaryComponentFactory =
    PrimaryFactory
}
