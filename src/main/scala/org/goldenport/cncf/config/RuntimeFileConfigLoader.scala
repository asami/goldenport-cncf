package org.goldenport.cncf.config

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.typesafe.config.{ConfigFactory, ConfigValueType}
import org.yaml.snakeyaml.Yaml
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.configuration.source.file.FileConfigLoader

/*
 * @since   Apr. 15, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeFileConfigLoader extends FileConfigLoader {
  override def load(
    path: Path
  ): Consequence[Configuration] =
    if (!Files.exists(path)) {
      Consequence.success(Configuration.empty)
    } else {
      try {
        Consequence.success(_load(path))
      } catch {
        case e: Exception =>
          Consequence.resourceInvalid(s"configuration file parse failed: ${path}: ${e.getMessage}")
      }
    }

  private def _load(
    path: Path
  ): Configuration = {
    val name = path.getFileName.toString.toLowerCase
    if (name.endsWith(".yaml") || name.endsWith(".yml"))
      _load_yaml(path)
    else
      _load_hocon_or_properties(path)
  }

  private def _load_yaml(
    path: Path
  ): Configuration = {
    val yaml = new Yaml()
    val loaded = yaml.load[Any](Files.readString(path))
    loaded match {
      case null => Configuration.empty
      case other => Configuration(_map_value(other).asInstanceOf[ConfigurationValue.ObjectValue].values)
    }
  }

  private def _load_hocon_or_properties(
    path: Path
  ): Configuration = {
    val config = ConfigFactory.parseFile(path.toFile).resolve()
    val values = config.entrySet().asScala.toVector.map { entry =>
      entry.getKey -> _typesafe_value(entry.getValue)
    }.toMap
    Configuration(values)
  }

  private def _map_value(
    value: Any
  ): ConfigurationValue =
    value match {
      case null => ConfigurationValue.NullValue
      case s: String => ConfigurationValue.StringValue(s)
      case b: java.lang.Boolean => ConfigurationValue.BooleanValue(b.booleanValue)
      case i: java.lang.Integer => ConfigurationValue.NumberValue(BigDecimal(i.intValue))
      case l: java.lang.Long => ConfigurationValue.NumberValue(BigDecimal(l.longValue))
      case d: java.lang.Double => ConfigurationValue.NumberValue(BigDecimal(d.doubleValue))
      case f: java.lang.Float => ConfigurationValue.NumberValue(BigDecimal(f.doubleValue))
      case n: java.lang.Number => ConfigurationValue.NumberValue(BigDecimal(n.toString))
      case xs: java.util.List[?] => ConfigurationValue.ListValue(xs.asScala.toList.map(_map_value))
      case xs: Seq[?] => ConfigurationValue.ListValue(xs.toList.map(_map_value))
      case m: java.util.Map[?, ?] =>
        ConfigurationValue.ObjectValue(m.asScala.toMap.map {
          case (k, v) => k.toString -> _map_value(v)
        })
      case m: Map[?, ?] =>
        ConfigurationValue.ObjectValue(m.map {
          case (k, v) => k.toString -> _map_value(v)
        })
      case other => ConfigurationValue.StringValue(other.toString)
    }

  private def _typesafe_value(
    value: com.typesafe.config.ConfigValue
  ): ConfigurationValue =
    value.valueType() match {
      case ConfigValueType.NULL => ConfigurationValue.NullValue
      case ConfigValueType.BOOLEAN => ConfigurationValue.BooleanValue(value.unwrapped().asInstanceOf[Boolean])
      case ConfigValueType.NUMBER => ConfigurationValue.NumberValue(BigDecimal(value.unwrapped().toString))
      case ConfigValueType.STRING => ConfigurationValue.StringValue(value.unwrapped().toString)
      case ConfigValueType.LIST =>
        ConfigurationValue.ListValue(value.unwrapped().asInstanceOf[java.util.List[?]].asScala.toList.map(_map_value))
      case ConfigValueType.OBJECT =>
        ConfigurationValue.ObjectValue(value.unwrapped().asInstanceOf[java.util.Map[?, ?]].asScala.toMap.map {
          case (k, v) => k.toString -> _map_value(v)
        })
    }
}
