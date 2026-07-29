package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import io.circe.{ACursor, HCursor, Json}
import io.circe.parser.parse

import org.goldenport.Consequence
import org.goldenport.cncf.CncfVersion

/*
 * CNCF-owned admission of mutable component-development runtime evidence.
 *
 * Development evidence proves stable CAR contract inputs. It intentionally
 * does not reuse packaged-CAR archive inventory semantics or accept a
 * packaged manifest as a development substitute.
 *
 * @since   Jul. 29, 2026
 * @version Jul. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[component] object DevelopmentCarRuntimeAdmission {
  val MANIFEST_FILE = "car-runtime-manifest.json"
  val MANIFEST_SCHEMA = "cncf.car-development-runtime-manifest.v1"
  val SOURCE_KIND = "development-directory"
  val RUNTIME_CLASSPATH_IDENTITY = "target/cncf.d/runtime-classpath.txt"
  val COMPONENT_DESCRIPTOR_IDENTITY = "src/main/car/component-descriptor.json"
  val ABI_MANIFEST_IDENTITY = "src/main/car/abi-manifest.json"

  private final case class Coordinate(name: String, version: String, component: String)
  private final case class RuntimeRange(minimum: String, maximum: Option[String], excluded: Vector[String], tested: Vector[String])
  private final case class Evidence(path: String, sha256: String, logicalSha256: Option[String])

  def validate(base: Path): Consequence[Unit] =
    try {
      _validate(base) match {
        case Right(_) => Consequence.success(())
        case Left(message) => Consequence.resourceInvalid(recoveryMessage(base, message))
      }
    } catch {
      case NonFatal(e) =>
        Consequence.resourceInvalid(
          recoveryMessage(base, s"development runtime evidence validation failed: ${Option(e.getMessage).getOrElse(e.getClass.getName)}")
        )
    }

  private def _validate(base: Path): Either[String, Unit] = {
    val root = base.toAbsolutePath.normalize()
    for {
      _ <- if (Files.isDirectory(root)) Right(()) else Left(s"[component-dev-dir] component development directory not found: $root")
      manifest <- _read_json(root.resolve("target/cncf.d").resolve(MANIFEST_FILE), "development runtime manifest")
      schema <- _required_string(manifest.hcursor, "schemaVersion", "development runtime manifest")
      _ <- _require_equal(schema, MANIFEST_SCHEMA, "development runtime manifest schemaVersion")
      sourcekind <- _required_string(manifest.hcursor, "sourceKind", "development runtime manifest")
      _ <- _require_equal(sourcekind, SOURCE_KIND, "development runtime manifest sourceKind")
      coordinate <- _coordinate(root)
      _ <- _validate_coordinate(manifest.hcursor, coordinate)
      range <- _runtime_range(manifest.hcursor)
      _ <- _validate_runtime_range(range, CncfVersion.current)
      evidence <- _evidence(manifest.hcursor)
      _ <- _validate_evidence(root, evidence)
      _ <- _validate_evidence_digest(manifest.hcursor, evidence)
    } yield ()
  }

  def recoveryMessage(base: Path, detail: String): String =
    s"[component-dev-dir] $detail. " +
      s"Run 'sbt cozyPrepareRuntime' in $base, then restart the application server. " +
      "CNCF will not fall back to a packaged CAR while component-dev-dir is explicit."

  private def _coordinate(root: Path): Either[String, Coordinate] =
    for {
      descriptor <- _read_json(root.resolve(COMPONENT_DESCRIPTOR_IDENTITY), "component development descriptor")
      name <- _required_string(descriptor.hcursor, "name", "component development descriptor")
      version <- _required_string(descriptor.hcursor, "version", "component development descriptor")
      component <- _required_string(descriptor.hcursor, "component", "component development descriptor")
      abi <- _read_json(root.resolve(ABI_MANIFEST_IDENTITY), "component development ABI manifest")
      format <- _required_string(abi.hcursor, "format", "component development ABI manifest")
      _ <- _require_equal(format, "cozy.car.abi-manifest.v1", "component development ABI manifest format")
      abiname <- _required_string(abi.hcursor.downField("car"), "name", "component development ABI manifest car")
      _ <- _require_equal(abiname, name, "component development ABI name")
      abiversion <- _required_string(abi.hcursor.downField("car"), "version", "component development ABI manifest car")
      _ <- _require_equal(abiversion, version, "component development ABI version")
      exports <- abi.hcursor.downField("abi").downField("exports").downField("components").as[Vector[Json]].left.map(error => s"component development ABI exports are invalid: ${error.message}")
      names <- _export_names(exports)
      _ <- if (names.contains(component)) Right(()) else Left(s"component development ABI does not export component $component")
    } yield Coordinate(name, version, component)

  private def _validate_coordinate(cursor: HCursor, coordinate: Coordinate): Either[String, Unit] =
    for {
      name <- _required_string(cursor.downField("car"), "name", "development runtime manifest car")
      version <- _required_string(cursor.downField("car"), "version", "development runtime manifest car")
      component <- _required_string(cursor.downField("car"), "component", "development runtime manifest car")
      _ <- _require_equal(name, coordinate.name, "development runtime manifest car.name")
      _ <- _require_equal(version, coordinate.version, "development runtime manifest car.version")
      _ <- _require_equal(component, coordinate.component, "development runtime manifest car.component")
    } yield ()

  private def _runtime_range(cursor: HCursor): Either[String, RuntimeRange] = {
    val cncf = cursor.downField("runtime").downField("cncf")
    for {
      minimum <- _required_string(cncf, "minimum", "development CNCF runtime range")
      maximum <- _optional_string(cncf, "maximum", "development CNCF runtime range")
      excluded <- _string_vector(cncf, "excluded", "development CNCF runtime range")
      tested <- _string_vector(cncf, "tested", "development CNCF runtime range")
      _ <- if (tested.nonEmpty) Right(()) else Left("development CNCF runtime range tested must contain at least one version")
      _ <- maximum.filter(CarRuntimeVersionOrdering.compare(minimum, _) > 0).map(value => Left(s"development CNCF runtime range is inverted: minimum=$minimum, maximum=$value")).getOrElse(Right(()))
    } yield RuntimeRange(minimum, maximum, excluded.distinct.sorted, tested.distinct.sorted)
  }

  private def _validate_runtime_range(range: RuntimeRange, current: String): Either[String, Unit] =
    if (CarRuntimeVersionOrdering.compare(current, range.minimum) < 0) Left(s"CNCF runtime $current is below development CAR minimum ${range.minimum}")
    else if (range.maximum.exists(CarRuntimeVersionOrdering.compare(current, _) > 0)) Left(s"CNCF runtime $current is above development CAR maximum ${range.maximum.get}")
    else if (range.excluded.contains(current)) Left(s"CNCF runtime $current is excluded by the development CAR runtime range")
    else Right(())

  private def _evidence(cursor: HCursor): Either[String, Vector[Evidence]] =
    cursor.downField("evidence").as[Vector[Json]].left.map(error => s"development runtime manifest evidence is invalid: ${error.message}").flatMap { values =>
      values.zipWithIndex.foldLeft(Right(Vector.empty): Either[String, Vector[Evidence]]) { case (result, (value, index)) =>
        for {
          accumulated <- result
          path <- _required_string(value.hcursor, "path", s"development runtime evidence $index")
          digest <- _required_string(value.hcursor, "sha256", s"development runtime evidence $index")
          logical <- _optional_string(value.hcursor, "logicalSha256", s"development runtime evidence $index")
          _ <- if (_safe_identity(path)) Right(()) else Left(s"development runtime evidence has unsafe path: $path")
          _ <- if (digest.matches("[0-9a-f]{64}")) Right(()) else Left(s"development runtime evidence has invalid SHA-256: $path")
        } yield accumulated :+ Evidence(path, digest, logical)
      }.flatMap { evidence =>
        val expected = Vector(RUNTIME_CLASSPATH_IDENTITY, COMPONENT_DESCRIPTOR_IDENTITY, ABI_MANIFEST_IDENTITY)
        if (evidence.map(_.path) == expected) Right(evidence)
        else Left(s"development runtime evidence paths are invalid: ${evidence.map(_.path).mkString(",")}")
      }
    }

  private def _validate_evidence(root: Path, evidence: Vector[Evidence]): Either[String, Unit] =
    evidence.foldLeft(Right(()): Either[String, Unit]) { case (result, entry) =>
      result.flatMap { _ =>
        val file = root.resolve(entry.path).normalize()
        if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.size(file) == 0L)
          Left(s"development runtime evidence is missing or empty: $file")
        else if (_sha256(file) != entry.sha256)
          Left(s"development runtime evidence digest mismatch: ${entry.path}")
        else if (entry.path == RUNTIME_CLASSPATH_IDENTITY)
          _classpath_identity(root, file).flatMap { identity =>
            if (entry.logicalSha256 == Some(identity)) Right(())
            else Left(s"development runtime classpath logical identity mismatch: $file")
          }
        else
          Right(())
      }
    }

  private def _validate_evidence_digest(cursor: HCursor, evidence: Vector[Evidence]): Either[String, Unit] =
    for {
      integrity <- cursor.downField("integrity").focus.toRight("development runtime manifest integrity is missing")
      algorithm <- _required_string(integrity.hcursor, "algorithm", "development runtime manifest integrity")
      _ <- _require_equal(algorithm, "SHA-256", "development runtime manifest integrity.algorithm")
      digest <- _required_string(integrity.hcursor, "evidenceSha256", "development runtime manifest integrity")
      _ <- if (digest == _evidence_digest(evidence)) Right(()) else Left("development runtime manifest evidence digest mismatch")
    } yield ()

  private def _evidence_digest(entries: Vector[Evidence]): String =
    _sha256(entries.map { entry =>
      s"${entry.path}\t${entry.sha256}\t${entry.logicalSha256.getOrElse("")}"
    }.mkString("\n").getBytes(StandardCharsets.UTF_8))

  private def _classpath_identity(root: Path, path: Path): Either[String, String] =
    try {
      val entries = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
        .flatMap(_.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)).toVector)
        .map(_.trim)
        .filter(_.nonEmpty)
      entries.foldLeft(Right(Vector.empty): Either[String, Vector[String]]) { case (result, value) =>
        for {
          accumulated <- result
          identity <- _classpath_entry_identity(root, path, value)
        } yield accumulated :+ identity
      }.map { identities =>
        _sha256(identities.distinct.sorted.mkString("\n").getBytes(StandardCharsets.UTF_8))
      }
    } catch {
      case NonFatal(e) =>
        Left(s"development runtime classpath cannot be read: $path: ${Option(e.getMessage).getOrElse(e.getClass.getName)}")
    }

  private def _classpath_entry_identity(root: Path, classpath: Path, value: String): Either[String, String] =
    try {
      val entry = Path.of(value).toAbsolutePath.normalize()
      if (!Files.exists(entry))
        Left(s"development runtime classpath entry is missing: $entry (declared by $classpath)")
      else if (entry.startsWith(root))
        Right(s"project:${root.relativize(entry).toString.replace('\\', '/')}")
      else
        Right(s"external:${entry.getFileName.toString}")
    } catch {
      case NonFatal(e) =>
        Left(s"development runtime classpath entry is invalid: $value (declared by $classpath): ${Option(e.getMessage).getOrElse(e.getClass.getName)}")
    }

  private def _export_names(values: Vector[Json]): Either[String, Vector[String]] =
    values.zipWithIndex.foldLeft(Right(Vector.empty): Either[String, Vector[String]]) { case (result, (value, index)) =>
      for {
        accumulated <- result
        name <- _required_string(value.hcursor, "name", s"component development ABI export $index")
      } yield accumulated :+ name
    }

  private def _read_json(path: Path, label: String): Either[String, Json] =
    if (!Files.isRegularFile(path)) Left(s"$label is missing: $path")
    else try parse(Files.readString(path, StandardCharsets.UTF_8)).left.map(error => s"$label is invalid JSON: ${error.message}")
    catch { case error: Exception => Left(s"$label cannot be read: ${error.getMessage}") }

  private def _required_string(cursor: ACursor, field: String, owner: String): Either[String, String] =
    cursor.get[String](field).left.map(error => s"$owner.$field is invalid: ${error.message}").flatMap { value =>
      val normalized = value.trim
      if (normalized.nonEmpty) Right(normalized) else Left(s"$owner.$field must be non-empty")
    }

  private def _optional_string(cursor: ACursor, field: String, owner: String): Either[String, Option[String]] =
    cursor.get[Option[String]](field).left.map(error => s"$owner.$field is invalid: ${error.message}").flatMap {
      case Some(value) if value.trim.nonEmpty => Right(Some(value.trim))
      case Some(_) => Left(s"$owner.$field must be non-empty when present")
      case None => Right(None)
    }

  private def _string_vector(cursor: ACursor, field: String, owner: String): Either[String, Vector[String]] =
    cursor.get[Vector[String]](field).left.map(error => s"$owner.$field is invalid: ${error.message}").flatMap { values =>
      val normalized = values.map(_.trim)
      if (normalized.forall(_.nonEmpty)) Right(normalized) else Left(s"$owner.$field must not contain blank versions")
    }

  private def _require_equal(actual: String, expected: String, label: String): Either[String, Unit] =
    if (actual == expected) Right(()) else Left(s"$label mismatch: expected=$expected actual=$actual")

  private def _safe_identity(value: String): Boolean =
    value.nonEmpty && !value.startsWith("/") && !value.contains('\\') && !value.split('/').contains("..")

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

}
