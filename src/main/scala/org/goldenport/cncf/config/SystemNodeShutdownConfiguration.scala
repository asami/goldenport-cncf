package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationBindingCollection

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class SystemNodeShutdownConfiguration private (
  val drainTimeoutMillis: Long,
  val source: SystemNodeShutdownConfiguration.Source
) {
  private[cncf] def observationMessage: String =
    s"system-node shutdown drain source=$source millis=$drainTimeoutMillis"
}

object SystemNodeShutdownConfiguration {
  val DEFAULT_DRAIN_TIMEOUT_MILLIS: Long = 30000L
  val MAXIMUM_DRAIN_TIMEOUT_MILLIS: Long = 300000L

  enum Source {
    case Default
    case Resolved
    case Override
  }

  def default: SystemNodeShutdownConfiguration =
    new SystemNodeShutdownConfiguration(DEFAULT_DRAIN_TIMEOUT_MILLIS, Source.Default)

  def overrideC(value: Long): Consequence[SystemNodeShutdownConfiguration] =
    _create(value, Source.Override)

  def from(
    collection: ConfigurationBindingCollection[CncfConfigurationTarget],
    overrideMillis: Option[Long] = None
  ): Consequence[SystemNodeShutdownConfiguration] =
    if (collection == null || overrideMillis == null)
      Consequence.configurationInvalid("SystemNode shutdown configuration is invalid")
    else
      overrideMillis match {
        case Some(value) => overrideC(value)
        case None =>
          collection.binding(CncfConfigurationParameterCatalog.systemNodeShutdownDrainTimeoutMillis).flatMap {
            case Some(binding) => _create(binding.value, Source.Resolved)
            case None => Consequence.success(default)
          }
      }

  private def _create(value: Long, source: Source): Consequence[SystemNodeShutdownConfiguration] =
    if (value >= 1L && value <= MAXIMUM_DRAIN_TIMEOUT_MILLIS)
      Consequence.success(new SystemNodeShutdownConfiguration(value, source))
    else
      Consequence.configurationInvalid("textus.system-node.shutdown.drain-timeout-millis requires an integer in 1..300000")
}
