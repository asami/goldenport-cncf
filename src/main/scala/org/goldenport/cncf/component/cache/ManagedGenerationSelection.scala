package org.goldenport.cncf.component.cache

import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal
import io.circe.Json
import io.circe.parser.parse

/** Short coordinate transactions; payload use remains protected by native generation locks.
  * References are conservative: only a protected current pointer proves liveness.
  * @since   Oct. 21, 2026
  * @version Oct. 22, 2026
  */
private[cache] final class ManagedGenerationSelection(root: Path, policy: ManagedCachePolicy, files: ManagedCacheFiles = ManagedCacheFiles) {
  import ManagedGenerationSelection.Selection

  def coordinate(archive: Path, kind: ArchiveKind): String = {
    val bytes = (kind.directory + ":" + archive.toAbsolutePath.normalize.toUri.toString).getBytes(java.nio.charset.StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString
  }

  private def _coordinate_lock(key: String): Path = root.resolve("locks/coordinates").resolve(key + ".lock")
  private def _generation_lock(key: String): Path = root.resolve("locks/generations").resolve(key + ".lock")
  private def _current(key: String): Path = root.resolve("metadata/coordinates").resolve(key).resolve("current.json")
  private def _metadata(key: String): Path = root.resolve("metadata/generations").resolve(key)
  private def _keys(source: SourceGeneration): Vector[String] = Vector(source.key, ExtractionGeneration(source).key)

  private def _read(key: String): Option[Selection] = {
    val path = _current(key)
    if (!Files.exists(path)) None
    else {
      val cursor = parse(Files.readString(path)).fold(throw _, identity).hcursor
      require(cursor.get[String]("schema").contains("cncf.managed-cache-selection.v1"), "invalid selection schema")
      val kind = cursor.get[String]("kind").fold(throw _, identity)
      val source = SourceGeneration(ArchiveKind.values.find(_.directory == kind).getOrElse(throw new IllegalArgumentException(kind)),
        cursor.get[String]("digest").fold(throw _, identity))
      require(cursor.get[String]("profile").contains(source.kind.profile), "invalid selection profile")
      Some(Selection(cursor.get[String]("revision").fold(throw _, identity), source))
    }
  }

  def withCoordinate[A](key: String)(body: => A): A = {
    val pin = ManagedCacheLocks.acquire(_coordinate_lock(key), false, policy)
    try body finally pin.close()
  }

  def collectCoordinate(key: String)(use: => Unit): Boolean = {
    val coordinate = ManagedCacheLocks.tryAcquire(_coordinate_lock(key), false)
    if (coordinate.isEmpty) false
    else try {
      val body = ManagedCacheLocks.tryAcquire(_generation_lock("coordinate-" + key), false)
      if (body.isEmpty) false
      else try { use; true } finally body.get.close()
    } finally coordinate.get.close()
  }

  def observe(key: String): Option[Selection] = {
    val pin = ManagedCacheLocks.acquire(_coordinate_lock(key), false, policy)
    try _read(key) finally pin.close()
  }

  /** Nonblocking generation acquisition under a coordinate lock. Callers retry outside it. */
  def pinCurrentSource(key: String): Option[(Selection, ManagedCacheLocks.Pin)] = {
    val pin = ManagedCacheLocks.acquire(_coordinate_lock(key), false, policy)
    try _read(key).flatMap(selection => ManagedCacheLocks.tryAcquire(_generation_lock(selection.source.key), true)
      .map(usage => selection -> usage))
    finally pin.close()
  }

  /** Caller already holds source and extraction shared pins throughout this transaction. */
  def commit(key: String, expected: Option[Selection], source: SourceGeneration): Boolean = {
    val pin = ManagedCacheLocks.acquire(_coordinate_lock(key), false, policy)
    try {
      val current = _read(key)
      if (current != expected) false
      else {
        _keys(source).foreach { generation =>
          _add_reference(key, generation, source)
        }
        if (!current.exists(_.source == source)) {
          // Record candidates before switching: either side of a failed switch
          // can be reconciled from its references and the authoritative pointer.
          current.foreach(value => markRetired(value.source))
          _atomic_json(_current(key), Json.obj(
            "schema" -> Json.fromString("cncf.managed-cache-selection.v1"),
            "revision" -> Json.fromString(UUID.randomUUID().toString),
            "kind" -> Json.fromString(source.kind.directory),
            "digest" -> Json.fromString(source.digest),
            "profile" -> Json.fromString(source.kind.profile)))
        }
        true
      }
    } finally pin.close()
  }

  private def _add_reference(coordinate: String, target: String, expected: SourceGeneration): Unit = {
    val path = _metadata(target).resolve("references").resolve(coordinate)
    val previous = if (Files.isRegularFile(path)) parse(Files.readString(path)).toOption.toVector
      .flatMap(_.hcursor.get[Vector[String]]("sources").getOrElse(Vector.empty)) else Vector.empty
    if (!previous.contains(expected.key))
      _atomic_json(path, Json.obj("sources" -> Json.arr((previous :+ expected.key).distinct.map(Json.fromString)*)))
  }

  /** Immutable embedded CARs have no permanent path alias. Their references
    * name the parent selection; shared child pins cover registration and use.
    */
  def referenceEmbedded(parent: SourceGeneration, child: SourceGeneration): Unit = {
    // Persist the targeted relationship before registering references. Replacement
    // can reconcile these children directly even when the general scan is full.
    _atomic_json(_metadata(parent.key).resolve("dependents").resolve(child.key), Json.obj(
      "kind" -> Json.fromString(child.kind.directory), "digest" -> Json.fromString(child.digest)))
    _references(parent.key).foreach { coordinate =>
      val pin = ManagedCacheLocks.acquire(_coordinate_lock(coordinate), false, policy)
      try _keys(child).foreach(target => _add_reference(coordinate, target, parent))
      finally pin.close()
    }
    markRetired(child)
  }

  def dependentSources(parent: SourceGeneration): Vector[SourceGeneration] = {
    val directory = _metadata(parent.key).resolve("dependents")
    if (!Files.isDirectory(directory)) Vector.empty
    else Using.resource(Files.list(directory))(_.iterator().asScala.filter(Files.isRegularFile(_)).toVector)
      .map { path =>
        val cursor = parse(Files.readString(path)).fold(throw _, identity).hcursor
        val kind = cursor.get[String]("kind").fold(throw _, identity)
        SourceGeneration(ArchiveKind.values.find(_.directory == kind).getOrElse(throw new IllegalArgumentException(kind)),
          cursor.get[String]("digest").fold(throw _, identity))
      }
  }

  private def _is_current_reference(key: String, coordinate: String): Boolean = {
    val path = _metadata(key).resolve("references").resolve(coordinate)
    val expected = parse(Files.readString(path)).fold(throw _, identity).hcursor
      .get[Vector[String]]("sources").fold(throw _, identity)
    _read(coordinate).exists(selection => expected.contains(selection.source.key))
  }

  def markRetired(source: SourceGeneration): Unit = _keys(source).foreach { key =>
    val pending = _metadata(key).resolve("retirement.json")
    if (!Files.isRegularFile(pending))
      _atomic_json(pending, Json.obj("pending" -> Json.fromBoolean(true)))
  }

  private def _references(key: String): Vector[String] = {
    val directory = _metadata(key).resolve("references")
    if (!Files.isDirectory(directory)) Vector.empty
    else Using.resource(Files.list(directory))(_.iterator().asScala.filter(Files.isRegularFile(_))
      .map(_.getFileName.toString).filter(_.matches("[0-9a-f]{64}")).toVector.sorted)
  }

  /** Never wait while holding coordinates or generation exclusion. A newly added
    * reference forces a later retry with the complete deterministic lock set.
    */
  def collectRetired(source: SourceGeneration, extracted: Boolean, delete: () => Unit): Unit = {
    try collectProtected(source, extracted, false) { (current, pending) =>
      if (!current && pending) { delete(); true } else false
    } catch { case NonFatal(_) => () } // durable pending failure is retried by explicit collection
    ()
  }

  /** Coordinate-before-generation protection is shared by retirement and policy
    * collection. Dry-run executes the same checks without changing metadata.
    */
  def collectProtected(source: SourceGeneration, extracted: Boolean, dryRun: Boolean)(
    use: (Boolean, Boolean) => Boolean
  ): Boolean = {
    val key = if (extracted) ExtractionGeneration(source).key else source.key
    val pending = _metadata(key).resolve("retirement.json")
    val references = _references(key)
    var pins = Vector.empty[ManagedCacheLocks.Pin]
    var complete = true
    var acquired = false
    try {
      references.foreach { coordinate =>
        if (complete) ManagedCacheLocks.tryAcquire(_coordinate_lock(coordinate), false) match {
          case Some(pin) => pins :+= pin
          case None => complete = false
        }
      }
      if (complete) ManagedCacheLocks.tryAcquire(_generation_lock(key), false).foreach { exclusion =>
        try {
          if (_references(key) == references) {
            acquired = true
            val current = references.exists(coordinate => _is_current_reference(key, coordinate))
            if (use(current, Files.isRegularFile(pending)) && !dryRun) Files.deleteIfExists(pending)
          }
        } catch {
          case NonFatal(error) =>
            if (!dryRun) _atomic_json(pending, Json.obj("pending" -> Json.fromBoolean(true), "failure" -> Json.fromString(error.toString)))
            throw error
        } finally exclusion.close()
      }
    } finally pins.reverse.foreach(_.close())
    acquired
  }

  private def _atomic_json(path: Path, value: Json): Unit = {
    if (Files.isRegularFile(path) && Files.readString(path) == value.noSpaces) return
    Files.createDirectories(path.getParent)
    val temporary = path.resolveSibling(path.getFileName.toString + "." + UUID.randomUUID().toString + ".tmp")
    try {
      files.writeMetadata(temporary, value.noSpaces)
      try Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case error: AtomicMoveNotSupportedException => throw CacheFailure(CacheFailure.Reason.UnsupportedFilesystem,
          "publish-selection", path.toString, detail = error.toString)
      }
    } finally Files.deleteIfExists(temporary)
  }
}

private[cache] object ManagedGenerationSelection {
  final case class Selection(revision: String, source: SourceGeneration)
}
