package org.goldenport.cncf.component.cache

import java.nio.channels.FileChannel
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardOpenOption}
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
import org.goldenport.cncf.component.{CarExtracted, CarExtractor, SarExtracted, SarExtractor}

/** Cache storage and usage protection for the existing repository/extractor route.
  * This owns neither repository resolution nor runtime shutdown.
  * @since   Oct. 21, 2026
  * @version Oct. 22, 2026
  */
final class ManagedArchiveCache private[cache] (
  val policy: ManagedCachePolicy,
  files: ManagedCacheFiles
) extends AutoCloseable {
  def this(policy: ManagedCachePolicy) = this(policy, ManagedCacheFiles)
  Files.createDirectories(policy.root)
  val root: Path = policy.root.toRealPath()

  private val _selection = new ManagedGenerationSelection(root, policy, files)
  private var _collection_cursor: Option[ManagedCacheInventory] = None
  private var _collection_pending = Vector.empty[ManagedCacheInventory.Entry]

  /** Only explicit collection owns a bounded traversal cursor. No runtime lease is owned here. */
  def close(): Unit = synchronized {
    _collection_cursor.foreach(_.close())
    _collection_cursor = None
    _collection_pending = Vector.empty
  }

  private def _lock(key: String): Path = root.resolve("locks/generations").resolve(key + ".lock")
  private def _source(generation: SourceGeneration): Path =
    root.resolve("sources").resolve(generation.kind.directory).resolve(generation.digest)
  private def _extraction(generation: ExtractionGeneration): Path =
    root.resolve("extracted").resolve(generation.source.kind.directory)
      .resolve(generation.source.digest).resolve(generation.profile)

  def acquireSourceC(archive: Path, kind: ArchiveKind = ArchiveKind.Car): Consequence[SourceLease] =
    _acquire_source(archive, kind)

  private def _acquire_source(archive: Path, kind: ArchiveKind): Consequence[SourceLease] =
    Consequence {
      val started = System.nanoTime()
      var attempts = 0
      var result: Option[SourceLease] = None
      while (result.isEmpty && attempts < policy.acquisitionMaxAttempts) {
        attempts += 1
        try {
          val guard: AutoCloseable = _managed_input_pin(archive).getOrElse(new AutoCloseable { def close(): Unit = () })
          result = Some(_with_guard(guard) { _ => _capture_source(archive, kind) })
        } catch {
          case failure: CacheFailure if failure.reason == CacheFailure.Reason.SourceChanged =>
            if (attempts == policy.acquisitionMaxAttempts) throw failure
            _retry_publication(started, archive.toString)
        }
      }
      result.getOrElse(throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "capture", archive.toString))
    }

  /** Existing repository fetchers supply their ordinary stream here. The cache
    * supplies ordinary staging and the short atomic coordinate switch.
    */
  private[cncf] def publishSourceC(target: Path, kind: ArchiveKind)(
    write: Path => Unit
  ): Consequence[Unit] = Consequence { publishSource(target, kind)(write) }

  private[cncf] def publishSource(target: Path, kind: ArchiveKind)(
    write: Path => Unit
  ): Unit = {
    val destination = target.toAbsolutePath.normalize
    require(destination.startsWith(root.resolve(kind.directory)), "managed coordinate outside repository cache")
    _io("publish-source", destination.toString) {
      Using.resource(_stage("source-" + kind.directory)) { stage =>
        val prepared = stage.payload.resolve("archive" + kind.suffix)
        _io("download", destination.toString) { write(prepared) }
        _force(prepared)
        val coordinate = _selection.coordinate(destination, kind)
        _selection.withCoordinate(coordinate) {
          val key = "coordinate-" + coordinate
          val pin = ManagedCacheLocks.tryAcquire(_lock(key), false).getOrElse(
            throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "publish-coordinate", coordinate))
          try stage.publishFile(prepared, destination) finally pin.close()
        }
      }
    }
  }

  private def _managed_input_pin(archive: Path): Option[ManagedCacheLocks.Pin] = {
    val original = archive.toAbsolutePath.normalize
    val sources = root.resolve("sources")
    val coordinatekind = ArchiveKind.values.find(kind => original.startsWith(root.resolve(kind.directory)) &&
      original.getFileName.toString.endsWith(kind.suffix))
    if (coordinatekind.nonEmpty) {
      val coordinate = _selection.coordinate(original, coordinatekind.get)
      val pin = ManagedCacheLocks.acquire(_lock("coordinate-" + coordinate), true, policy)
      try {
        val touched = root.resolve("metadata/coordinates").resolve(coordinate).resolve("last-used")
        Files.createDirectories(touched.getParent)
        Files.writeString(touched, "")
        Some(pin)
      } catch { case error: Throwable => pin.close(); throw error }
    } else if (!original.startsWith(sources)) None
    else {
      val relative = sources.relativize(original)
      val kind = ArchiveKind.values.find(value => relative.getNameCount == 3 &&
        value.directory == relative.getName(0).toString && relative.getName(2).toString == "archive" + value.suffix)
      kind match {
        case Some(value) if relative.getName(1).toString.matches("[0-9a-f]{64}") =>
          val generation = SourceGeneration(value, relative.getName(1).toString)
          val pin = ManagedCacheLocks.acquire(_lock(generation.key), true, policy)
          try {
            if (!_valid(_source(generation), generation, "source-v1"))
              throw CacheFailure(CacheFailure.Reason.SourceChanged, "read-source", original.toString)
            Some(pin)
          } catch { case error: Throwable => pin.close(); throw error }
        case _ => throw CacheFailure(CacheFailure.Reason.SourceChanged, "read-source", original.toString)
      }
    }
  }

  private def _capture_source(archive: Path, kind: ArchiveKind): SourceLease = {
    val original = archive.toAbsolutePath.normalize
    val base = root.resolve("sources").resolve(kind.directory)
    if (original.startsWith(base) && base.relativize(original).getNameCount == 2 &&
        original.getFileName.toString == "archive" + kind.suffix) {
      val digest = base.relativize(original).getName(0).toString
      if (digest.matches("[0-9a-f]{64}")) {
        val generation = SourceGeneration(kind, digest)
        return _source_lease(generation, _source(generation), None)
      }
    }
    _with_guard(_stage("source-" + kind.directory)) { stage =>
        val original = archive.toAbsolutePath.normalize
        val before = Files.readAttributes(original, classOf[BasicFileAttributes])
        if (!before.isRegularFile) throw CacheFailure(CacheFailure.Reason.SourceChanged, "capture", original.toString)
        val digest = MessageDigest.getInstance("SHA-256")
        val captured = stage.payload.resolve("archive" + kind.suffix)
        var bytes = 0L
        Using.resource(files.openInput(original)) { input =>
          Using.resource(files.openOutput(captured)) { output =>
            val buffer = new Array[Byte](65536)
            var count = input.read(buffer)
            while (count >= 0) {
              if (count > 0) {
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
        _source_lease(generation, destination, Some(stage))
      }
  }

  private def _source_lease(generation: SourceGeneration, destination: Path, stage: Option[Stage]): SourceLease = {
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
          Files.setLastModifiedTime(destination.resolve("entry.json"), FileTime.fromMillis(System.currentTimeMillis()))
          retained = true
          return new SourceLease(generation, body, () => { pin.close(); _collect_retired(generation) })
        }
      } finally if (!retained) pin.close()
      ManagedCacheLocks.tryAcquire(_lock(generation.key), shared = false) match {
        case None => _retry_publication(started, generation.key)
        case Some(writer) =>
          try {
            if (!_complete_()) {
              _remove_payload(destination)
              if (!stage.exists(value => Files.isDirectory(value.payload))) throw CacheFailure(CacheFailure.Reason.SourceChanged,
                "capture-retry", generation.key, detail = "collection won publication-to-usage transition")
              stage.get.publish(destination)
            }
          } finally writer.close()
      }
      // Reacquire shared usage and recheck completion, never expose the gap
      // between dropping construction exclusion and acquiring usage protection.
    }
    throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "publish-source", generation.key)
  }

  def acquireCarC(archive: Path): Consequence[ExtractionLease[CarExtracted]] =
    _acquire_selected(archive, ArchiveKind.Car, CarExtractor.resolveDirectory,
      (source, tree, reserve) => CarExtractor.extractToC(source, tree, reserve))

  def acquireSarC(archive: Path): Consequence[ExtractionLease[SarExtracted]] =
    _acquire_selected(archive, ArchiveKind.Sar, SarExtractor.resolveDirectory,
      (source, tree, reserve) => SarExtractor.extractToC(source, tree, reserve))

  private def _acquire_selected[A](archive: Path, kind: ArchiveKind,
    resolve: Path => Consequence[A], extract: (Path, Path, Long => Unit) => Consequence[A]
  ): Consequence[ExtractionLease[A]] = Consequence {
    val coordinate = _selection.coordinate(archive, kind)
    var expected = _selection.observe(coordinate)
    var reselect = false
    var attempts = 0
    val started = System.nanoTime()
    var result: Option[ExtractionLease[A]] = None
    while (result.isEmpty && attempts < policy.acquisitionMaxAttempts) {
      attempts += 1
      val selected = if (reselect) _selection.pinCurrentSource(coordinate) else None
      if (reselect && selected.isEmpty) _retry_publication(started, coordinate)
      else {
        val source = selected match {
          case Some((selection, pin)) =>
            expected = Some(selection)
            new SourceLease(selection.source, _source(selection.source).resolve("archive" + kind.suffix),
              () => { pin.close(); _collect_retired(selection.source) })
          case None => _acquire_source(archive, kind).TAKE
        }
        ManagedArchiveCache.withAcquisitionCleanup { cleanup =>
          cleanup.retain(source)
          // Also records unselected prepared work if preparation or switching fails.
          if (!expected.exists(_.source == source.generation)) _selection.markRetired(source.generation)
          val prepared = cleanup.retain(_acquire_extracted(source, resolve, extract))
          if (!expected.exists(_.source == source.generation)) _selection.markRetired(source.generation)
          val committed = _selection.commit(coordinate, expected, source.generation)
          if (!committed) {
            reselect = true
            prepared.close()
          }
          source.close()
          expected.filterNot(_.source == source.generation).foreach { value =>
            _collect_retired(value.source)
            _selection.dependentSources(value.source).foreach(_collect_retired)
          }
          if (committed) {
            // Transfer only after every operation that can fail acquisition has ended.
            cleanup.transfer(prepared)
            result = Some(prepared)
          }
        }
      }
    }
    result.getOrElse(throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "select", coordinate))
  }

  private def _acquire_extracted[A](source: SourceLease,
    resolve: Path => Consequence[A], extract: (Path, Path, Long => Unit) => Consequence[A]
  ): ExtractionLease[A] = {
    source.checkOpen()
    val generation = ExtractionGeneration(source.generation)
    val destination = _extraction(generation)
    def _complete_(): Option[A] =
      if (_valid(destination, source.generation, generation.profile) && Files.isDirectory(destination.resolve("tree")))
        resolve(destination.resolve("tree")).toOption
      else None
    var attempts = 0
    val started = System.nanoTime()
    while (attempts < policy.acquisitionMaxAttempts) {
      attempts += 1
      val pin = ManagedCacheLocks.acquire(_lock(generation.key), shared = true, policy)
      var retained = false
      try {
        val completed = _complete_()
        if (completed.nonEmpty) {
          // The existing extractor remains the sole admission boundary on hits.
          val extracted = completed.get
          Files.setLastModifiedTime(destination.resolve("entry.json"), FileTime.fromMillis(System.currentTimeMillis()))
          retained = true
          return new ExtractionLease(generation, extracted, () => { pin.close(); _collect_retired(source.generation) })
        }
      } finally if (!retained) pin.close()
      ManagedCacheLocks.tryAcquire(_lock(generation.key), shared = false) match {
        // Another builder may already have returned a live shared lease. Do
        // not wait for its exclusive lock: reselect the now-complete entry.
        case None => _retry_publication(started, generation.key)
        case Some(writer) =>
          try {
            if (_complete_().isEmpty) {
              _remove_payload(destination)
              Using.resource(_stage("extracted-" + source.generation.kind.directory)) { stage =>
                val tree = Files.createDirectory(stage.payload.resolve("tree"))
                var bytes = 0L
                extract(source.path, tree, count => {
                  bytes += count
                }).TAKE
                resolve(tree).TAKE
                _write_json(stage.payload.resolve("entry.json"), _metadata(source.generation, generation.profile, bytes))
                stage.publish(destination)
              }
            }
          } finally writer.close()
      }
    }
    throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "publish-extraction", generation.key)
  }

  private[cncf] def withRetainedEmbeddedCarC[A](
    archive: Path,
    parent: SourceGeneration,
    register: AutoCloseable => Consequence[Unit]
  )(use: CarExtracted => Consequence[A]): Consequence[A] = {
    val acquired = _acquire_source(archive, ArchiveKind.Car).flatMap { source => Consequence {
      _with_guard(source) { owned =>
        val lease = _acquire_extracted(owned, CarExtractor.resolveDirectory,
          (input, tree, reserve) => CarExtractor.extractToC(input, tree, reserve))
        try { _selection.referenceEmbedded(parent, owned.generation); lease }
        catch { case error: Throwable => lease.close(); throw error }
      }
    } }
    _with_retained(acquired, register)(use)
  }

  /** Existing SystemNode registration owns the backing lease, not this cache.
    * Register before discovery so registration failure cannot strand a loader
    * whose backing lease was never accepted by that same owner.
    */
  private[cncf] def withRetainedCarC[A](
    archive: Path,
    register: AutoCloseable => Consequence[Unit]
  )(use: CarExtracted => Consequence[A]): Consequence[A] =
    _with_retained(acquireCarC(archive), register)(use)

  private def _with_retained[A, B](acquired: Consequence[ExtractionLease[A]],
    register: AutoCloseable => Consequence[Unit]
  )(use: A => Consequence[B]): Consequence[B] =
    acquired.flatMap { lease =>
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

  /** Register the SAR parent before discovering any directory-backed child.
    * Packed-only children acquire independent CAR backing before this parent ends.
    */
  private[cncf] def withRetainedSarC[A](
    archive: Path,
    register: AutoCloseable => Consequence[Unit]
  )(use: (SarExtracted, SourceGeneration) => Consequence[A]): Consequence[A] =
    acquireSarC(archive).flatMap { lease =>
      var retained = false
      try {
        val result = Consequence.run(register(lease)).flatMap(_ => Consequence.run(use(lease.value, lease.generation.source)))
        result match {
          case Consequence.Success(_) =>
            val value = lease.value
            retained = value.carDirectories.nonEmpty || value.extensionJars.nonEmpty || value.configFiles.nonEmpty
          case Consequence.Failure(_) => ()
        }
        result
      } finally if (!retained) lease.close()
    }

  private def _collect_retired(source: SourceGeneration): Unit = {
    _selection.collectRetired(source, false, () => {
      val directory = _source(source)
      Files.deleteIfExists(directory.resolve("entry.json"))
      _remove_payload(directory)
    })
    _selection.collectRetired(source, true, () => {
      val directory = _extraction(ExtractionGeneration(source))
      Files.deleteIfExists(directory.resolve("entry.json"))
      _remove_payload(directory)
    })
  }

  /** Physical totals cover only the returned bounded candidates. */
  def inspectC(): Consequence[CacheInspection] = Consequence {
    val projection = _collect(true, policy.collectionMaxEntries)
    val details = projection.operations ++ projection.entries.map { entry =>
      CacheOperationEntry("extracted", entry.generation.key, entry.bytes, entry.disposition,
        entry.reason.getOrElse("inspection"), entry.lastUsedMillis, entry.bytesKnown)
    }
    CacheInspection(details.filter(entry => entry.pool == "source" || entry.pool == "coordinate").map(_.bytes).sum,
      details.filter(_.pool == "extracted").map(_.bytes).sum, details.filter(_.pool == "staging").map(_.bytes).sum,
      details, projection.truncated)
  }

  /** Collection shares coordinate/payload exclusion with SNAPSHOT retirement.
    * A rotating cursor prevents an active or retained prefix starving later work.
    * Dry-run neither advances this cursor nor writes any cache metadata.
    */
  def collectC(dryRun: Boolean = false): Consequence[CacheCollectionResult] =
    Consequence { _collect(dryRun, policy.collectionMaxEntries) }

  private def _recorded_bytes(directory: Path): Long = _recorded_size(directory).getOrElse(0L)

  private def _recorded_size(directory: Path): Option[Long] = {
    val path = directory.resolve("entry.json")
    try {
      if (!Files.isRegularFile(path) || Files.size(path) > 65536L) None
      else parse(Files.readString(path)).toOption.flatMap(_.hcursor.get[Long]("bytes").toOption).filter(_ >= 0L)
    } catch { case NonFatal(_) => None }
  }

  private def _last_used(directory: Path): Option[Long] = {
    val metadata = directory.resolve("entry.json")
    if (Files.isRegularFile(metadata)) Some(Files.getLastModifiedTime(metadata).toMillis) else None
  }

  private def _collect(dryrun: Boolean, bound: Int): CacheCollectionResult = synchronized {
    val scan = if (dryrun) new ManagedCacheInventory(root, files)
      else _collection_cursor.getOrElse {
        val value = new ManagedCacheInventory(root, files)
        _collection_cursor = Some(value)
        value
      }
    val batch = try {
      if (!dryrun && _collection_pending.nonEmpty)
        ManagedCacheInventory.Batch(_collection_pending, scan.hasMore, 0)
      else scan.nextBatch(bound)
    } finally if (dryrun) scan.close()
    val candidates = batch.entries.sortBy(entry => if (entry.pool == "extracted") 0 else 1)
    val sample = candidates.groupMapReduce(_.pool)(entry =>
      if (entry.pool == "coordinate" && Files.isRegularFile(entry.directory)) Files.size(entry.directory)
      else _recorded_bytes(entry.directory))(_ + _)
    val results = Vector.newBuilder[CacheCollectionEntry]
    val operations = Vector.newBuilder[CacheOperationEntry]
    var considered = 0
    var selectedbytes = 0L
    candidates.iterator.takeWhile(_ => selectedbytes < policy.collectionMaxBytes)
      .foreach { candidate =>
        val pool = candidate.pool
        val directory = candidate.directory
        val source = candidate.source
        val key = if (pool == "coordinate") {
          val kind = ArchiveKind.values.find(value => directory.getFileName.toString.endsWith(value.suffix)).get
          "coordinate-" + _selection.coordinate(directory, kind)
        } else candidate.key
        considered += 1
        if (pool == "coordinate") {
          var bytes = if (Files.isRegularFile(directory)) Files.size(directory) else 0L
          var disposition = CacheCollectionDisposition.InUse
          var reason = "coordinate capture or publication in use"
          try {
            val acquired = _selection.collectCoordinate(key.stripPrefix("coordinate-")) {
              if (Files.isRegularFile(directory)) {
                bytes = Files.size(directory)
                val touched = root.resolve("metadata/coordinates").resolve(key.stripPrefix("coordinate-")).resolve("last-used")
                val lastused = Files.getLastModifiedTime(if (Files.isRegularFile(touched)) touched else directory).toMillis
                val idle = System.currentTimeMillis() - lastused >= policy.idleMillis
                if (policy.evictReusableSources && idle) {
                  reason = "explicit source eviction policy"
                  if (dryrun) disposition = CacheCollectionDisposition.WouldReclaim
                  else { _remove_payload(directory); disposition = CacheCollectionDisposition.Reclaimed }
                  selectedbytes += bytes
                } else {
                  disposition = CacheCollectionDisposition.Retained
                  reason = "offline coordinate retention"
                }
              }
            }
            if (!acquired) disposition = CacheCollectionDisposition.InUse
          } catch { case NonFatal(error) => disposition = CacheCollectionDisposition.Failed; reason = error.toString }
          operations += CacheOperationEntry(pool, key, bytes, disposition, reason)
        } else if (pool == "staging") {
          val pin = ManagedCacheLocks.tryAcquire(root.resolve("locks/staging").resolve(key + ".lock"), false)
          var bytes = 0L
          var bytesknown = false
          var disposition = CacheCollectionDisposition.InUse
          var reason = "staging owner in use"
          try if (pin.nonEmpty) {
            bytes = _bytes(directory)
            bytesknown = true
            if (!dryrun) _delete_tree(directory)
            disposition = if (dryrun) CacheCollectionDisposition.WouldReclaim else CacheCollectionDisposition.Reclaimed
            reason = "abandoned staging"
            selectedbytes += bytes
          } catch { case NonFatal(error) => disposition = CacheCollectionDisposition.Failed; reason = error.toString }
          finally pin.foreach(_.close())
          operations += CacheOperationEntry(pool, key, bytes, disposition, reason, bytesKnown = bytesknown)
        } else {
          val generation = source.get
          val extracted = pool == "extracted"
          var bytes = _recorded_bytes(directory)
          var bytesknown = _recorded_size(directory).nonEmpty
          var touched: Option[Long] = _last_used(directory)
          var disposition = CacheCollectionDisposition.InUse
          var reason = "native usage or coordinate protection held"
          try {
            val acquired = _selection.collectProtected(generation, extracted, dryrun) { (current, pending) =>
              bytes = if (_valid(directory, generation, if (extracted) generation.kind.profile else "source-v1"))
                _recorded_bytes(directory) else _bytes(directory)
              bytesknown = true
              touched = _last_used(directory)
              val retired = pending && !current
              val invalid = !_valid(directory, generation, if (extracted) generation.kind.profile else "source-v1")
              val idle = touched.forall(time => System.currentTimeMillis() - time >= policy.idleMillis)
              val pressure = if (extracted) sample.getOrElse("extracted", 0L) >= policy.extractedMaxBytes
                else sample.getOrElse("source", 0L) + sample.getOrElse("coordinate", 0L) >= policy.sourceMaxBytes
              val eligible = retired || invalid || ((extracted || policy.evictReusableSources) && (idle || pressure))
              if (eligible) {
                reason = if (retired) "retired SNAPSHOT" else if (invalid) "incomplete or damaged publication"
                  else if (pressure) "capacity pressure" else "idle reusable payload"
                if (dryrun) disposition = CacheCollectionDisposition.WouldReclaim
                else {
                  Files.deleteIfExists(directory.resolve("entry.json"))
                  _remove_payload(directory)
                  disposition = CacheCollectionDisposition.Reclaimed
                }
                selectedbytes += bytes
              } else {
                disposition = CacheCollectionDisposition.Retained
                reason = if (!extracted && !policy.evictReusableSources) "offline source retention" else "within idle policy"
              }
              eligible
            }
            if (!acquired) disposition = CacheCollectionDisposition.InUse
          } catch {
            case NonFatal(error) => disposition = CacheCollectionDisposition.Failed; reason = error.toString
          }
          if (extracted) results += CacheCollectionEntry(ExtractionGeneration(generation), bytes, disposition, Some(reason), touched, bytesknown)
          else operations += CacheOperationEntry(pool, key, bytes, disposition, reason, touched, bytesknown)
        }
      }
    val truncated = batch.truncated || considered < candidates.size
    if (!dryrun) {
      _collection_pending = candidates.drop(considered)
      if (!truncated) { scan.close(); _collection_cursor = None }
    }
    CacheCollectionResult(results.result(), operations.result(), policy.collectionMaxBytes,
      truncated, batch.visited)
  }

  private def _remove_payload(path: Path): Unit = {
    try _delete_tree(path)
    catch {
      case NonFatal(error) => throw _failure(CacheFailure.Reason.DeletionFailed, "delete", path.toString, error)
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
        cursor.get[Boolean]("completed").contains(true) && cursor.get[Long]("bytes").exists { bytes =>
          bytes >= 0L && (profile != "source-v1" || {
            val archive = directory.resolve("archive" + source.kind.suffix)
            Files.isRegularFile(archive) && Files.size(archive) == bytes
          })
        }
    }
  }

  private final class Stage(val id: String, val directory: Path, val payload: Path,
    pin: ManagedCacheLocks.Pin) extends AutoCloseable {
    def publish(destination: Path): Unit = _publish(payload, destination)
    def publishFile(prepared: Path, destination: Path): Unit = _publish(prepared, destination)
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
    try files.publish(prepared, destination)
    catch {
      case error: AtomicMoveNotSupportedException => throw _failure(CacheFailure.Reason.UnsupportedFilesystem,
        "publish", destination.toString, error)
      case NonFatal(error) => throw _failure(CacheFailure.Reason.PublicationFailed,
        "publish", destination.toString, error)
    }
  }

  private def _write_json(path: Path, value: Json): Unit = {
    files.writeMetadata(path, value.noSpaces)
  }

  private def _force(path: Path): Unit =
    Using.resource(FileChannel.open(path, StandardOpenOption.WRITE))(_.force(true))

  private def _bytes(path: Path): Long =
    if (!Files.exists(path)) 0L
    else Using.resource(Files.walk(path))(_.iterator().asScala.filter(Files.isRegularFile(_)).map(Files.size(_)).sum)

  private def _delete_tree(path: Path): Unit = files.deleteTree(path)

  private def _failure(reason: CacheFailure.Reason, operation: String, entry: String, cause: Throwable): CacheFailure = {
    val failure = CacheFailure(reason, operation, entry, detail = cause.toString)
    failure.initCause(cause)
    failure
  }

  private def _io[A](operation: String, entry: String)(body: => A): A =
    try body catch {
      case failure: CacheFailure => throw failure
      case NonFatal(error) => throw _failure(CacheFailure.Reason.IoFailure, operation, entry, error)
    }

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
  /** Owns acquisition resources until successful finalization transfers them.
    * Reverse-order cleanup always attempts every release and preserves failures.
    */
  private[cache] final class AcquisitionCleanup extends AutoCloseable {
    private var _resources = Vector.empty[AutoCloseable]

    def retain[A <: AutoCloseable](resource: A): A = {
      _resources :+= resource
      resource
    }

    def transfer(resource: AutoCloseable): Unit =
      _resources = _resources.filterNot(_ eq resource)

    def close(): Unit = {
      val resources = _resources.reverse
      _resources = Vector.empty
      var failure: Option[Throwable] = None
      resources.foreach { resource =>
        try resource.close()
        catch {
          case error: Throwable => failure match {
            case Some(primary) => if (primary ne error) primary.addSuppressed(error)
            case None => failure = Some(error)
          }
        }
      }
      failure.foreach(throw _)
    }
  }

  private[cache] def withAcquisitionCleanup[A](body: AcquisitionCleanup => A): A = {
    val cleanup = new AcquisitionCleanup
    var failure: Option[Throwable] = None
    try body(cleanup)
    catch { case error: Throwable => failure = Some(error); throw error }
    finally {
      try cleanup.close()
      catch {
        case error: Throwable => failure match {
          case Some(primary) => if (primary ne error) primary.addSuppressed(error)
          case None => throw error
        }
      }
    }
  }

  def fromC(configuration: ResolvedConfiguration, repositoryRoot: Option[Path] = None): Consequence[ManagedArchiveCache] =
    ManagedCachePolicy.fromC(configuration, repositoryRoot).flatMap(policy => Consequence(new ManagedArchiveCache(policy)))
}
