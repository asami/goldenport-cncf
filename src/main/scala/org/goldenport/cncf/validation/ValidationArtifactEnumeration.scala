package org.goldenport.cncf.validation

import java.lang.reflect.Modifier
import java.nio.file.{Files, LinkOption, Path}
import java.util.jar.JarFile
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import ValidationDiscoveryV1.*

/** Bounded local artifact traversal, code-source admission and bounded diagnostics.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
private[validation] object ValidationArtifactEnumeration {
  final case class Enumeration(names: Vector[String], usable: Boolean, diagnostics: Vector[Diagnostic])

  val diagnosticCodes: Set[String] = Set(
    "enumeration", "loading", "provenance", "unsupported-suite", "suite-construction",
    "annotation", "anchor", "provider", "metadata", "identity", "ambiguity", "truncated"
  )

  def textValid(value: String): Boolean =
    value != null && value.nonEmpty && value == value.trim && value.length <= 512 && !value.exists(_.isControl)

  def packageValid(value: String): Boolean =
    textValid(value) && value.split("\\.", -1).forall(_identifier)

  def classValid(value: String): Boolean = packageValid(value)

  def rootValid(root: Path): Boolean =
    root != null && root.isAbsolute && !Files.isSymbolicLink(root) &&
      root.normalize == root && root.toRealPath() == root &&
      (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS))

  def sameArtifact(value: Class[?], root: Path): Boolean = {
    val source = Option(value.getProtectionDomain).flatMap(x => Option(x.getCodeSource))
      .flatMap(x => Option(x.getLocation))
    source.exists(x => x.getProtocol == "file" && Path.of(x.toURI).toRealPath() == root)
  }

  def constructible(value: Class[?]): Boolean =
    Modifier.isPublic(value.getModifiers) && !Modifier.isAbstract(value.getModifiers) &&
      Modifier.isPublic(value.getConstructor().getModifiers)

  def diagnostic(code: String, source: String, message: String): Diagnostic =
    Diagnostic(code, _bounded(source), _bounded(message))

  def bounded(diagnostics: Vector[Diagnostic], limit: Int, source: String): Vector[Diagnostic] =
    if (diagnostics.size <= limit) diagnostics
    else diagnostics.take(limit - 1) :+ diagnostic("truncated", source, "Additional discovery diagnostics omitted")

  def enumerate(artifact: ArtifactScope): Enumeration = {
    val names = Vector.newBuilder[String]
    var usable = false
    var skippedlink = false
    try {
      val prefix = artifact.packagePrefix.replace('.', '/') + "/"
      if (Files.isDirectory(artifact.root)) {
        val directory = artifact.root.resolve(artifact.packagePrefix.replace('.', '/'))
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
          // A symlinked package must not cause traversal beyond the admitted root.
          if (directory.toRealPath() != directory || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return Enumeration(Vector.empty, false, Vector(diagnostic("enumeration", artifact.artifactIdentity,
              "Package namespace is not a local directory within the admitted artifact")))
          }
          val stream = Files.walk(directory)
          try {
            stream.iterator().asScala.foreach { path =>
              if (Files.isSymbolicLink(path)) {
                skippedlink = true
              } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.toString.endsWith(".class")) {
                val relative = artifact.root.relativize(path).toString.replace(java.io.File.separatorChar, '/')
                _class_name(relative).foreach(names += _)
              }
            }
          } finally {
            stream.close()
          }
        }
      } else {
        val jar = new JarFile(artifact.root.toFile)
        try {
          jar.entries().asScala.filter(x => !x.isDirectory && x.getName.startsWith(prefix))
            .foreach(x => _class_name(x.getName).foreach(names += _))
        } finally {
          jar.close()
        }
      }
      usable = true
      val diagnostics = if (skippedlink) Vector(diagnostic("enumeration", artifact.artifactIdentity,
        "Symbolic links within the package namespace were skipped")) else Vector.empty
      Enumeration(names.result().distinct.sorted, usable, diagnostics)
    } catch {
      case NonFatal(_) => _failed(names.result(), artifact)
      case _: LinkageError => _failed(names.result(), artifact)
    }
  }

  private def _failed(names: Vector[String], artifact: ArtifactScope): Enumeration =
    Enumeration(names.distinct.sorted, names.nonEmpty,
      Vector(diagnostic("enumeration", artifact.artifactIdentity, "Local artifact enumeration failed")))

  private def _class_name(path: String): Option[String] =
    Option.when(path.endsWith(".class"))(path.dropRight(6).replace('/', '.')).filter(classValid)

  private def _identifier(value: String): Boolean =
    value.nonEmpty && Character.isJavaIdentifierStart(value.head) &&
      value.tail.forall(Character.isJavaIdentifierPart)

  private def _bounded(value: String): String =
    Option(value).getOrElse("").map(x => if (x.isControl) ' ' else x).take(512)
}
