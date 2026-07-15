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
 * @version Jul. 15, 2026
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
    val config =
      _derived_config(record) ++ _config(record)
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

  private def _derived_config(record: Record): Map[String, String] =
    _home_config(record) ++ _execution_config(record) ++ _runtime_config(record) ++ _component_config(record)

  private def _execution_config(record: Record): Map[String, String] =
    record.getAny("execution").flatMap(_record).map { execution =>
      val direct = Vector(
        _string(execution, "profile").map(RuntimeConfig.EXECUTION_PROFILE_KEY -> _),
        _string(execution, "key").map(RuntimeConfig.EXECUTION_KEY -> _)
      ).flatten.toMap
      direct ++
        _execution_time_section(execution) ++
        _execution_section(execution, "random", Vector(
          "mode" -> RuntimeConfig.EXECUTION_RANDOM_MODE_KEY,
          "seed" -> RuntimeConfig.EXECUTION_RANDOM_SEED_KEY
        )) ++
        _execution_section(execution, "ids", Vector(
          "mode" -> RuntimeConfig.EXECUTION_IDS_MODE_KEY
        )) ++
        _execution_section(execution, "scheduler", Vector(
          "mode" -> RuntimeConfig.EXECUTION_SCHEDULER_MODE_KEY
        )) ++
        _execution_section(execution, "ordering", Vector(
          "mode" -> RuntimeConfig.EXECUTION_ORDERING_MODE_KEY
        )) ++
        _execution_assumptions(execution)
    }.getOrElse(Map.empty)

  private def _execution_assumptions(execution: Record): Map[String, String] =
    execution.getAny("assumptions").flatMap(_record).map { assumptions =>
      val direct = Vector(
        _string(assumptions, "locale").map(RuntimeConfig.EXECUTION_LOCALE_KEY -> _),
        _string(assumptions, "timezone").map(RuntimeConfig.EXECUTION_TIMEZONE_KEY -> _),
        _string(assumptions, "charset").map(RuntimeConfig.EXECUTION_CHARSET_KEY -> _),
        _string(assumptions, "line-separator").orElse(_string(assumptions, "lineSeparator")).map(RuntimeConfig.EXECUTION_LINE_SEPARATOR_KEY -> _),
        _string(assumptions, "math-context").orElse(_string(assumptions, "mathContext")).map(RuntimeConfig.EXECUTION_MATH_CONTEXT_KEY -> _)
      ).flatten.toMap
      direct ++ _execution_i18n(assumptions) ++ _execution_environment(assumptions)
    }.getOrElse(Map.empty)

  private def _execution_i18n(assumptions: Record): Map[String, String] =
    assumptions.getAny("i18n").flatMap(_record).map { i18n =>
      Vector(
        _string(i18n, "text-normalization-policy").orElse(_string(i18n, "textNormalizationPolicy")).map(RuntimeConfig.EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY -> _),
        _string(i18n, "text-comparison-policy").orElse(_string(i18n, "textComparisonPolicy")).map(RuntimeConfig.EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY -> _),
        _string(i18n, "date-time-format-policy").orElse(_string(i18n, "dateTimeFormatPolicy")).map(RuntimeConfig.EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY -> _)
      ).flatten.toMap
    }.getOrElse(Map.empty)

  private def _execution_environment(assumptions: Record): Map[String, String] =
    assumptions.getAny("environment").flatMap(_record).map { environment =>
      val allow = _string_vector(environment, "allow")
      val allowconfig = Option.when(allow.nonEmpty)(RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY -> allow.mkString(",")).toMap
      val values = environment.getAny("values").flatMap(_record).map(_.asMap.flatMap { case (name, value) =>
        Option(value).map(x => s"${RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY}.${name}" -> x.toString)
      }).getOrElse(Map.empty)
      allowconfig ++ values
    }.getOrElse(Map.empty)

  private def _execution_time_section(execution: Record): Map[String, String] =
    execution.getAny("time").flatMap(_record).map { value =>
      Vector(
        _string(value, "mode").map(RuntimeConfig.EXECUTION_TIME_MODE_KEY -> _),
        _string(value, "start-at")
          .orElse(_string(value, "startAt"))
          .map(RuntimeConfig.EXECUTION_TIME_START_AT_KEY -> _)
      ).flatten.toMap
    }.getOrElse(Map.empty)

  private def _execution_section(
    execution: Record,
    section: String,
    keys: Vector[(String, String)]
  ): Map[String, String] =
    execution.getAny(section).flatMap(_record).map { value =>
      keys.flatMap { case (source, target) =>
        _string(value, source).map(target -> _)
      }.toMap
    }.getOrElse(Map.empty)

  private def _home_config(record: Record): Map[String, String] =
    record.getAny("home").flatMap(_record).map { home =>
      val base = Vector(
        _string(home, "mode").map(RuntimeConfig.TEST_HOME_MODE_KEY -> _),
        _string(home, "path").map(RuntimeConfig.TEST_HOME_PATH_KEY -> _)
      ).flatten.toMap
      val temporary = _string(home, "temporary").map(RuntimeConfig.TEST_HOME_TEMPORARY_KEY -> _).toMap
      val inherit =
        home.getAny("inherit").flatMap(_record).map { inherit =>
          Vector(
            _string(inherit, "runtime").map(RuntimeConfig.TEST_HOME_INHERIT_RUNTIME_KEY -> _),
            _string(inherit, "repositories").map(RuntimeConfig.TEST_HOME_INHERIT_REPOSITORIES_KEY -> _),
            _string(inherit, "credentials").map(RuntimeConfig.TEST_HOME_INHERIT_CREDENTIALS_KEY -> _),
            _string(inherit, "local-data").orElse(_string(inherit, "localData")).map(RuntimeConfig.TEST_HOME_INHERIT_LOCAL_DATA_KEY -> _)
          ).flatten.toMap
        }.getOrElse(Map.empty)
      base ++ temporary ++ inherit
    }.getOrElse(Map.empty)

  private def _runtime_config(record: Record): Map[String, String] =
    record.getAny("runtime").flatMap(_record).flatMap(_.getAny("datastore")).flatMap(_record).map { datastore =>
      _datastore_config("textus.datastore", datastore)
    }.getOrElse(Map.empty)

  private def _component_config(record: Record): Map[String, String] =
    record.getAny("components").flatMap(_record).map { components =>
      components.asMap.iterator.flatMap { case (component, value) =>
        _record(value).toVector.flatMap { componentrecord =>
          val datastores =
            componentrecord.getAny("datastores")
              .orElse(componentrecord.getAny("datastore"))
              .flatMap(_record)
              .toVector
          datastores.flatMap { datastorerecord =>
            _component_datastore_config(component, datastorerecord)
          }
        }
      }.toMap
    }.getOrElse(Map.empty)

  private def _component_datastore_config(
    component: String,
    datastores: Record
  ): Map[String, String] =
    datastores.asMap.iterator.flatMap { case (name, value) =>
      _record(value).toVector.flatMap { datastore =>
        _datastore_config(s"textus.component.${component}.datastores.${name}", datastore)
      }
    }.toMap

  private def _datastore_config(
    prefix: String,
    datastore: Record
  ): Map[String, String] =
    Vector(
      _string(datastore, "type").orElse(_string(datastore, "kind")).map(prefix + ".kind" -> _),
      _string(datastore, "path").map(prefix + ".path" -> _),
      _string(datastore, "policy").map(prefix + ".policy" -> _),
      _string(datastore, "jdbcUrl").orElse(_string(datastore, "jdbc-url")).map(prefix + ".jdbc.url" -> _),
      _string(datastore, "user").map(prefix + ".jdbc.user" -> _),
      _string(datastore, "password").map(prefix + ".jdbc.password" -> _)
    ).flatten.toMap

  private def _string(
    record: Record,
    key: String
  ): Option[String] =
    record.getAny(key).map {
      case value: java.util.Date => value.toInstant.toString
      case value => value.toString
    }.map(_.trim).filter(_.nonEmpty)

  private def _string_vector(
    record: Record,
    key: String
  ): Vector[String] =
    record.getAny(key).toVector.flatMap {
      case values: Seq[?] => values.toVector.map(_.toString)
      case values: Array[?] => values.toVector.map(_.toString)
      case value => value.toString.split("[,|\\s]+").toVector
    }.map(_.trim).filter(_.nonEmpty)

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
