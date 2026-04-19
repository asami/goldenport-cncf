package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import scala.util.Using

/*
 * @since   Apr. 20, 2026
 * @version Apr. 20, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait WebResourceRoot {
  def name: String
  def exists(relativePath: Path): Boolean
  def readText(relativePath: Path): Option[String]
}

object WebResourceRoot {
  final case class Directory(root: Path) extends WebResourceRoot {
    def name: String = root.toString

    def exists(relativePath: Path): Boolean =
      _safe(relativePath) && Files.isRegularFile(root.resolve(relativePath))

    def readText(relativePath: Path): Option[String] =
      Option.when(exists(relativePath)) {
        Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8)
      }
  }

  final case class Archive(archive: Path) extends WebResourceRoot {
    def name: String = archive.toString

    def exists(relativePath: Path): Boolean =
      _entry_name(relativePath).exists { entry =>
        Using.resource(new ZipFile(archive.toFile)) { zip =>
          Option(zip.getEntry(entry)).exists(!_.isDirectory)
        }
      }

    def readText(relativePath: Path): Option[String] =
      _entry_name(relativePath).flatMap { entry =>
        Using.resource(new ZipFile(archive.toFile)) { zip =>
          Option(zip.getEntry(entry)).filterNot(_.isDirectory).map { x =>
            val in = zip.getInputStream(x)
            try {
              new String(in.readAllBytes(), StandardCharsets.UTF_8)
            } finally {
              in.close()
            }
          }
        }
      }

    private def _entry_name(relativePath: Path): Option[String] =
      Option.when(_safe(relativePath)) {
        ("web" +: relativePath.iterator.asScala.map(_.toString).toVector).mkString("/")
      }
  }

  def directory(root: Path): WebResourceRoot =
    Directory(root)

  def archive(path: Path): WebResourceRoot =
    Archive(path)

  def isArchiveFile(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase(java.util.Locale.ROOT)
    name.endsWith(".sar") || name.endsWith(".car") || name.endsWith(".zip")
  }

  private def _safe(path: Path): Boolean =
    !path.isAbsolute && !path.iterator.asScala.exists(_.toString == "..")

}
