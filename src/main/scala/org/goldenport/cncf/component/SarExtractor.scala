package org.goldenport.cncf.component

import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipInputStream}
import scala.jdk.CollectionConverters._
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor

/*
 * @since   Mar. 22, 2026
 * @version Apr.  8, 2026
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
  ): Consequence[R] = workarea.execute { handle =>
    handle.createTempDir("sar-").flatMap { tmproot =>
      for {
        _ <- _unzip(sar, tmproot)
        extracted <- _resolve_structure(tmproot)
        result <- use(extracted)
      } yield result
    }
  }

  def resolveDirectory(root: Path): Consequence[SarExtracted] =
    _resolve_structure(root)

  private def _unzip(
    sar: Path,
    root: Path
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
          Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
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
