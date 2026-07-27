package org.goldenport.cncf.component

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.jdk.CollectionConverters._

import io.circe.{ACursor, HCursor, Json}
import io.circe.parser.parse

import org.goldenport.Consequence
import org.goldenport.cncf.CncfVersion

/*
 * CNCF-owned admission of packaged CAR runtime evidence.
 *
 * The runtime deliberately does not load Cozy or interpret generation
 * compatibility/provenance. All regular CAR files are integrity-protected as
 * opaque bytes; ABI and CNCF runtime compatibility are validated separately.
 *
 * @since   Jul. 28, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[component] object CarRuntimeAdmission {
  val MANIFEST_FILE = "car-runtime-manifest.json"
  val MANIFEST_SCHEMA = "cncf.car-runtime-manifest.v1"
  val ABI_MANIFEST_FILE = "abi-manifest.json"
  val ABI_MANIFEST_FORMAT = "cozy.car.abi-manifest.v1"

  private final case class RuntimeRange(
    minimum: String,
    maximum: Option[String],
    excluded: Vector[String],
    tested: Vector[String]
  )

  private final case class IntegrityEntry(
    path: String,
    sha256: String
  )

  def validate(extracted: CarExtracted): Consequence[Unit] =
    _validate(extracted) match {
      case Right(_) => Consequence.success(())
      case Left(message) => Consequence.resourceInvalid(message)
    }

  private def _validate(extracted: CarExtracted): Either[String, Unit] =
    for {
      manifest <- _read_json(
        extracted.root.resolve(MANIFEST_FILE),
        "CAR runtime manifest"
      )
      _ <- _require_string(manifest.hcursor, "schemaVersion", "CAR runtime manifest")
        .flatMap { schema =>
          _require_equal(
            schema,
            MANIFEST_SCHEMA,
            "CAR runtime manifest schemaVersion"
          )
        }
      _ <- _validate_coordinate(manifest.hcursor, extracted.descriptor)
      range <- _runtime_range(manifest.hcursor)
      _ <- _validate_runtime_range(range, CncfVersion.current)
      entries <- _integrity_entries(manifest.hcursor)
      _ <- _validate_integrity(extracted.root, entries)
      abi <- _read_json(
        extracted.root.resolve(ABI_MANIFEST_FILE),
        "CAR ABI manifest"
      )
      _ <- _validate_abi(abi.hcursor, extracted.descriptor)
    } yield ()

  private def _read_json(path: Path, label: String): Either[String, Json] =
    if (!Files.isRegularFile(path))
      Left(s"${label} is missing: ${path}")
    else
      try {
        parse(Files.readString(path)).left.map { error =>
          s"${label} is invalid JSON: ${error.message}"
        }
      } catch {
        case error: Exception =>
          Left(s"${label} cannot be read: ${error.getMessage}")
      }

  private def _validate_coordinate(
    cursor: HCursor,
    descriptor: ComponentDescriptor
  ): Either[String, Unit] =
    for {
      name <- _required_descriptor_value(descriptor.name, "name")
      version <- _required_descriptor_value(descriptor.version, "version")
      component <- _required_descriptor_value(
        descriptor.componentName,
        "component"
      )
      manifestname <- _require_string(
        cursor.downField("car"),
        "name",
        "CAR runtime manifest car"
      )
      manifestversion <- _require_string(
        cursor.downField("car"),
        "version",
        "CAR runtime manifest car"
      )
      manifestcomponent <- _require_string(
        cursor.downField("car"),
        "component",
        "CAR runtime manifest car"
      )
      _ <- _require_equal(manifestname, name, "CAR runtime manifest car.name")
      _ <- _require_equal(
        manifestversion,
        version,
        "CAR runtime manifest car.version"
      )
      _ <- _require_equal(
        manifestcomponent,
        component,
        "CAR runtime manifest car.component"
      )
    } yield ()

  private def _runtime_range(cursor: HCursor): Either[String, RuntimeRange] = {
    val cncf = cursor.downField("runtime").downField("cncf")
    for {
      minimum <- _require_string(cncf, "minimum", "CAR CNCF runtime range")
      maximum <- _optional_string(cncf, "maximum", "CAR CNCF runtime range")
      excluded <- _string_vector(cncf, "excluded", "CAR CNCF runtime range")
      tested <- _string_vector(cncf, "tested", "CAR CNCF runtime range")
      _ <-
        if (tested.nonEmpty) Right(())
        else Left("CAR CNCF runtime range tested must contain at least one version")
      _ <- maximum match {
        case Some(value) if _compare_versions(minimum, value) > 0 =>
          Left(
            s"CAR CNCF runtime range is inverted: minimum=${minimum}, maximum=${value}"
          )
        case _ => Right(())
      }
    } yield RuntimeRange(
      minimum,
      maximum,
      excluded.distinct.sorted,
      tested.distinct.sorted
    )
  }

  private def _validate_runtime_range(
    range: RuntimeRange,
    current: String
  ): Either[String, Unit] =
    if (_compare_versions(current, range.minimum) < 0)
      Left(
        s"CNCF runtime ${current} is below CAR minimum ${range.minimum}"
      )
    else if (range.maximum.exists(_compare_versions(current, _) > 0))
      Left(
        s"CNCF runtime ${current} is above CAR maximum ${range.maximum.get}"
      )
    else if (range.excluded.contains(current))
      Left(s"CNCF runtime ${current} is excluded by the CAR runtime range")
    else
      Right(())

  private def _integrity_entries(
    cursor: HCursor
  ): Either[String, Vector[IntegrityEntry]] = {
    val integrity = cursor.downField("integrity")
    for {
      algorithm <- _require_string(
        integrity,
        "algorithm",
        "CAR runtime manifest integrity"
      )
      _ <- _require_equal(
        algorithm,
        "SHA-256",
        "CAR runtime manifest integrity.algorithm"
      )
      values <- integrity.downField("entries").as[Vector[Json]].left.map { error =>
        s"CAR runtime manifest integrity.entries is invalid: ${error.message}"
      }
      entries <- values.zipWithIndex.foldLeft(
        Right(Vector.empty): Either[String, Vector[IntegrityEntry]]
      ) { case (result, (value, index)) =>
        for {
          accumulated <- result
          path <- _require_string(
            value.hcursor,
            "path",
            s"CAR integrity entry ${index}"
          )
          digest <- _require_string(
            value.hcursor,
            "sha256",
            s"CAR integrity entry ${index}"
          )
          _ <-
            if (_is_safe_relative_path(path)) Right(())
            else Left(s"CAR integrity entry has unsafe path: ${path}")
          _ <-
            if (digest.matches("[0-9a-f]{64}")) Right(())
            else Left(s"CAR integrity entry has invalid SHA-256: ${path}")
        } yield accumulated :+ IntegrityEntry(path, digest)
      }
      duplicates = entries.groupBy(_.path).collect {
        case (path, xs) if xs.size > 1 => path
      }.toVector.sorted
      _ <-
        if (duplicates.isEmpty) Right(())
        else Left(
          s"CAR runtime manifest has duplicate integrity paths: ${duplicates.mkString(", ")}"
        )
    } yield entries.sortBy(_.path)
  }

  private def _validate_integrity(
    root: Path,
    entries: Vector[IntegrityEntry]
  ): Either[String, Unit] = {
    val files = _regular_files(root).filterNot(_._1 == MANIFEST_FILE)
    val expectedpaths = entries.map(_.path)
    val actualpaths = files.map(_._1)
    if (expectedpaths != actualpaths) {
      val missing = actualpaths.toSet.diff(expectedpaths.toSet).toVector.sorted
      val unexpected = expectedpaths.toSet.diff(actualpaths.toSet).toVector.sorted
      Left(
        s"CAR integrity file set mismatch: unlisted=${missing.mkString(",")}, missing=${unexpected.mkString(",")}"
      )
    } else {
      val actualfiles = files.toMap
      entries.collectFirst {
        case entry
            if _sha256(actualfiles(entry.path)) != entry.sha256 =>
          entry
      } match {
        case Some(entry) =>
          Left(s"CAR integrity digest mismatch: ${entry.path}")
        case None =>
          Right(())
      }
    }
  }

  private def _validate_abi(
    cursor: HCursor,
    descriptor: ComponentDescriptor
  ): Either[String, Unit] =
    for {
      format <- _require_string(cursor, "format", "CAR ABI manifest")
      _ <- _require_equal(
        format,
        ABI_MANIFEST_FORMAT,
        "CAR ABI manifest format"
      )
      name <- _required_descriptor_value(descriptor.name, "name")
      version <- _required_descriptor_value(descriptor.version, "version")
      component <- _required_descriptor_value(
        descriptor.componentName,
        "component"
      )
      abiname <- _require_string(
        cursor.downField("car"),
        "name",
        "CAR ABI manifest car"
      )
      abiversion <- _require_string(
        cursor.downField("car"),
        "version",
        "CAR ABI manifest car"
      )
      _ <- _require_equal(abiname, name, "CAR ABI manifest car.name")
      _ <- _require_equal(
        abiversion,
        version,
        "CAR ABI manifest car.version"
      )
      versionnumber <- cursor.downField("abi").get[Int]("version").left.map {
        error => s"CAR ABI manifest abi.version is invalid: ${error.message}"
      }
      _ <-
        if (versionnumber == 1) Right(())
        else Left(s"Unsupported CAR ABI version: ${versionnumber}")
      components <- cursor
        .downField("abi")
        .downField("exports")
        .downField("components")
        .as[Vector[Json]]
        .left
        .map { error =>
          s"CAR ABI exports.components is invalid: ${error.message}"
        }
      componentnames <- components.zipWithIndex.foldLeft(
        Right(Vector.empty): Either[String, Vector[String]]
      ) { case (result, (value, index)) =>
        for {
          accumulated <- result
          componentname <- _require_string(
            value.hcursor,
            "name",
            s"CAR ABI component export ${index}"
          )
        } yield accumulated :+ componentname
      }
      _ <-
        if (componentnames.contains(component)) Right(())
        else Left(
          s"CAR ABI manifest does not export packaged component ${component}"
        )
    } yield ()

  private def _required_descriptor_value(
    value: Option[String],
    field: String
  ): Either[String, String] =
    value.map(_.trim).filter(_.nonEmpty).toRight(
      s"CAR component descriptor is missing ${field}"
    )

  private def _require_string(
    cursor: ACursor,
    field: String,
    owner: String
  ): Either[String, String] =
    cursor.get[String](field).left.map { error =>
      s"${owner}.${field} is invalid: ${error.message}"
    }.flatMap { value =>
      val normalized = value.trim
      if (normalized.nonEmpty) Right(normalized)
      else Left(s"${owner}.${field} must be non-empty")
    }

  private def _optional_string(
    cursor: ACursor,
    field: String,
    owner: String
  ): Either[String, Option[String]] =
    cursor.get[Option[String]](field).left.map { error =>
      s"${owner}.${field} is invalid: ${error.message}"
    }.flatMap {
      case Some(value) if value.trim.nonEmpty => Right(Some(value.trim))
      case Some(_) => Left(s"${owner}.${field} must be non-empty when present")
      case None => Right(None)
    }

  private def _string_vector(
    cursor: ACursor,
    field: String,
    owner: String
  ): Either[String, Vector[String]] =
    cursor.get[Vector[String]](field).left.map { error =>
      s"${owner}.${field} is invalid: ${error.message}"
    }.flatMap { values =>
      val normalized = values.map(_.trim)
      if (normalized.forall(_.nonEmpty)) Right(normalized)
      else Left(s"${owner}.${field} must not contain blank versions")
    }

  private def _require_equal(
    actual: String,
    expected: String,
    owner: String
  ): Either[String, Unit] =
    if (actual == expected) Right(())
    else Left(s"${owner} mismatch: expected=${expected}, actual=${actual}")

  private def _regular_files(root: Path): Vector[(String, Path)] = {
    val stream = Files.walk(root)
    try {
      stream.iterator().asScala.toVector.collect {
        case path if Files.isRegularFile(path) =>
          root.relativize(path).toString.replace('\\', '/') -> path
      }.sortBy(_._1)
    } finally {
      stream.close()
    }
  }

  private def _is_safe_relative_path(path: String): Boolean = {
    val parts = path.split("/", -1).toVector
    path.nonEmpty &&
      !path.startsWith("/") &&
      !path.contains('\\') &&
      parts.forall(part => part.nonEmpty && part != "." && part != "..")
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var count = input.read(buffer)
      while (count >= 0) {
        if (count > 0)
          digest.update(buffer, 0, count)
        count = input.read(buffer)
      }
    } finally {
      input.close()
    }
    digest.digest().map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def _compare_versions(left: String, right: String): Int = {
    def _parts_(value: String): Vector[String] =
      value.split("[.\\-+_]").toVector.map(_.trim).filter(_.nonEmpty)
    def _number_(value: String): Option[BigInt] =
      if (value.forall(_.isDigit)) Some(BigInt(value)) else None
    def _compare_part_(l: String, r: String): Int =
      (_number_(l), _number_(r)) match {
        case (Some(a), Some(b)) => a.compare(b)
        case (Some(_), None) => 1
        case (None, Some(_)) => -1
        case (None, None) => l.compareToIgnoreCase(r)
      }
    def _remaining_(parts: Vector[String], index: Int): Int =
      parts.drop(index).find(_.nonEmpty).map { value =>
        _number_(value) match {
          case Some(number) => number.signum
          case None => -1
        }
      }.getOrElse(0)

    val leftparts = _parts_(left)
    val rightparts = _parts_(right)
    val size = math.max(leftparts.length, rightparts.length)
    (0 until size).foldLeft(0) {
      case (0, index) if index >= leftparts.length =>
        -_remaining_(rightparts, index)
      case (0, index) if index >= rightparts.length =>
        _remaining_(leftparts, index)
      case (0, index) =>
        _compare_part_(leftparts(index), rightparts(index))
      case (result, _) => result
    }
  }
}
