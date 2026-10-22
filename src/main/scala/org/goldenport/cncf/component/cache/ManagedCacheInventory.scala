package org.goldenport.cncf.component.cache

import java.nio.file.{Files, Path}
import java.util.stream.Stream
import scala.jdk.CollectionConverters.*

/** Incremental explicit-operation traversal; acquisitions never construct it.
  * A cursor retains native directory iteration, avoiding rescans of a large prefix.
  * @since   Oct. 22, 2026
  * @version Oct. 22, 2026
  */
private[cache] final class ManagedCacheInventory(root: Path, files: ManagedCacheFiles) extends AutoCloseable {
  import ManagedCacheInventory.*

  private val _walks = (ArchiveKind.values.toVector.flatMap { kind => Vector(
    ("extracted", root.resolve("extracted").resolve(kind.directory), Some(kind)),
    ("source", root.resolve("sources").resolve(kind.directory), Some(kind)),
    ("coordinate", root.resolve(kind.directory), Some(kind)))
  } :+ (("staging", root.resolve("staging"), None))).flatMap { case (pool, base, kind) =>
    if (Files.isDirectory(base)) {
      val depth = pool match {
        case "source" => 1
        case "extracted" => 2
        case "staging" => 3
        case _ => Int.MaxValue
      }
      val stream = Files.walk(base, depth)
      Some(new Walk(pool, base, kind, stream, stream.iterator().asScala))
    } else None
  }
  private var _position = 0

  def hasMore: Boolean = _walks.exists(_.hasMore)

  def nextBatch(maxVisits: Int): Batch = {
    val entries = Vector.newBuilder[Entry]
    var visited = 0
    while (visited < maxVisits && hasMore) {
      val walk = _walks(_position % _walks.size)
      _position = (_position + 1) % _walks.size
      if (walk.hasMore) {
        val path = walk.paths.next()
        visited += 1
        files.visited(path)
        val relative = walk.base.relativize(path)
        val count = if (path == walk.base) 0 else relative.getNameCount
        val directory = Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
        val kind = walk.kind
        if (walk.pool == "source" && count == 1 && directory && path.getFileName.toString.matches("[0-9a-f]{64}")) {
          val source = SourceGeneration(kind.get, path.getFileName.toString)
          entries += Entry("source", source.key, path, Some(source))
        } else if (walk.pool == "extracted" && count == 2 && directory &&
            relative.getName(0).toString.matches("[0-9a-f]{64}") && path.getFileName.toString == kind.get.profile) {
          val source = SourceGeneration(kind.get, relative.getName(0).toString)
          entries += Entry("extracted", ExtractionGeneration(source).key, path, Some(source))
        } else if (walk.pool == "staging" && count == 3 && directory &&
            relative.getName(0).toString.matches("[0-9]{4}") && relative.getName(1).toString.matches("[0-9]{2}") &&
            path.getFileName.toString.matches("[0-9a-f-]{36}")) {
          entries += Entry("staging", path.getFileName.toString, path, None)
        } else if (walk.pool == "coordinate" && !directory &&
            Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) && path.getFileName.toString.endsWith(kind.get.suffix)) {
          entries += Entry("coordinate", path.toAbsolutePath.normalize.toString, path, None)
        }
      }
    }
    Batch(entries.result(), hasMore, visited)
  }

  def close(): Unit = _walks.foreach(_.close())
}

private[cache] object ManagedCacheInventory {
  final case class Entry(pool: String, key: String, directory: Path, source: Option[SourceGeneration])
  final case class Batch(entries: Vector[Entry], truncated: Boolean, visited: Int)

  private final class Walk(val pool: String, val base: Path, val kind: Option[ArchiveKind],
    stream: Stream[Path], val paths: Iterator[Path]) extends AutoCloseable {
    private var _closed = false
    def hasMore: Boolean = {
      if (_closed) false
      else if (paths.hasNext) true
      else { close(); false }
    }
    def close(): Unit = if (!_closed) { _closed = true; stream.close() }
  }
}
