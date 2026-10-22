package org.goldenport.cncf.component.cache

import java.io.{InputStream, OutputStream}
import java.nio.file.{Files, Path, StandardCopyOption}
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Ordinary filesystem boundary and focused deterministic I/O observation.
  * @since   Oct. 22, 2026
  * @version Oct. 22, 2026
  */
private[cache] trait ManagedCacheFiles {
  def openInput(path: Path): InputStream = Files.newInputStream(path)
  def openOutput(path: Path): OutputStream = Files.newOutputStream(path)
  def visited(path: Path): Unit = ()
  def writeMetadata(path: Path, text: String): Unit = {
    Files.writeString(path, text)
    Using.resource(FileChannel.open(path, StandardOpenOption.WRITE))(_.force(true))
  }
  def deleteTree(path: Path): Unit
  def publish(prepared: Path, destination: Path): Unit = {
    Files.createDirectories(destination.getParent)
    Files.move(prepared, destination, StandardCopyOption.ATOMIC_MOVE)
    ()
  }
}

private[cache] object ManagedCacheFiles extends ManagedCacheFiles {
  def deleteTree(path: Path): Unit = if (Files.exists(path)) {
    val paths = Using.resource(Files.walk(path))(_.iterator().asScala.toVector.sortBy(_.getNameCount).reverse)
    paths.foreach(Files.deleteIfExists(_))
  }
}
