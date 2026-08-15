package org.goldenport.cncf.testutil

import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

/*
 * Compiles fixture-only Java classes into a jar that is deliberately absent
 * from the test runtime parent class loader.
 *
 * @since   Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
object RuntimeInvisibleJarFixture {
  def compileJavaJar(target: Path, sources: Map[String, String]): Path = {
    Option(target.getParent).foreach(Files.createDirectories(_))
    val classes = Files.createTempDirectory(target.getParent, "runtime-invisible-classes")
    try {
      compileJavaDirectory(classes, sources)
      Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zip =>
        Using.resource(Files.walk(classes)) { paths =>
          paths.iterator().asScala
            .filter(Files.isRegularFile(_))
            .filter(_.getFileName.toString.endsWith(".class"))
            .foreach { path =>
              zip.putNextEntry(new ZipEntry(classes.relativize(path).toString.replace('\\', '/')))
              Files.copy(path, zip)
              zip.closeEntry()
            }
        }
      }
      target
    } finally {
      if (Files.exists(classes))
        Using.resource(Files.walk(classes)) { paths =>
          paths.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists)
      }
    }
  }

  def compileJavaDirectory(target: Path, sources: Map[String, String]): Path = {
    Option(target.getParent).foreach(Files.createDirectories(_))
    Files.createDirectories(target)
    val sourcepath = Files.createTempDirectory(target.getParent, "runtime-invisible-sources")
    try {
      val sourcefiles = sources.toVector.map { case (classname, source) =>
        val sourcefile = sourcepath.resolve(s"${classname.replace('.', '/')}.java")
        Option(sourcefile.getParent).foreach(Files.createDirectories(_))
        Files.writeString(sourcefile, source)
        sourcefile
      }
      val compiler = Option(javax.tools.ToolProvider.getSystemJavaCompiler)
        .getOrElse(throw new IllegalStateException("JDK JavaCompiler is required for runtime-invisible fixture"))
      val arguments = Vector("-classpath", System.getProperty("java.class.path"), "-d", target.toString) ++ sourcefiles.map(_.toString)
      if (compiler.run(null, null, null, arguments*) != 0)
        throw new IllegalStateException(s"runtime-invisible fixture compilation failed: ${target.getFileName}")
      target
    } finally {
      if (Files.exists(sourcepath))
        Using.resource(Files.walk(sourcepath)) { paths =>
          paths.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists)
        }
    }
  }
}
