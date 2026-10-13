package org.goldenport.cncf.component.capability

import io.circe.jawn.JawnParser
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityPackageSource.*

/*
 * Strict canonical index grammar and complete JSON inventory.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityPackageIndexPart {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _filename = "[A-Za-z0-9][A-Za-z0-9._-]*\\.json".r

  def decode(json: String): Either[Diagnostic, Vector[String]] = {
    for {
      text <- _nonnull(json, "/index")
      parsed <- _parser.parse(text).left.map(_ => _malformed("/index", "Invalid index JSON or duplicate key."))
      obj <- _object(parsed, Vector("schemaVersion", "projections"), "/index")
      version <- _read(obj, "schemaVersion", "/index")(_integer)
      _ <- Either.cond(version == indexVersion, (), _diagnostic(DiagnosticKind.Unsupported, "Unsupported package index version.", "/index/schemaVersion"))
      names <- _read(obj, "projections", "/index") { (value, path) =>
        _vector(value, path) { (item, location) =>
          _string(item, location).flatMap { name =>
            Either.cond(_filename.matches(name) && name != "index.json", name,
              _malformed(location, "A flat literal projection JSON basename is required."))
          }
        }
      }
      _ <- Either.cond(names.size <= 256, (), _malformed("/index/projections", "The projection count exceeds 256."))
      _ <- names.indices.find(index => names.take(index).contains(names(index))) match {
        case Some(index) => Left(_diagnostic(DiagnosticKind.Ambiguous, "Repeated projection filename.", s"/index/projections/$index"))
        case None => Right(())
      }
    } yield names
  }

  def validateInventory(index: Vector[String], resources: Vector[String]): Either[Diagnostic, Unit] = {
    val prefix = metadataDirectory + "/"
    val jsons = resources.filter(name => name.startsWith(prefix) && name.endsWith(".json") && name != indexPath)
    jsons.find(name => !index.contains(name.stripPrefix(prefix))) match {
      case Some(name) => Left(_malformed("/" + name, "Unlisted or nested metadata JSON resource."))
      case None => Right(())
    }
  }
}
