package org.goldenport.cncf.adapter

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class DefaultAdapterResolver[A](
  registry: AdapterRegistry[A]
) extends AdapterResolver[A] {
  def resolve(
    key: AdapterKey,
    context: AdapterContext = AdapterContext()
  ): Either[AdapterResolutionError, A] = {
    val matches = registry.factories.filter(_.supports(key, context))
    matches match {
      case Vector() =>
        Left(AdapterNotFound(key))
      case Vector(factory) =>
        Right(factory.create(context))
      case multiple =>
        Left(
          AmbiguousAdapterResolution(
            key,
            multiple.map(_.key.value)
          )
        )
    }
  }
}
