package org.goldenport.cncf.config

import org.goldenport.configuration.{ConfigurationOrigin, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Property, Switch}

/*
 * @since   Mar. 13, 2026
 * @version Mar. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResolvedParameter(
  key: String,
  value: ConfigurationValue,
  source: ResolvedParameter.Source
) {
  def display: String =
    s"${key}=${ResolvedParameter.format_value(value)}(source=${source.label})"
}

object ResolvedParameter {
  sealed trait Source {
    def label: String
  }
  object Source {
    final case class Configuration(origin: ConfigurationOrigin) extends Source {
      def label: String = ResolvedParameter.origin_label(origin)
    }
    final case class Operation(kind: String) extends Source {
      def label: String = s"operation:${kind}"
    }
    final case class Framework(kind: String) extends Source {
      def label: String = s"framework:${kind}"
    }
    final case class Component(kind: String) extends Source {
      def label: String = s"component:${kind}"
    }
    case object Unknown extends Source {
      def label: String = "unknown"
    }
  }

  def format_value(
    value: ConfigurationValue
  ): String =
    value match {
      case ConfigurationValue.StringValue(v) => v
      case ConfigurationValue.NumberValue(v) => v.toString
      case ConfigurationValue.BooleanValue(v) => v.toString
      case ConfigurationValue.ListValue(vs) => vs.map(format_value).mkString("[", ", ", "]")
      case ConfigurationValue.ObjectValue(vs) =>
        vs.map { case (k, v) => s"${k}=${format_value(v)}" }.mkString("{", ", ", "}")
      case ConfigurationValue.NullValue => "null"
    }

  def origin_label(
    origin: ConfigurationOrigin
  ): String =
    origin match {
      case ConfigurationOrigin.Arguments => "cli"
      case ConfigurationOrigin.Environment => "env"
      case ConfigurationOrigin.Default => "default"
      case ConfigurationOrigin.Home => "file"
      case ConfigurationOrigin.Project => "file"
      case ConfigurationOrigin.Cwd => "file"
      case ConfigurationOrigin.Resource => "resource"
    }
}

final class ResolvedParameters private (
  private val _entries: Map[String, ResolvedParameter],
  private val _parent: Option[ResolvedParameters],
  private val _used: scala.collection.mutable.LinkedHashMap[String, ResolvedParameter]
) {
  def get(key: String): Option[ResolvedParameter] =
    _entries.get(key) match {
      case Some(param) =>
        _mark_used(param)
        Some(param)
      case None =>
        val fromparent = _parent.flatMap(_.get(key))
        fromparent.foreach(_mark_used)
        fromparent
    }

  def usedEntries: Vector[ResolvedParameter] =
    _used.values.toVector

  def usedText: Option[String] = {
    val entries = usedEntries
    if (entries.isEmpty) None
    else Some(s"params=${entries.map(_.display).mkString(", ")}")
  }

  def markAllLocalUsed(): Unit =
    _entries.values.foreach(_mark_used)

  private def _mark_used(
    param: ResolvedParameter
  ): Unit =
    if (!_used.contains(param.key)) {
      _used.put(param.key, param)
    }
}

object ResolvedParameters {
  def empty(
    parent: Option[ResolvedParameters] = None
  ): ResolvedParameters =
    new ResolvedParameters(
      Map.empty,
      parent,
      scala.collection.mutable.LinkedHashMap.empty
    )

  def fromResolvedConfiguration(
    conf: ResolvedConfiguration
  ): ResolvedParameters = {
    val entries = conf.configuration.values.toVector.map { case (key, value) =>
      val source = conf.trace.get(key) match {
        case Some(r) => ResolvedParameter.Source.Configuration(r.origin)
        case None => ResolvedParameter.Source.Unknown
      }
      key -> ResolvedParameter(key, value, source)
    }.toMap
    new ResolvedParameters(
      entries,
      None,
      scala.collection.mutable.LinkedHashMap.empty
    )
  }

  def forOperation(
    arguments: List[Argument],
    switches: List[Switch],
    properties: List[Property],
    parent: Option[ResolvedParameters]
  ): ResolvedParameters = {
    val entries = Vector.newBuilder[(String, ResolvedParameter)]
    arguments.foreach { arg =>
      val key = arg.name
      val value = ConfigurationValue.StringValue(Option(arg.value).map(_.toString).getOrElse(""))
      val source = ResolvedParameter.Source.Operation("argument")
      entries += key -> ResolvedParameter(key, value, source)
    }
    switches.foreach { sw =>
      val key = sw.name
      val value = ConfigurationValue.StringValue(Option(sw.value).map(_.toString).getOrElse("true"))
      val source = ResolvedParameter.Source.Operation("switch")
      entries += key -> ResolvedParameter(key, value, source)
    }
    properties.foreach { prop =>
      val key = prop.name
      val value = ConfigurationValue.StringValue(Option(prop.value).map(_.toString).getOrElse(""))
      val source = ResolvedParameter.Source.Operation("property")
      entries += key -> ResolvedParameter(key, value, source)
    }
    val params =
      new ResolvedParameters(
        entries.result().toMap,
        parent,
        scala.collection.mutable.LinkedHashMap.empty
      )
    params.markAllLocalUsed()
    params
  }
}
