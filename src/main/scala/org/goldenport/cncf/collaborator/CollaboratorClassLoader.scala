package org.goldenport.cncf.collaborator

import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

final class CollaboratorClassLoader(
  urls: Array[URL],
  parent: ClassLoader
) extends URLClassLoader(urls, parent)

object CollaboratorClassLoader {
  def apply(paths: Seq[Path]): CollaboratorClassLoader = {
    val urls = paths.map(_.toUri.toURL).toArray
    val parent = getClass.getClassLoader
    // Parent is the CNCF runtime loader so collaborators see SPI/runtime types while staying isolated from components.
    new CollaboratorClassLoader(urls, parent)
  }
}
