package org.goldenport.cncf.component

import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipInputStream}
import scala.jdk.CollectionConverters._
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.workarea.{WorkAreaHandle, WorkAreaSpace}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor

/*
 * @since   Mar. 22, 2026
 *  version Apr.  8, 2026
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SarExtracted(
  root: Path,
  descriptor: GenericSubsystemDescriptor,
  carArtifacts: Vector[Path],
  carDirectories: Vector[Path],
  extensionJars: Vector[Path],
  configFiles: Vector[Path]
)

object SarExtractor {
  def withExtracted[R](
    sar: Path,
    workarea: WorkAreaSpace
  )(
    use: SarExtracted => Consequence[R]
  ): Consequence[R] = workarea.execute(handle => _with_extracted(sar, handle, use))

  private[cncf] def withRetainedExtracted[R](
    sar: Path,
    workarea: WorkAreaSpace,
    register: AutoCloseable => Consequence[Unit]
  )(
    use: SarExtracted => Consequence[R]
  ): Consequence[R] =
    workarea.executeRetained(register)(handle => _with_extracted(sar, handle, use))

  private def _with_extracted[R](
    sar: Path,
    handle: WorkAreaHandle,
    use: SarExtracted => Consequence[R]
  ): Consequence[R] =
    handle.createTempDir("sar-").flatMap { tmproot =>
      for {
        _ <- _unzip(sar, tmproot)
        extracted <- _resolve_structure(tmproot)
        result <- use(extracted)
      } yield result
    }

  def resolveDirectory(root: Path): Consequence[SarExtracted] =
    _resolve_structure(root)

  /** Expand only into a cache-owned unpublished destination. */
  private[cncf] def extractToC(
    sar: Path,
    root: Path,
    beforeWrite: Long => Unit = _ => ()
  ): Consequence[SarExtracted] =
    _unzip(sar, root, beforeWrite).flatMap(_ => _resolve_structure(root))

  private def _unzip(
    sar: Path,
    root: Path,
    beforewrite: Long => Unit = _ => ()
  ): Consequence[Unit] = Consequence {
    Using.resource(new ZipInputStream(Files.newInputStream(sar))) { zip =>
      var entry: ZipEntry = zip.getNextEntry
      while (entry != null) {
        val target = root.resolve(entry.getName).normalize
        if (!target.startsWith(root)) {
          throw new IllegalArgumentException(s"zip entry escapes work area: ${entry.getName}")
        }
        if (entry.isDirectory) {
          Files.createDirectories(target)
        } else {
          Option(target.getParent).foreach(parent => Files.createDirectories(parent))
          Using.resource(Files.newOutputStream(target)) { output =>
            val buffer = new Array[Byte](65536)
            var count = zip.read(buffer)
            while (count >= 0) {
              if (count > 0) {
                beforewrite(count.toLong)
                output.write(buffer, 0, count)
              }
              count = zip.read(buffer)
            }
          }
        }
        zip.closeEntry()
        entry = zip.getNextEntry
      }
    }
  }

  private def _resolve_structure(
    root: Path
  ): Consequence[SarExtracted] = {
    val descriptor = GenericSubsystemDescriptor.loadArchive(root)
    val componentdir = root.resolve("component")
    val cars = _list_files_by_suffix(root, ".car")
    val cardirs = _list_directories(componentdir).filter(ComponentDescriptorLoader.looksLikeArchiveDirectory)
    val extensionJars = _list_files_by_suffix(root.resolve("extension"), ".jar")
    val configFiles = _list_regular_files(root.resolve("config"))
    descriptor.map { d =>
      SarExtracted(
        root = root,
        descriptor = d,
        carArtifacts = cars.sortBy(_.toString),
        carDirectories = cardirs.sortBy(_.toString),
        extensionJars = extensionJars.sortBy(_.toString),
        configFiles = configFiles.sortBy(_.toString)
      )
    }
  }

  private def _list_files_by_suffix(
    root: Path,
    suffix: String
  ): Vector[Path] =
    _list_regular_files(root).filter(_.getFileName.toString.endsWith(suffix))

  private def _list_regular_files(
    root: Path
  ): Vector[Path] = {
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
      try {
        stream
          .iterator()
          .asScala
          .filter(Files.isRegularFile(_))
          .toVector
      } finally {
        stream.close()
      }
    }
  }

  private def _list_directories(root: Path): Vector[Path] = {
    if (!Files.isDirectory(root)) {
      Vector.empty
    } else {
      val stream = Files.list(root)
      try {
        stream.iterator().asScala.filter(Files.isDirectory(_)).toVector
      } finally {
        stream.close()
      }
    }
  }
}
