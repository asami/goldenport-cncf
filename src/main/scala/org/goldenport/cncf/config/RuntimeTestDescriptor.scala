package org.goldenport.cncf.config

import java.nio.file.{Path, Paths}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.configuration.source.ConfigurationSource
import org.goldenport.record.Record
import org.goldenport.cncf.component.DescriptorRecordLoader
import org.goldenport.cncf.subsystem.GenericSubsystemAssemblyDescriptorSource

/*
 * @since   Jul.  8, 2026
 * @version Jul.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuntimeTestDescriptor(
  path: Path,
  record: Record,
  config: Map[String, String],
  assembly: Option[GenericSubsystemAssemblyDescriptorSource]
)

object RuntimeTestDescriptor {
  def path(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.TEST_DESCRIPTOR_KEY)
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.RUNTIME_TEST_DESCRIPTOR_KEY))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  def load(
    path: Path
  ): Consequence[RuntimeTestDescriptor] =
    DescriptorRecordLoader.load(path).flatMap { records =>
      records.headOption match {
        case Some(record) =>
          Consequence.success(fromRecord(path, record))
        case None =>
          Consequence.resourceInvalid(s"test descriptor is empty: ${path}")
      }
    }

  def load(
    configuration: ResolvedConfiguration
  ): Consequence[Option[RuntimeTestDescriptor]] =
    path(configuration) match {
      case Some(path) => load(path).map(Some(_))
      case None => Consequence.success(None)
    }

  def configurationSource(
    descriptor: RuntimeTestDescriptor
  ): Option[ConfigurationSource] =
    if (descriptor.config.isEmpty) {
      None
    } else {
      ConfigurationSource.args(descriptor.config)
    }

  def fromRecord(
    path: Path,
    record: Record
  ): RuntimeTestDescriptor = {
    val config = _config(record)
    val assembly = record.getAny("assembly").flatMap(_record).map { assembly =>
      GenericSubsystemAssemblyDescriptorSource(
        record = assembly,
        source = "test",
        path = Some(path)
      )
    }
    RuntimeTestDescriptor(path, record, config, assembly)
  }

  private def _config(record: Record): Map[String, String] =
    record.getAny("config").flatMap(_record).map { config =>
      config.asMap.iterator.map { case (key, value) =>
        key -> Option(value).map(_.toString).getOrElse("")
      }.toMap
    }.getOrElse(Map.empty)

  private def _record(value: Any): Option[Record] =
    value match {
      case record: Record => Some(record)
      case map: Map[?, ?] =>
        Some(Record.create(map.iterator.map { case (key, value) => key.toString -> value }.toVector))
      case map: java.util.Map[?, ?] =>
        Some(Record.create(map.asScala.iterator.map { case (key, value) => key.toString -> value }.toVector))
      case _ => None
    }
}
