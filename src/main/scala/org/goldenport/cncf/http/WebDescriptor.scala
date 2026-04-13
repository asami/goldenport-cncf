package org.goldenport.cncf.http

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.cncf.component.DescriptorRecordLoader
import org.goldenport.record.Record

/*
 * @since   Apr. 14, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WebDescriptor(
  expose: Map[String, WebDescriptor.Exposure] = Map.empty,
  auth: WebDescriptor.Auth = WebDescriptor.Auth(),
  authorization: Map[String, WebDescriptor.Authorization] = Map.empty,
  form: Map[String, WebDescriptor.Form] = Map.empty,
  apps: Vector[WebDescriptor.App] = Vector.empty
) {
  def hasControls: Boolean =
    expose.nonEmpty ||
      authorization.nonEmpty ||
      form.nonEmpty ||
      apps.nonEmpty ||
      auth != WebDescriptor.Auth()

  def exposureOf(selector: String): WebDescriptor.Exposure =
    expose.getOrElse(selector, WebDescriptor.Exposure.Internal)

  def isFormEnabled(selector: String): Boolean =
    form.get(selector).flatMap(_.enabled) match {
      case Some(value) => value
      case None =>
        if (!hasControls)
          true
        else
          exposureOf(selector) != WebDescriptor.Exposure.Internal
    }

  def isAppEnabled(name: String, path: Vector[String] = Vector.empty): Boolean =
    if (apps.isEmpty)
      true
    else
      apps.exists(_.matches(name, path))
}

object WebDescriptor {
  enum Exposure {
    case Internal
    case Protected
    case Public

    def name: String =
      this match {
        case Internal => "internal"
        case Protected => "protected"
        case Public => "public"
      }
  }

  object Exposure {
    def parse(value: String): Option[Exposure] =
      value.trim.toLowerCase match {
        case "internal" => Some(Internal)
        case "protected" => Some(Protected)
        case "public" => Some(Public)
        case _ => None
      }
  }

  final case class Auth(
    mode: String = "none"
  )

  final case class Authorization(
    roles: Vector[String] = Vector.empty,
    scopes: Vector[String] = Vector.empty,
    capabilities: Vector[String] = Vector.empty
  )

  final case class Form(
    enabled: Option[Boolean] = None
  )

  final case class App(
    name: String,
    path: String,
    kind: String
  ) {
    def matches(requestName: String, requestPath: Vector[String]): Boolean = {
      val normalizedRequestName = _normalize_app_segment(requestName)
      val normalizedRequestPath = requestPath.map(_normalize_app_segment)
      val appPath = path.split("/").toVector.filter(_.nonEmpty).map(_normalize_app_segment)
      _normalize_app_segment(name) == normalizedRequestName ||
        appPath == ("web" +: normalizedRequestName +: normalizedRequestPath) ||
        appPath == ("web" +: normalizedRequestName +: Vector(_normalize_app_segment(kind)))
    }
  }

  val empty: WebDescriptor = WebDescriptor()

  def load(path: Path): Consequence[WebDescriptor] =
    if (!Files.exists(path))
      Consequence.resourceNotFound(s"web descriptor path does not exist: ${path}")
    else if (Files.isDirectory(path)) {
      val file = path.resolve("web").resolve("web.yaml")
      if (Files.isRegularFile(file))
        load(file)
      else
        Consequence.resourceNotFound(s"web descriptor not found: ${file}")
    } else if (_is_archive_file(path)) {
      _load_archive_file(path)
    } else {
      DescriptorRecordLoader.load(path).flatMap { records =>
        records.headOption match {
          case Some(record) => Consequence.success(fromRecord(record))
          case None => Consequence.resourceInvalid(s"web descriptor is empty: ${path}")
        }
      }
    }

  def fromRecord(record: Record): WebDescriptor = {
    val web = _record_value(record, "web").getOrElse(record)
    WebDescriptor(
      expose = _expose(web),
      auth = _auth(web),
      authorization = _authorization(web),
      form = _form(web),
      apps = _apps(web)
    )
  }

  private def _expose(record: Record): Map[String, Exposure] =
    _record_value(record, "expose")
      .map(_.asMap.toVector.flatMap {
        case (key, value) => Exposure.parse(value.toString).map(key -> _)
      }.toMap)
      .getOrElse(Map.empty)

  private def _auth(record: Record): Auth =
    Auth(
      mode = _record_value(record, "auth").flatMap(_.getString("mode")).getOrElse("none")
    )

  private def _authorization(record: Record): Map[String, Authorization] =
    _record_value(record, "authorization")
      .map(_.asMap.toVector.flatMap {
        case (key, value) =>
          _any_to_record(value).map { r =>
            key -> Authorization(
              roles = _string_vector(r, "roles"),
              scopes = _string_vector(r, "scopes"),
              capabilities = _string_vector(r, "capabilities")
            )
          }
      }.toMap)
      .getOrElse(Map.empty)

  private def _form(record: Record): Map[String, Form] =
    _record_value(record, "form")
      .map(_.asMap.toVector.flatMap {
        case (key, value) =>
          _any_to_record(value).map { r =>
            key -> Form(enabled = _boolean(r, "enabled"))
          }
      }.toMap)
      .getOrElse(Map.empty)

  private def _apps(record: Record): Vector[App] =
    record.getAny("apps") match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_any_to_record).flatMap(_app)
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.flatMap(_any_to_record).flatMap(_app)
      case _ => Vector.empty
    }

  private def _app(record: Record): Option[App] =
    for {
      name <- record.getString("name").map(_.trim).filter(_.nonEmpty)
      path <- record.getString("path").map(_.trim).filter(_.nonEmpty)
      kind <- record.getString("kind").map(_.trim).filter(_.nonEmpty)
    } yield App(name, path, kind)

  private def _record_value(record: Record, key: String): Option[Record] =
    record.getAny(key).flatMap(_any_to_record)

  private def _any_to_record(value: Any): Option[Record] =
    value match {
      case r: Record => Some(r)
      case m: Map[?, ?] => Some(_map_to_record(m))
      case m: java.util.Map[?, ?] => Some(_map_to_record(m.asScala.toMap))
      case _ => None
    }

  private def _map_to_record(m: collection.Map[?, ?]): Record =
    Record.create(m.iterator.map { case (k, v) => k.toString -> _record_value_any(v) }.toSeq)

  private def _record_value_any(value: Any): Any =
    value match {
      case r: Record => r
      case m: Map[?, ?] => _map_to_record(m)
      case m: java.util.Map[?, ?] => _map_to_record(m.asScala.toMap)
      case xs: Seq[?] => xs.toVector.map(_record_value_any)
      case xs: java.util.List[?] => xs.asScala.toVector.map(_record_value_any)
      case other => other
    }

  private def _string_vector(record: Record, key: String): Vector[String] =
    record.getAny(key) match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString.trim).filter(_.nonEmpty)
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.map(_.toString.trim).filter(_.nonEmpty)
      case Some(s: String) => s.split("[,|\\s]+").toVector.map(_.trim).filter(_.nonEmpty)
      case Some(other) => Vector(other.toString.trim).filter(_.nonEmpty)
      case None => Vector.empty
    }

  private def _boolean(record: Record, key: String): Option[Boolean] =
    record.getString(key).flatMap { value =>
      value.trim.toLowerCase match {
        case "true" | "yes" | "on" | "1" => Some(true)
        case "false" | "no" | "off" | "0" => Some(false)
        case _ => None
      }
    }

  private def _normalize_app_segment(value: String): String =
    value.trim.toLowerCase.replace("_", "-")

  private def _is_archive_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase
    name.endsWith(".sar") || name.endsWith(".car") || name.endsWith(".zip")
  }

  private def _load_archive_file(path: Path): Consequence[WebDescriptor] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      val file = fs.getPath("/").resolve("web").resolve("web.yaml")
      if (Files.isRegularFile(file))
        load(file)
      else
        Consequence.resourceNotFound(s"web descriptor not found in archive: ${path}")
    }
  }
}
