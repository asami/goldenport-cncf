package org.goldenport.cncf.http

import java.nio.file.Paths

import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Apr. 14, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object WebDescriptorResolver {
  def resolve(
    subsystem: Subsystem
  ): Consequence[WebDescriptor] =
    RuntimeConfig.getString(subsystem.configuration, RuntimeConfig.WebDescriptorKey) match {
      case Some(path) => WebDescriptor.load(Paths.get(path))
      case None =>
        subsystem.descriptor match {
          case Some(descriptor) =>
            WebDescriptor.load(descriptor.path) match {
              case Consequence.Success(value) => Consequence.success(value)
              case Consequence.Failure(_) => Consequence.success(WebDescriptor.empty)
            }
          case None => Consequence.success(WebDescriptor.empty)
        }
    }

  def resolve(
    configuration: ResolvedConfiguration
  ): Consequence[WebDescriptor] =
    RuntimeConfig.getString(configuration, RuntimeConfig.WebDescriptorKey) match {
      case Some(path) => WebDescriptor.load(Paths.get(path))
      case None => Consequence.success(WebDescriptor.empty)
    }
}
