package org.goldenport.cncf.component

import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipInputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.cncf.workarea.WorkAreaSpace

/*
 * @since   Feb.  3, 2026
 *  version Mar. 22, 2026
 * @version Apr.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CarExtracted(
  root: Path,
  descriptor: ComponentDescriptor,
  componentMain: Path,
  componentLibs: Vector[Path],
  collaboratorMain: Option[Path],
  collaboratorLibs: Vector[Path]
) {
  def componentClasspath: Vector[Path] = componentMain +: componentLibs

  def collaboratorClasspath: Option[Vector[Path]] =
    collaboratorMain.map(main => main +: collaboratorLibs)

  def fullClasspath: Vector[Path] =
    componentClasspath ++ collaboratorClasspath.getOrElse(Vector.empty)
}

object CarExtractor {
  def withExtracted[R](
    car: Path,
    workarea: WorkAreaSpace
  )(
    use: CarExtracted => Consequence[R]
  ): Consequence[R] = workarea.execute { handle =>
    handle.createTempDir("car-").flatMap { tmproot =>
      for {
        _ <- _unzip(car, tmproot)
        extracted <- _resolve_structure(tmproot, car)
        result <- use(extracted)
      } yield result
    }
  }

  def resolveDirectory(root: Path): Consequence[CarExtracted] =
    _resolve_structure(root, root)

  private def _unzip(
    car: Path,
    root: Path
  ): Consequence[Unit] = Consequence {
    Using.resource(new ZipInputStream(Files.newInputStream(car))) { zip =>
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
    root: Path,
    car: Path
  ): Consequence[CarExtracted] = {
    val descriptor = ComponentDescriptorLoader.loadArchive(root)
    descriptor match {
      case Consequence.Failure(conclusion) =>
        return Consequence.Failure(conclusion)
      case _ =>
    }
    val componentdir = root.resolve("component")
    val componentjars = _list_jars(componentdir)
    if (componentjars.isEmpty) {
      _invalid_structure(car, "component/", "exactly(1)", 0)
    } else if (componentjars.size > 1) {
      _invalid_structure(car, "component/", "exactly(1)", componentjars.size)
    } else {
      val componentlibs = _list_jars(componentdir.resolve("lib")).sortBy(_.getFileName.toString)
      val collaboratordir = componentdir.resolve("collaborator")
      val collaboratormainfiles = _list_jars(collaboratordir)
      if (collaboratormainfiles.size > 1) {
        _invalid_structure(car, "component/collaborator/", "atmost(1)", collaboratormainfiles.size)
      } else {
        val collaboratormain = collaboratormainfiles.headOption
        val collaboratorlibs = _list_jars(collaboratordir.resolve("lib")).sortBy(_.getFileName.toString)
        descriptor.flatMap { d =>
          _success_extracted(
            root,
            descriptor = d,
            componentMain = componentjars.head,
            componentLibs = componentlibs,
            collaboratorMain = collaboratormain,
            collaboratorLibs = collaboratorlibs
          )
        }
      }
    }
  }

  private def _list_jars(root: Path): Vector[Path] = {
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.list(root)
      try {
        stream.iterator().asScala
          .filter(p => Files.isRegularFile(p))
          .filter(_.getFileName.toString.toLowerCase.endsWith(".jar"))
          .toVector
      } finally {
        stream.close()
      }
    }
  }

  private def _success_extracted(
    root: Path,
    descriptor: ComponentDescriptor,
    componentMain: Path,
    componentLibs: Vector[Path],
    collaboratorMain: Option[Path],
    collaboratorLibs: Vector[Path]
  ): Consequence[CarExtracted] = Consequence.success(
    CarExtracted(
      root = root,
      descriptor = descriptor,
      componentMain = componentMain,
      componentLibs = componentLibs,
      collaboratorMain = collaboratorMain,
      collaboratorLibs = collaboratorLibs
    )
  )

  private def _invalid_structure(
    car: Path,
    location: String,
    required: String,
    actual: Int
  ): Consequence[CarExtracted] =
    Consequence.failure(s"invalid car structure path=${car} location=${location} required=${required} actual=${actual}")
}
