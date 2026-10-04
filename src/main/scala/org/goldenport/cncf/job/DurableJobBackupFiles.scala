package org.goldenport.cncf.job

import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{FileVisitResult, Files, LinkOption, Path, Paths, SimpleFileVisitor, StandardCopyOption, StandardOpenOption}
import java.nio.file.attribute.{BasicFileAttributes, PosixFilePermissions}
import scala.jdk.CollectionConverters.*

/*
 * Operation-local bounded inventory and private create-new physical copy.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] abstract class DurableJobBackupCopyReader {
  def open(path: Path): InputStream
}

private[job] object DurableJobBackupCopyReader {
  val native: DurableJobBackupCopyReader = new DurableJobBackupCopyReader {
    def open(path: Path): InputStream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)
  }
}

private[job] final class DurableJobBackupFiles(reader: DurableJobBackupCopyReader) {
  private val _reader = reader
  private val _no_follow = Array(LinkOption.NOFOLLOW_LINKS)
  private val _directory_permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
  private val _file_permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))

  def backupInventory(source: DurableJobBackupSource): DurableJobBackupInventory = {
    root(source._database); root(source._blob_root)
    _require(!overlap(source._database, source._blob_root))
    Vector("-wal", "-shm", "-journal").foreach(suffix =>
      _require(!Files.exists(source._database.resolveSibling(source._database.getFileName.toString + suffix), _no_follow*)))
    val inventory = new Inventory(source._policy)
    inventory.add(source._database, Paths.get("jobs.db"), _attributes(source._database))
    _require(!inventory.entries.head.directory)
    _blobs(inventory, source._blob_root)
    inventory.result
  }

  def artifactInventory(artifact: Path, policy: DurableJobBackupResourcePolicy)
      : (DurableJobBackupMarker, DurableJobBackupInventory) = {
    root(artifact); _require(_attributes(artifact).isDirectory)
    _children(artifact, Set("backup.properties", "data"))
    val markerpath = artifact.resolve("backup.properties")
    val attributes = _attributes(markerpath)
    _require(attributes.isRegularFile && attributes.size() <= 4096L)
    val marker = _marker(_read_marker(markerpath, attributes.size()))
    val data = artifact.resolve("data")
    _require(_attributes(data).isDirectory)
    _children(data, Set("jobs.db", "blobs"))
    val inventory = new Inventory(policy)
    inventory.add(data.resolve("jobs.db"), Paths.get("jobs.db"), _attributes(data.resolve("jobs.db")))
    _require(!inventory.entries.head.directory)
    _require(_attributes(data.resolve("blobs")).isDirectory)
    _blobs(inventory, data.resolve("blobs"))
    val result = inventory.result
    _require(marker.filecount == result.filecount && marker.bytecount == result.bytecount)
    marker -> result
  }

  def destination(path: Path, sources: Vector[Path]): Unit = {
    root(path)
    val parent = path.getParent
    _require(parent != null && _attributes(parent).isDirectory &&
      Files.getFileStore(parent).supportsFileAttributeView("posix"))
    _require(!Files.exists(path, _no_follow*) && sources.forall(source => !overlap(path, source)))
  }

  def root(path: Path): Unit = {
    _require(path != null && path.isAbsolute && path.normalize() == path &&
      path.getFileSystem.provider().getScheme == "file")
    var current = path
    while (current != null) {
      _interrupt()
      _require(!Files.isSymbolicLink(current))
      if (current != path) _require(Files.isDirectory(current, _no_follow*))
      current = current.getParent
    }
  }

  def overlap(left: Path, right: Path): Boolean = left.startsWith(right) || right.startsWith(left)

  def copy(inventory: DurableJobBackupInventory, destination: Path): Path = {
    _interrupt()
    Files.createDirectory(destination, _directory_permissions)
    val temporary = destination.resolve(".incomplete")
    Files.createDirectory(temporary, _directory_permissions)
    inventory.entries.foreach { entry =>
      _interrupt()
      val target = temporary.resolve(entry.relative)
      if (entry.directory) Files.createDirectory(target, _directory_permissions)
      else _copy_file(entry.source.get, target, entry.size)
    }
    _interrupt()
    val data = destination.resolve("data")
    Files.move(temporary, data, StandardCopyOption.ATOMIC_MOVE)
    data
  }

  def publishMarker(destination: Path, marker: DurableJobBackupMarker): Unit = {
    _interrupt()
    val bytes = marker.text.getBytes(StandardCharsets.UTF_8)
    _require(bytes.length <= 4096)
    val path = destination.resolve("backup.properties")
    Files.createFile(path, _file_permissions)
    try {
      val output = Files.newOutputStream(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
      try { _interrupt(); output.write(bytes); _interrupt() }
      finally output.close()
    } catch {
      case exception: Exception =>
        if (exception.isInstanceOf[InterruptedException]) Thread.currentThread().interrupt()
        // Remove only our failed publication marker. Retain the owned root/data;
        // it is never automatically erased or reused as an overwrite retry.
        Files.deleteIfExists(path)
        throw exception
    }
  }

  private def _blobs(inventory: Inventory, blobroot: Path): Unit = {
    if (!Files.exists(blobroot, _no_follow*)) inventory.emptyBlobs()
    else {
      _require(_attributes(blobroot).isDirectory)
      Files.walkFileTree(blobroot, new SimpleFileVisitor[Path] {
        override def preVisitDirectory(path: Path, attributes: BasicFileAttributes): FileVisitResult = {
          inventory.add(path, Paths.get("blobs").resolve(blobroot.relativize(path)), attributes)
          FileVisitResult.CONTINUE
        }
        override def visitFile(path: Path, attributes: BasicFileAttributes): FileVisitResult = {
          inventory.add(path, Paths.get("blobs").resolve(blobroot.relativize(path)), attributes)
          FileVisitResult.CONTINUE
        }
      })
    }
  }

  private def _attributes(path: Path): BasicFileAttributes =
    Files.readAttributes(path, classOf[BasicFileAttributes], _no_follow*)

  private def _children(path: Path, expected: Set[String]): Unit = {
    val stream = Files.newDirectoryStream(path)
    try {
      val iterator = stream.iterator()
      var names = Set.empty[String]
      while (iterator.hasNext) {
        _interrupt()
        val name = iterator.next().getFileName.toString
        _require(expected.contains(name) && !names.contains(name))
        names += name
      }
      _require(names == expected)
    } finally stream.close()
  }

  private def _copy_file(source: Path, target: Path, size: Long): Unit = {
    val input = _reader.open(source)
    try {
      Files.createFile(target, _file_permissions)
      val output = Files.newOutputStream(target, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
      try {
        val buffer = new Array[Byte](8192)
        var copied = 0L
        while (copied < size) {
          _interrupt()
          val count = input.read(buffer, 0, math.min(buffer.length.toLong, size - copied).toInt)
          _require(count > 0 && count <= size - copied)
          _interrupt()
          output.write(buffer, 0, count)
          copied = Math.addExact(copied, count.toLong)
        }
        _interrupt()
        _require(input.read() == -1)
        _interrupt()
      } finally output.close()
    } finally input.close()
  }

  private def _read_marker(path: Path, size: Long): String = {
    val input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)
    try {
      val bytes = new ByteArrayOutputStream(4096)
      val buffer = new Array[Byte](4096)
      var copied = 0L
      while (copied < size) {
        _interrupt()
        val count = input.read(buffer, 0, (size - copied).toInt)
        _require(count > 0 && count <= size - copied)
        bytes.write(buffer, 0, count)
        copied = Math.addExact(copied, count.toLong)
      }
      _require(input.read() == -1)
      StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray)).toString
    } finally input.close()
  }

  private def _marker(text: String): DurableJobBackupMarker = {
    val lines = text.stripSuffix("\n").split("\n", -1).toVector
    _require(lines.size == 5 && !text.exists(character => Character.isISOControl(character) && character != '\n'))
    val expected = Set("version", "blob.store", "blob.container", "file.count", "byte.count")
    var fields = Map.empty[String, String]
    lines.foreach { line =>
      val split = line.indexOf('=')
      _require(split > 0)
      val key = line.substring(0, split)
      val value = line.substring(split + 1)
      _require(expected.contains(key) && !fields.contains(key) && value.nonEmpty)
      fields += key -> value
    }
    _require(fields.keySet == expected && fields("version") == "1" &&
      DurableJobBackupSource._valid_name(fields("blob.store")) &&
      DurableJobBackupSource._valid_name(fields("blob.container")))
    _require(fields("file.count").matches("[0-9]+") && fields("byte.count").matches("[0-9]+"))
    val files = fields("file.count").toInt
    val bytes = fields("byte.count").toLong
    _require(files > 0 && bytes >= 0L)
    DurableJobBackupMarker(fields("blob.store"), fields("blob.container"), files, bytes)
  }

  private def _require(condition: Boolean): Unit =
    if (!condition) throw new IllegalArgumentException("offline copy refused")

  private def _interrupt(): Unit =
    if (Thread.currentThread().isInterrupted) throw new InterruptedException()

  private final class Inventory(policy: DurableJobBackupResourcePolicy) {
    private var _entries = Vector.empty[DurableJobBackupEntry]
    private var _files = 0
    private var _bytes = 0L
    def entries: Vector[DurableJobBackupEntry] = _entries
    def result: DurableJobBackupInventory = DurableJobBackupInventory(_entries, _files, _bytes)
    def emptyBlobs(): Unit = _add(None, Paths.get("blobs"), true, 0L)
    def add(source: Path, relative: Path, attributes: BasicFileAttributes): Unit = {
      _interrupt()
      _require(!attributes.isSymbolicLink && (attributes.isDirectory || attributes.isRegularFile))
      _add(Some(source), relative, attributes.isDirectory, if (attributes.isRegularFile) attributes.size() else 0L)
    }
    private def _add(source: Option[Path], relative: Path, directory: Boolean, size: Long): Unit = {
      _require(!relative.isAbsolute && relative.normalize() == relative && size >= 0L &&
        relative.getNameCount <= policy.maxDepth &&
        relative.toString.getBytes(StandardCharsets.UTF_8).length <= policy.maxRelativePathBytes &&
        relative.iterator().asScala.forall(segment => {
          val name = segment.toString
          name.nonEmpty && name != "." && name != ".." && !name.contains("\\") &&
            !name.exists(character => Character.isISOControl(character)) && name.getBytes(StandardCharsets.UTF_8).length <= policy.maxSegmentBytes
        }))
      val count = Math.addExact(_entries.size, 1)
      _require(count <= policy.maxEntries)
      if (!directory) {
        _files = Math.addExact(_files, 1)
        _bytes = Math.addExact(_bytes, size)
        _require(_files <= policy.maxFiles && _bytes <= policy.maxBytes)
      }
      _entries :+= DurableJobBackupEntry(source, relative, directory, size)
    }
  }
}
