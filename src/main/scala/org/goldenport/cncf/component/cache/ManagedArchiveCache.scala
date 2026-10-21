package org.goldenport.cncf.component.cache

import java.nio.channels.FileChannel
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption, StandardOpenOption}
import java.nio.file.attribute.{BasicFileAttributes, FileTime}
import java.security.MessageDigest
import java.time.YearMonth
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.component.{CarExtracted, CarExtractor}

/** Cache storage and usage protection for the existing repository/extractor route.
  * This owns neither repository resolution nor runtime shutdown.
  * @since Oct. 21, 2026
 * @version Oct. 21, 2026
  */
final class ManagedArchiveCache(val policy: ManagedCachePolicy) {
  Files.createDirectories(policy.root)
  val root: Path = policy.root.toRealPath()

  private def _lock(key: String): Path = root.resolve("locks/generations").resolve(key + ".lock")
  private def _source(generation: SourceGeneration): Path =
    root.resolve("sources").resolve(generation.kind.directory).resolve(generation.digest)
  private def _extraction(generation: ExtractionGeneration): Path =
    root.resolve("extracted").resolve(generation.source.kind.directory)
      .resolve(generation.source.digest).resolve(generation.profile)

  def acquireSourceC(archive: Path, kind: ArchiveKind = ArchiveKind.Car): Consequence[SourceLease] =
    Consequence {
      _with_guard(_stage("source-" + kind.directory)) { stage =>
        val original = archive.toAbsolutePath.normalize
        val before = Files.readAttributes(original, classOf[BasicFileAttributes])
        if (!before.isRegularFile) throw CacheFailure(CacheFailure.Reason.SourceChanged, "capture", original.toString)
        val digest = MessageDigest.getInstance("SHA-256")
        val captured = stage.payload.resolve("archive" + kind.suffix)
        var bytes = 0L
        Using.resource(Files.newInputStream(original)) { input =>
          Using.resource(Files.newOutputStream(captured)) { output =>
            val buffer = new Array[Byte](65536)
            var count = input.read(buffer)
            while (count >= 0) {
              if (count > 0) {
                _entry_capacity("capture", original.toString, bytes, count.toLong, policy.sourceEntryMaxBytes)
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
                bytes += count
              }
              count = input.read(buffer)
            }
          }
        }
        val after = Files.readAttributes(original, classOf[BasicFileAttributes])
        if (before.fileKey() != after.fileKey() || before.size() != after.size() ||
            before.lastModifiedTime() != after.lastModifiedTime() || bytes != after.size())
          throw CacheFailure(CacheFailure.Reason.SourceChanged, "capture", original.toString)
        _force(captured)
        val generation = SourceGeneration(kind, digest.digest().map(value => f"${value & 0xff}%02x").mkString)
        val destination = _source(generation)
        _write_json(stage.payload.resolve("entry.json"), _metadata(generation, "source-v1", bytes))
        _source_lease(generation, destination, stage.payload)
      }
    }

  private def _source_lease(generation: SourceGeneration, destination: Path, prepared: Path): SourceLease = {
    def _complete_(): Boolean = _valid(destination, generation, "source-v1") &&
      Files.isRegularFile(destination.resolve("archive" + generation.kind.suffix))
    var attempts = 0
    val started = System.nanoTime()
    while (attempts < policy.acquisitionMaxAttempts) {
      attempts += 1
      val pin = ManagedCacheLocks.acquire(_lock(generation.key), shared = true, policy)
      var retained = false
      try {
        val body = destination.resolve("archive" + generation.kind.suffix)
        if (_complete_()) {
          retained = true
          return new SourceLease(generation, body, () => pin.close())
        }
      } finally if (!retained) pin.close()
      ManagedCacheLocks.tryAcquire(_lock(generation.key), shared = false) match {
        case None => _retry_publication(started, generation.key)
        case Some(writer) =>
          try {
            if (!_complete_()) {
              _delete_tree(destination)
              _publish(prepared, destination)
            }
          } finally writer.close()
      }
      // Reacquire shared usage and recheck completion, never expose the gap
      // between dropping construction exclusion and acquiring usage protection.
    }
    throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "publish-source", generation.key)
  }

  def acquireCarC(archive: Path): Consequence[ExtractionLease[CarExtracted]] =
    acquireSourceC(archive).flatMap { source =>
      Consequence {
        _with_guard(source) { owned => _acquire_car(owned) }
      }
    }

  private def _acquire_car(source: SourceLease): ExtractionLease[CarExtracted] = {
    source.checkOpen()
    val generation = ExtractionGeneration(source.generation)
    val destination = _extraction(generation)
    def _complete_(): Boolean = _valid(destination, source.generation, generation.profile) &&
      Files.isDirectory(destination.resolve("tree"))
    var attempts = 0
    val started = System.nanoTime()
    while (attempts < policy.acquisitionMaxAttempts) {
      attempts += 1
      val pin = ManagedCacheLocks.acquire(_lock(generation.key), shared = true, policy)
      var retained = false
      try {
        if (_complete_()) {
          // Structural and runtime admission remain at the existing extractor
          // boundary on hits as well as misses; no directory-content rehash.
          val extracted = CarExtractor.resolveDirectory(destination.resolve("tree")).TAKE
          Files.setLastModifiedTime(destination.resolve("entry.json"), FileTime.fromMillis(System.currentTimeMillis()))
          retained = true
          return new ExtractionLease(generation, extracted, () => pin.close())
        }
      } finally if (!retained) pin.close()
      ManagedCacheLocks.tryAcquire(_lock(generation.key), shared = false) match {
        // Another builder may already have returned a live shared lease. Do
        // not wait for its exclusive lock: reselect the now-complete entry.
        case None => _retry_publication(started, generation.key)
        case Some(writer) =>
          try {
            if (!_complete_()) {
              _delete_tree(destination)
              val stage = _stage("extracted-car")
              try {
                val tree = Files.createDirectory(stage.payload.resolve("tree"))
                var bytes = 0L
                CarExtractor.extractToC(source.path, tree, count => {
                  _entry_capacity("extract", generation.key, bytes, count, policy.extractedEntryMaxBytes)
                  bytes += count
                }).TAKE
                _write_json(stage.payload.resolve("entry.json"), _metadata(source.generation, generation.profile, bytes))
                _publish(stage.payload, destination)
              } finally stage.close()
            }
          } finally writer.close()
      }
    }
    throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "publish-extraction", generation.key)
  }

  /** Existing SystemNode registration owns the backing lease, not this cache.
    * Register before discovery so registration failure cannot strand a loader
    * whose backing lease was never accepted by that same owner.
    */
  private[cncf] def withRetainedCarC[A](
    archive: Path,
    register: AutoCloseable => Consequence[Unit]
  )(use: CarExtracted => Consequence[A]): Consequence[A] =
    acquireCarC(archive).flatMap { lease =>
      var retained = false
      try {
        val result = Consequence.run(register(lease)).flatMap(_ => Consequence.run(use(lease.value)))
        result match {
          case Consequence.Success(_) => retained = true
          case Consequence.Failure(_) => ()
        }
        result
      } finally if (!retained) lease.close()
    }

  def inspectC(): Consequence[CacheInspection] = Consequence {
    CacheInspection(_bytes(root.resolve("sources")), _bytes(root.resolve("extracted")), _bytes(root.resolve("staging")))
  }

  /** Ordinary reusable originals are retained independently of extracted trees. */
  def collectC(dryRun: Boolean = false): Consequence[CacheCollectionResult] = Consequence {
    val candidates = _extraction_entries().sortBy { case (_, directory) =>
      val metadata = directory.resolve("entry.json")
      if (Files.isRegularFile(metadata)) Files.getLastModifiedTime(metadata).toMillis else Long.MinValue
    }
    val results = Vector.newBuilder[CacheCollectionEntry]
    var considered = 0
    var reclaimed = 0L
    candidates.iterator.takeWhile(_ => considered < policy.collectionMaxEntries && reclaimed < policy.collectionMaxBytes)
      .foreach { case (generation, directory) =>
        considered += 1
        ManagedCacheLocks.tryAcquire(_lock(generation.key), shared = false) match {
          case None => results += CacheCollectionEntry(generation, 0L, CacheCollectionDisposition.InUse)
          case Some(pin) =>
            try {
              val metadata = directory.resolve("entry.json")
              if (Files.exists(directory)) {
                val bytes = _bytes(directory)
                val touched = if (Files.isRegularFile(metadata)) Files.getLastModifiedTime(metadata).toMillis else 0L
                if (Files.isRegularFile(metadata) && System.currentTimeMillis() - touched < policy.idleMillis)
                  results += CacheCollectionEntry(generation, bytes, CacheCollectionDisposition.Retained)
                else if (dryRun)
                  results += CacheCollectionEntry(generation, bytes, CacheCollectionDisposition.WouldReclaim)
                else {
                  // Removing completion first ensures any failed partial deletion
                  // remains a non-hit for the next ordinary acquisition/collection.
                  Files.deleteIfExists(metadata)
                  _delete_tree(directory)
                  reclaimed += bytes
                  results += CacheCollectionEntry(generation, bytes, CacheCollectionDisposition.Reclaimed)
                }
              }
            } catch {
              case NonFatal(error) => results += CacheCollectionEntry(generation, 0L,
                CacheCollectionDisposition.Failed, Some(error.toString))
            } finally pin.close()
        }
      }
    CacheCollectionResult(results.result())
  }

  private def _extraction_entries(): Vector[(ExtractionGeneration, Path)] = ArchiveKind.values.toVector.flatMap { kind =>
    _directories(root.resolve("extracted").resolve(kind.directory)).filter(_.getFileName.toString.matches("[0-9a-f]{64}"))
      .flatMap { digest =>
        val entry = digest.resolve(kind.profile)
        if (Files.isDirectory(entry)) Some(ExtractionGeneration(SourceGeneration(kind, digest.getFileName.toString)) -> entry)
        else None
      }
  }

  private def _metadata(source: SourceGeneration, profile: String, bytes: Long): Json = Json.obj(
    "schema" -> Json.fromString("cncf.managed-cache-entry.v1"),
    "kind" -> Json.fromString(source.kind.directory),
    "digest" -> Json.fromString(source.digest),
    "profile" -> Json.fromString(profile),
    "bytes" -> Json.fromLong(bytes),
    "completed" -> Json.fromBoolean(true)
  )

  private def _valid(directory: Path, source: SourceGeneration, profile: String): Boolean = {
    val metadata = directory.resolve("entry.json")
    if (!Files.isRegularFile(metadata) || Files.size(metadata) > 65536L) false
    else parse(Files.readString(metadata)).toOption.exists { value =>
      val cursor = value.hcursor
      cursor.get[String]("schema").contains("cncf.managed-cache-entry.v1") &&
        cursor.get[String]("kind").contains(source.kind.directory) &&
        cursor.get[String]("digest").contains(source.digest) &&
        cursor.get[String]("profile").contains(profile) &&
        cursor.get[Boolean]("completed").contains(true) && cursor.get[Long]("bytes").exists(_ >= 0L)
    }
  }

  private final class Stage(val id: String, val directory: Path, val payload: Path,
    pin: ManagedCacheLocks.Pin) extends AutoCloseable {
    def close(): Unit = try _delete_tree(directory) finally pin.close()
  }

  private def _stage(purpose: String): Stage = {
    val id = UUID.randomUUID().toString
    val pin = ManagedCacheLocks.acquire(root.resolve("locks/staging").resolve(id + ".lock"), shared = false, policy)
    try {
      val date = YearMonth.now()
      val parent = root.resolve("staging").resolve(f"${date.getYear}%04d").resolve(f"${date.getMonthValue}%02d")
      Files.createDirectories(parent)
      val directory = Files.createDirectory(parent.resolve(id))
      val payload = Files.createDirectory(directory.resolve("payload"))
      _write_json(directory.resolve("stage.json"), Json.obj("id" -> Json.fromString(id), "purpose" -> Json.fromString(purpose)))
      new Stage(id, directory, payload, pin)
    } catch {
      case error: Throwable =>
        try pin.close() catch { case cleanup: Throwable => error.addSuppressed(cleanup) }
        throw error
    }
  }

  private def _publish(prepared: Path, destination: Path): Unit = {
    Files.createDirectories(destination.getParent)
    try Files.move(prepared, destination, StandardCopyOption.ATOMIC_MOVE)
    catch {
      case error: AtomicMoveNotSupportedException => throw CacheFailure(CacheFailure.Reason.UnsupportedFilesystem,
        "publish", destination.toString, detail = error.toString)
    }
  }

  private def _write_json(path: Path, value: Json): Unit = {
    Files.writeString(path, value.noSpaces)
    _force(path)
  }

  private def _force(path: Path): Unit =
    Using.resource(FileChannel.open(path, StandardOpenOption.WRITE))(_.force(true))

  private def _directories(path: Path): Vector[Path] =
    if (!Files.isDirectory(path)) Vector.empty
    else Using.resource(Files.list(path))(_.iterator().asScala.filter(Files.isDirectory(_)).toVector)

  private def _bytes(path: Path): Long =
    if (!Files.exists(path)) 0L
    else Using.resource(Files.walk(path))(_.iterator().asScala.filter(Files.isRegularFile(_)).map(Files.size(_)).sum)

  private def _delete_tree(path: Path): Unit = if (Files.exists(path)) {
    val paths = Using.resource(Files.walk(path))(_.iterator().asScala.toVector.sortBy(_.getNameCount).reverse)
    paths.foreach(Files.deleteIfExists(_))
  }

  private def _entry_capacity(operation: String, entry: String, used: Long, count: Long, limit: Long): Unit =
    if (count > limit - used)
      throw CacheFailure(CacheFailure.Reason.CapacityExceeded, operation, entry, Some(count), Some(math.max(0L, limit - used)))

  private def _retry_publication(started: Long, key: String): Unit = {
    val remaining = policy.acquisitionMaxWaitMillis - (System.nanoTime() - started) / 1000000L
    if (remaining <= 0L) throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "publish", key)
    try Thread.sleep(math.min(25L, remaining))
    catch { case error: InterruptedException => Thread.currentThread().interrupt(); throw error }
  }

  /** Cleanup failure must not hide a produced live lease from its caller. */
  private def _with_guard[G <: AutoCloseable, A <: AutoCloseable](guard: G)(body: G => A): A = {
    var produced: Option[A] = None
    var primary: Option[Throwable] = None
    try {
      val value = body(guard)
      produced = Some(value)
      value
    } catch {
      case error: Throwable => primary = Some(error); throw error
    } finally {
      try guard.close()
      catch {
        case error: Throwable => primary match {
          case Some(failure) => failure.addSuppressed(error)
          case None =>
            produced.foreach { value =>
              try value.close() catch { case cleanup: Throwable => error.addSuppressed(cleanup) }
            }
            throw error
        }
      }
    }
  }
}

object ManagedArchiveCache {
  def fromC(configuration: ResolvedConfiguration, repositoryRoot: Option[Path] = None): Consequence[ManagedArchiveCache] =
    ManagedCachePolicy.fromC(configuration, repositoryRoot).flatMap(policy => Consequence(new ManagedArchiveCache(policy)))
}
