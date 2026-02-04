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
 * @version Feb.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CarExtracted(
  root: Path,
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

  // def withExtracted[R](
  //   car: Path,
  //   workarea: WorkAreaSpace
  // )(
  //   use: CarExtracted => Consequence[R]
  // ): Consequence[R] = {
  //   workarea.createTempDir("car-").flatMap { tmproot =>
  //     val attempt = for {
  //       _ <- _unzip(car, tmproot)
  //       extracted <- _resolve_structure(tmproot, car)
  //       result <- use(extracted)
  //     } yield result
  //     _with_cleanup(tmproot)(attempt)
  //   }
  // }

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
        _success_extracted(
          root,
          componentMain = componentjars.head,
          componentLibs = componentlibs,
          collaboratorMain = collaboratormain,
          collaboratorLibs = collaboratorlibs
        )
      }
    }
  }

  private def _success_extracted(
    root: Path,
    componentMain: Path,
    componentLibs: Vector[Path],
    collaboratorMain: Option[Path],
    collaboratorLibs: Vector[Path]
  ): Consequence[CarExtracted] =
    Consequence.success(
      CarExtracted(
        root = root,
        componentMain = componentMain,
        componentLibs = componentLibs,
        collaboratorMain = collaboratorMain,
        collaboratorLibs = collaboratorLibs
      )
    )

  private def _invalid_structure(
    car: Path,
    area: String,
    constraint: String,
    found: Int
  ): Consequence[CarExtracted] = {
    val message = s"car.invalid-structure area=$area constraint=$constraint found=$found uri=${car.toUri}"
    Consequence.failure(message)
  }

  private def _list_jars(dir: Path): Vector[Path] = {
    if (!Files.exists(dir)) {
      Vector.empty
    } else {
      val stream = Files.list(dir)
      try {
        stream
          .iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".jar"))
          .toVector
      } finally {
        stream.close()
      }
    }
  }

  // private def _with_cleanup[R](root: Path)(body: => Consequence[R]): Consequence[R] = {
  //   val attempt = Consequence.run(body)
  //   attempt match {
  //     case Consequence.Success(result) =>
  //       _delete_tree(root).map(_ => result)
  //     case Consequence.Failure(conclusion) =>
  //       _delete_tree(root).flatMap(_ => Consequence.Failure(conclusion))
  //   }
  // }

  // private def _delete_tree(root: Path): Consequence[Unit] = Consequence {
  //   if (Files.exists(root)) {
  //     Using.resource(Files.walk(root)) { stream =>
  //       stream
  //         .sorted(Comparator.reverseOrder())
  //         .iterator()
  //         .asScala
  //         .foreach(p => Files.deleteIfExists(p))
  //     }
  //   }
  // }
}
