package org.goldenport.cncf.filestore

import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import cats.free.Free
import cats.syntax.all.*
import io.circe.{Json, JsonObject, parser}
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.bag.{Bag, BagBackend}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.datatype.MimeType
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.Property

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */
/** Create-only Drive storage over admitted native HTTP programs.
  * Callers establish owning entity/source authorization before using this store.
  */
final class GoogleDriveFileStore private (
  val name: String,
  private val _credentials: GoogleDriveCredentials
) extends FileStore {
  import GoogleDriveFileStore.*

  override def toString: String = s"GoogleDriveFileStore($name)"

  def stat(reference: FileStoreRef): ExecUowM[FileStoreMetadata] = for {
    _ <- _pure(_reference(reference))
    auth <- _credentials._authorization
    response <- _lift(UnitOfWorkOp.HttpGet(s"$_files/${reference.key}?supportsAllDrives=true&fields=$_fields",
      auth + ("Accept" -> "application/json"), _metadata_properties))
    metadata <- _pure(if (response.code == 200) _metadata(response, false).flatMap { value =>
      if (value.reference == reference) Consequence.success(value) else _invalid_metadata(false)
    } else _status(response.code, false))
  } yield metadata

  def read(reference: FileStoreRef): ExecUowM[FileStoreContent] = for {
    _ <- _pure(_reference(reference))
    auth <- _credentials._authorization
    response <- _lift(UnitOfWorkOp.HttpGet(s"$_files/${reference.key}?alt=media&supportsAllDrives=true",
      auth + ("Accept" -> "application/octet-stream"), Vector(Property("http.response-body-mode", "binary", None))))
    content <- _pure(if (response.code == 200) Consequence.success(FileStoreContent(response.mime, response.bag))
      else _status(response.code, false))
  } yield content

  def createCollection(parent: FileStoreRef, name: String): ExecUowM[FileStoreMetadata] = for {
    _ <- _pure(_reference(parent))
    _ <- _pure(_creation_input(_valid_name(name)))
    auth <- _credentials._authorization
    response <- _lift(UnitOfWorkOp.HttpPost(s"$_files?supportsAllDrives=true&fields=$_fields",
      Some(_creation_json(parent, name, _folder_mime)), auth ++ _json_headers, _metadata_properties))
    metadata <- _pure(if (Set(200, 201)(response.code))
      _confirmed_metadata(response, parent, name, _folder_mime, None, FileStoreKind.Collection)
      else _status(response.code, true))
  } yield metadata

  def create(parent: FileStoreRef, name: String, mediaType: MimeType, content: Bag): ExecUowM[FileStoreMetadata] = for {
    _ <- _pure(_reference(parent))
    _ <- _pure(_creation_input(_valid_name(name) && Option(mediaType).exists(value =>
      _valid_mime(value.value) && value.value != _folder_mime) && _valid_content(content)))
    auth <- _credentials._authorization
    initiation <- _lift(UnitOfWorkOp.HttpPost(s"$_upload?uploadType=resumable&supportsAllDrives=true&fields=$_fields",
      Some(_creation_json(parent, name, mediaType.value)), auth ++ _json_headers ++ Map(
        "X-Upload-Content-Type" -> mediaType.value, "X-Upload-Content-Length" -> content.metadata.size.get.toString),
      _metadata_properties))
    session <- _pure(if (initiation.code == 200) _session(initiation.headerValue("Location"))
      else _status[String](initiation.code, true))
    completion <- _lift(UnitOfWorkOp.HttpPutBag(session, Some(content),
      auth ++ Map("Content-Type" -> mediaType.value, "Accept" -> "application/json"), _metadata_properties))
    metadata <- _pure(if (Set(200, 201)(completion.code))
      _confirmed_metadata(completion, parent, name, mediaType.value, content.metadata.size, FileStoreKind.File)
      else _status(completion.code, true))
  } yield metadata

  private def _reference(reference: FileStoreRef): Consequence[Unit] = {
    if (Option(reference).exists(value => value.store == name && _valid_id(value.key))) Consequence.success(())
    else Consequence.argumentInvalid("Invalid Google Drive file reference")
  }

  private def _metadata(response: HttpResponse, writing: Boolean): Consequence[FileStoreMetadata] = {
    val parsed = try {
      if (!response.mime.value.equalsIgnoreCase("application/json")) None
      else response.getString.flatMap(text => parser.parse(text).toOption).flatMap(_.asObject).flatMap { fields =>
        for {
          id <- fields("id").flatMap(_.asString).filter(_valid_id)
          label <- fields("name").flatMap(_.asString).filter(_valid_label)
          mime <- fields("mimeType").flatMap(_.asString).filter(_valid_mime)
          _ <- Option.when(fields("trashed").flatMap(_.asBoolean).contains(false))(())
          parents <- _parents(fields)
          size <- _optional_decimal(fields, "size").flatMap {
            case None => Some(None)
            case Some(value) => value.toLongOption.filter(_ >= 0).map(Some(_))
          }
          version <- _optional_decimal(fields, "version")
        } yield FileStoreMetadata(FileStoreRef(name, id), label, MimeType(mime), size,
          parents.map(FileStoreRef(name, _)), if (mime == _folder_mime) FileStoreKind.Collection else FileStoreKind.File, version)
      }
    } catch { case NonFatal(_) => None }
    parsed.fold(_invalid_metadata(writing))(Consequence.success)
  }

  private def _confirmed_metadata(response: HttpResponse, parent: FileStoreRef, label: String,
    mime: String, size: Option[Long], kind: FileStoreKind): Consequence[FileStoreMetadata] = {
    _metadata(response, true).flatMap { metadata =>
      if (metadata.name == label && metadata.mediaType.value == mime && metadata.kind == kind &&
          metadata.parents.contains(parent) && size.forall(value => metadata.byteLength.contains(value)))
        Consequence.success(metadata)
      else _invalid_metadata(true)
    }
  }
}

object GoogleDriveFileStore {
  private val _files = "https://www.googleapis.com/drive/v3/files"
  private val _upload = "https://www.googleapis.com/upload/drive/v3/files"
  private val _fields = "id,name,mimeType,size,parents,trashed,version"
  private val _folder_mime = "application/vnd.google-apps.folder"
  private val _json_headers = Map("Content-Type" -> "application/json; charset=UTF-8", "Accept" -> "application/json")
  private val _metadata_properties = Vector(Property("http.max-response-bytes", "65536", None))

  def createC(name: String, credentials: GoogleDriveCredentials): Consequence[GoogleDriveFileStore] = {
    if (Option(name).exists(_.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) && Option(credentials).nonEmpty)
      Consequence.success(new GoogleDriveFileStore(name, credentials))
    else Consequence.argumentInvalid("Invalid Google Drive file store configuration")
  }

  private def _pure[A](value: Consequence[A]): ExecUowM[A] = ConsequenceT.fromConsequence(value)

  private def _lift(operation: UnitOfWorkOp[HttpResponse]): ExecUowM[HttpResponse] =
    ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, HttpResponse](operation))

  private def _valid_id(value: String): Boolean = Option(value).exists(_.matches("[A-Za-z0-9_-]{1,256}"))

  private def _valid_label(value: String): Boolean =
    Option(value).exists(text => text.nonEmpty && !text.exists(c => Character.isISOControl(c)))

  private def _valid_name(value: String): Boolean = _valid_label(value) && value.length <= 1024

  private def _valid_mime(value: String): Boolean =
    Option(value).exists(_.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+/[!#$%&'*+.^_`|~0-9A-Za-z-]+"))

  private def _valid_content(content: Bag): Boolean = Option(content).exists { value =>
    value.backend match {
      case BagBackend.Url(_) => false
      case _ => value.metadata.size.exists(_ >= 0)
    }
  }

  private def _creation_input(valid: Boolean): Consequence[Unit] =
    if (valid) Consequence.success(()) else Consequence.argumentInvalid("Invalid Google Drive file creation input")

  private def _creation_json(parent: FileStoreRef, label: String, mime: String): String =
    Json.obj("name" -> Json.fromString(label), "mimeType" -> Json.fromString(mime),
      "parents" -> Json.arr(Json.fromString(parent.key))).noSpaces

  private def _parents(fields: JsonObject): Option[Vector[String]] = fields("parents") match {
    case None => Some(Vector.empty)
    case Some(value) => value.asArray.flatMap { values =>
      val parents = values.flatMap(_.asString.filter(_valid_id))
      Option.when(parents.size == values.size)(parents)
    }
  }

  private def _optional_decimal(fields: JsonObject, key: String): Option[Option[String]] = fields(key) match {
    case None => Some(None)
    case Some(value) => value.asString.filter(_.matches("0|[1-9][0-9]*")).map(Some(_))
  }

  private def _invalid_metadata[A](writing: Boolean): Consequence[A] =
    if (writing) Consequence.resourceInvalid("Google Drive file creation is unconfirmed; do not retry automatically")
    else Consequence.resourceInvalid("Google Drive file metadata is invalid")

  private def _status[A](status: Int, writing: Boolean): Consequence[A] = status match {
    case 401 | 403 => Consequence.securityPermissionDenied("Google Drive file access was denied")
    case 404 => Consequence.resourceNotFound("Google Drive file was not found")
    case value if value == 429 || value >= 500 && value <= 599 =>
      Consequence.serviceUnavailable(if (writing) "Google Drive file creation is unconfirmed; do not retry automatically"
        else "Google Drive file service is unavailable")
    case _ => Consequence.resourceInvalid(if (writing) "Google Drive file creation is unconfirmed; do not retry automatically"
      else "Google Drive file response is invalid")
  }

  private def _session(location: Option[String]): Consequence[String] = {
    val admitted = location.filter(text => text.nonEmpty && text.length <= 16384 &&
      !text.exists(c => c == '\\' || Character.isISOControl(c))).filter { text =>
      try {
        val uri = new URI(text)
        val query = Option(uri.getRawQuery).toVector.flatMap(_.split("&", -1).toVector).map { part =>
          val pair = part.split("=", 2)
          URLDecoder.decode(pair(0), StandardCharsets.UTF_8) ->
            URLDecoder.decode(pair.lift(1).getOrElse(""), StandardCharsets.UTF_8)
        }
        val uploadids = query.collect { case ("upload_id", value) => value }
        val uploadtypes = query.collect { case ("uploadType", value) => value }
        uri.isAbsolute && !uri.isOpaque && uri.getScheme == "https" && uri.getHost == "www.googleapis.com" &&
          Option(uri.getRawUserInfo).isEmpty && Option(uri.getRawFragment).isEmpty &&
          Set(-1, 443)(uri.getPort) && uri.getRawPath == "/upload/drive/v3/files" &&
          uploadids.size == 1 && uploadids.head.nonEmpty && uploadtypes == Vector("resumable")
      } catch { case NonFatal(_) => false }
    }
    admitted.fold[Consequence[String]](Consequence.resourceInvalid("Google Drive upload session is invalid; creation is unconfirmed"))(
      Consequence.success)
  }
}
