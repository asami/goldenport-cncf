package org.goldenport.cncf.component.repository.fixture.discovery

import org.goldenport.cncf.component.Component

/**
 * Selection must inspect constructor metadata without running any fixture.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class AParameterizedFactory(dependency: String) extends Component.BundleFactory {
  throw new IllegalStateException("parameterized factory must not be constructed by discovery selection")
  def primaryFactory: Component.PrimaryComponentFactory =
    throw new IllegalStateException("selection must not request the primary factory")
}

final class BPrivateConstructorFactory private () extends Component.BundleFactory {
  throw new IllegalStateException("private constructor must not be bypassed")
  def primaryFactory: Component.PrimaryComponentFactory =
    throw new IllegalStateException("selection must not request the primary factory")
}

abstract class CAbstractFactory extends Component.BundleFactory
trait DFactoryInterface extends Component.BundleFactory

object EModuleFactory extends Component.BundleFactory {
  throw new IllegalStateException("selection must not initialize a module")
  def primaryFactory: Component.PrimaryComponentFactory =
    throw new IllegalStateException("selection must not request the primary factory")
}

final class ZLauncherFactory extends Component.BundleFactory {
  throw new IllegalStateException("selection must not construct even a valid launcher factory")
  def primaryFactory: Component.PrimaryComponentFactory =
    throw new IllegalStateException("selection must not request the primary factory")
}
