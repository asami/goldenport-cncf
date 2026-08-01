package org.goldenport.cncf.config

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.configuration.source.file.ConfigTextDecoder

/*
 * Standalone-only current-user profile document. This is an admission
 * contract, not an effective configuration resolver.
 *
 * @since   Jul. 31, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
object StandaloneUserProfile {
  val API_VERSION: String = "textus/v1"
  val KIND: String = "StandaloneUserProfile"

  final case class User(
    id: Option[String] = None,
    displayName: Option[String] = None,
    locale: Option[String] = None,
    timezone: Option[String] = None
  )

  final case class Document(
    user: Option[User] = None,
    subsystems: Map[String, User] = Map.empty
  )

  private[config] def load(path: Path): Consequence[Option[Document]] =
    if (!Files.isRegularFile(path))
      Consequence.success(None)
    else
      try {
        ConfigTextDecoder.decode(path, Files.readString(path, StandardCharsets.UTF_8)).flatMap { configuration =>
          parse(configuration, path.toString).map(Some(_))
        }
      } catch {
        case e: Exception => Consequence.configurationInvalid(s"StandaloneUserProfile cannot be read: $path: ${e.getMessage}")
      }

  def parse(
    configuration: Configuration,
    source: String = "StandaloneUserProfile"
  ): Consequence[Document] = {
    val values = configuration.values
    val unknown = values.keySet -- Set("apiVersion", "kind", "user", "subsystems")
    if (unknown.nonEmpty)
      _invalid(source, s"contains unknown field(s): ${unknown.toVector.sorted.mkString(", ")}")
    else
      for {
        apiversion <- _required_string(values, "apiVersion", source)
        _ <- if (apiversion == API_VERSION) Consequence.success(()) else _invalid(source, s"requires apiVersion '$API_VERSION'")
        kind <- _required_string(values, "kind", source)
        _ <- if (kind == KIND) Consequence.success(()) else _invalid(source, s"requires kind '$KIND'")
        user <- _user(values.get("user"), s"$source.user")
        subsystems <- _subsystems(values.get("subsystems"), source)
      } yield Document(user, subsystems)
  }

  private def _subsystems(
    value: Option[ConfigurationValue],
    source: String
  ): Consequence[Map[String, User]] =
    value match {
      case None => Consequence.success(Map.empty)
      case Some(ConfigurationValue.ObjectValue(values)) =>
        values.toVector.sortBy(_._1).foldLeft(Consequence.success(Map.empty[String, User])) { case (z, (name, profile)) =>
          z.flatMap { result =>
            val normalized = name.trim
            if (normalized.isEmpty)
              _invalid(source, "subsystems must not contain an empty identity")
            else
              _subsystem_user(profile, s"$source.subsystems.$normalized").map { user =>
                result.updated(normalized, user)
              }
          }
        }
      case Some(_) => _invalid(source, "subsystems must be an object")
    }

  private def _subsystem_user(
    value: ConfigurationValue,
    source: String
  ): Consequence[User] =
    value match {
      case ConfigurationValue.ObjectValue(values) =>
        val unknown = values.keySet -- Set("user")
        if (unknown.nonEmpty)
          _invalid(source, s"contains unknown field(s): ${unknown.toVector.sorted.mkString(", ")}")
        else
          _user(values.get("user"), s"$source.user").flatMap(_.map(Consequence.success).getOrElse(_invalid(source, "requires user")))
      case _ => _invalid(source, "must be an object")
    }

  private def _user(
    value: Option[ConfigurationValue],
    source: String
  ): Consequence[Option[User]] =
    value match {
      case None => Consequence.success(None)
      case Some(ConfigurationValue.ObjectValue(values)) =>
        val unknown = values.keySet -- Set("id", "displayName", "locale", "timezone")
        if (unknown.nonEmpty)
          _invalid(source, s"contains unknown field(s): ${unknown.toVector.sorted.mkString(", ")}")
        else
          for {
            id <- _optional_string(values, "id", source)
            displayname <- _optional_string(values, "displayName", source)
            locale <- _optional_string(values, "locale", source)
            timezone <- _optional_string(values, "timezone", source)
          } yield Some(User(id, displayname, locale, timezone))
      case Some(_) => _invalid(source, "must be an object")
    }

  private def _required_string(
    values: Map[String, ConfigurationValue],
    key: String,
    source: String
  ): Consequence[String] =
    _optional_string(values, key, source).flatMap(_.map(Consequence.success).getOrElse(_invalid(source, s"requires '$key'")))

  private def _optional_string(
    values: Map[String, ConfigurationValue],
    key: String,
    source: String
  ): Consequence[Option[String]] =
    values.get(key) match {
      case None => Consequence.success(None)
      case Some(ConfigurationValue.StringValue(value)) if value.trim.nonEmpty => Consequence.success(Some(value.trim))
      case Some(ConfigurationValue.StringValue(_)) => _invalid(source, s"'$key' must not be empty")
      case Some(_) => _invalid(source, s"'$key' must be a string")
    }

  private def _invalid[A](source: String, message: String): Consequence[A] =
    Consequence.configurationInvalid(s"StandaloneUserProfile $source $message")
}
