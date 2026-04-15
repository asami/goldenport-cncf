package org.goldenport.cncf.config

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.xml.{Elem, Node}
import scala.xml.XML
import com.typesafe.config.{ConfigFactory, ConfigValueType}
import org.yaml.snakeyaml.Yaml
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.provisional.observation.Taxonomy
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
          RuntimeFileConfigLoader.configurationFileParseInvalid(path, e)
      }
    }

  private def _load(
    path: Path
  ): Configuration = {
    val name = path.getFileName.toString.toLowerCase
    if (name.endsWith(".yaml") || name.endsWith(".yml"))
      _load_yaml(path)
    else if (name.endsWith(".xml"))
      _load_xml(path)
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
      case other =>
        _map_value(other) match {
          case obj: ConfigurationValue.ObjectValue =>
            Configuration(obj.values ++ _flatten_object(obj))
          case _ =>
            throw new IllegalArgumentException("runtime configuration root must be an object")
        }
      }
    }

  private def _load_xml(
    path: Path
  ): Configuration = {
    val elem = XML.loadFile(path.toFile)
    val obj = ConfigurationValue.ObjectValue(_xml_children(elem))
    Configuration(obj.values ++ _flatten_object(obj))
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

  private def _xml_children(
    node: Node
  ): Map[String, ConfigurationValue] =
    node.child.collect { case e: Elem => e }.groupBy(_.label).map {
      case (label, children) => label -> _xml_value(children.toVector)
    }

  private def _xml_value(
    children: Seq[Elem]
  ): ConfigurationValue =
    children.toList match {
      case one :: Nil => _xml_value(one)
      case many => ConfigurationValue.ListValue(many.map(_xml_value))
    }

  private def _xml_value(
    node: Elem
  ): ConfigurationValue = {
    val elements = node.child.collect { case e: Elem => e }
    if (elements.nonEmpty)
      ConfigurationValue.ObjectValue(_xml_children(node))
    else
      ConfigurationValue.StringValue(node.text.trim)
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

  private def _flatten_object(
    value: ConfigurationValue.ObjectValue
  ): Map[String, ConfigurationValue] =
    value.values.toVector.flatMap {
      case (key, obj: ConfigurationValue.ObjectValue) => _flatten_object(key, obj)
      case _ => Vector.empty
    }.toMap

  private def _flatten_object(
    prefix: String,
    value: ConfigurationValue.ObjectValue
  ): Vector[(String, ConfigurationValue)] =
    value.values.toVector.flatMap {
      case (key, obj: ConfigurationValue.ObjectValue) =>
        val path = s"$prefix.$key"
        (path -> obj) +: _flatten_object(path, obj)
      case (key, v) =>
        Vector(s"$prefix.$key" -> v)
    }
}

object RuntimeFileConfigLoader {
  def configurationFileParseInvalid[A](
    path: Path,
    cause: Throwable
  ): Consequence.Failure[A] = {
    val filetype = fileType(path)
    Consequence.fail(
      Taxonomy.resourceInvalid,
      cause,
      Seq(
        Descriptor.Facet.Message(s"configuration file parse failed: ${path}: ${cause.getMessage}"),
        Descriptor.Facet.Resource(Descriptor.Facet.Resource.Kind.File, path.toUri),
        Descriptor.Facet.Properties(Map(
          "fileType" -> filetype,
          "path" -> path.toString,
          "cause" -> Option(cause.getMessage).getOrElse(cause.getClass.getName)
        ))
      )
    )
  }

  def fileType(path: Path): String = {
    val name = path.getFileName.toString
    val i = name.lastIndexOf('.')
    if (i >= 0 && i + 1 < name.length)
      name.substring(i + 1).toLowerCase
    else
      "unknown"
  }
}
